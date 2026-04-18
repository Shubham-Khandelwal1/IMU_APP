'use strict';
/* ═══════════════════════════════════════════════════════════════
   IMU Research Dashboard — analysis.js
   Allan Variance, RMS Noise, Bias Drift — all computed client-side
═══════════════════════════════════════════════════════════════ */

const socket = io();

// ── State ──────────────────────────────────────────────────────
let isRecording  = false;
let recTimer     = null;
let recSeconds   = 0;
let activeSource = 'both';   // 'phone' | 'imu' | 'both'
let pendingAnalysis = {};    // source → samples array

// ── Source selector ────────────────────────────────────────────
function selectSource(src) {
  activeSource = src;
  ['Phone','Imu','Both'].forEach(s => {
    const btn = document.getElementById('src' + s);
    btn.className = 'src-btn';
  });
  const map = { phone: 'srcPhone', imu: 'srcImu', both: 'srcBoth' };
  const cls = { phone: 'active-phone', imu: 'active-imu', both: 'active-phone' };
  document.getElementById(map[src]).classList.add(cls[src]);
}

// ── Recording controls ─────────────────────────────────────────
function toggleRecord() {
  if (!isRecording) {
    socket.emit('start_recording');
  } else {
    socket.emit('stop_recording');
  }
}

socket.on('recording_ack', data => {
  isRecording = data.recording;
  const btn   = document.getElementById('recBtn');
  const dot   = document.getElementById('recDot');
  const lbl   = document.getElementById('recBtnLabel');

  if (isRecording) {
    btn.className = 'record-btn stop';
    dot.classList.add('rec-dot-pulse');
    lbl.textContent = 'Stop Recording';
    recSeconds = 0;
    recTimer = setInterval(() => {
      recSeconds++;
      document.getElementById('recDuration').textContent = recSeconds + 's';
    }, 1000);
    setStatus('Recording… keep IMU stationary!', '#FF5252');
  } else {
    btn.className = 'record-btn start';
    dot.classList.remove('rec-dot-pulse');
    lbl.textContent = 'Start Recording';
    clearInterval(recTimer);
    if (data.n_phone !== undefined) {
      document.getElementById('recNPhone').textContent = data.n_phone;
      document.getElementById('recNImu').textContent   = data.n_imu;
    }
    document.getElementById('analyzeBtn').disabled = false;
    setStatus(`Done — ${recSeconds}s recorded. Click Analyze.`, '#69F0AE');
  }
});

// ── Run analysis ───────────────────────────────────────────────
function runAnalysis() {
  setStatus('Fetching data from server…', '#FFD740');
  pendingAnalysis = {};

  const sources = activeSource === 'both' ? ['phone', 'imu'] : [activeSource];
  sources.forEach(src => socket.emit('get_analysis_data', src));
}

socket.on('analysis_data', data => {
  pendingAnalysis[data.source] = data;
  if (activeSource === 'both') {
    if (pendingAnalysis.phone && pendingAnalysis.imu) processAnalysis();
  } else {
    processAnalysis();
  }
});

function processAnalysis() {
  setStatus('Computing…', '#FFD740');

  const sources = activeSource === 'both' ? ['phone', 'imu'] : [activeSource];

  sources.forEach(src => {
    const d = pendingAnalysis[src];
    if (!d || d.n < 10) {
      setStatus('Not enough samples. Record for at least 10 seconds.', '#FF5252');
      return;
    }

    const dt      = 1.0 / d.rate_hz;   // seconds per sample
    const samples = d.samples;

    // Extract gyro axes (convert rad/s → deg/s for display)
    const R2D = 180 / Math.PI;
    const gx  = samples.map(s => s.gx * R2D);
    const gy  = samples.map(s => s.gy * R2D);
    const gz  = samples.map(s => s.gz * R2D);
    const ts  = samples.map(s => s.ts);

    const isPhone = src === 'phone';
    const pfx     = isPhone ? 'phone' : 'imu';
    const color   = isPhone ? '#00E5FF' : '#FF6D00';

    // ── RMS / Std / Bias per axis ─────────────────────────
    const stats = [
      { label: 'Gx', data: gx, cls: 'col-x' },
      { label: 'Gy', data: gy, cls: 'col-y' },
      { label: 'Gz', data: gz, cls: 'col-z' },
    ];

    const allGyr = [...gx, ...gy, ...gz];
    const rmsAll  = rms(allGyr);
    const stdAll  = std(allGyr);
    const biasAll = mean(allGyr);

    setEl(`${pfx}GyrRms`, fmt4(rmsAll) + '<span class="stat-unit">°/s</span>');
    setEl(`${pfx}GyrStd`, fmt4(stdAll) + '<span class="stat-unit">°/s</span>');
    setEl(`${pfx}GyrBias`, fmt4(Math.abs(biasAll)) + '<span class="stat-unit">°/s</span>');

    // XYZ breakdown table
    const tbody = document.getElementById(`${pfx}GyrXyz`);
    if (tbody) {
      tbody.innerHTML = stats.map(({label, data, cls}) => {
        const r = rms(data), s = std(data), b = mean(data);
        return `<tr>
          <td class="${cls}">${label}</td>
          <td>${fmt4(r)}</td><td>${fmt4(s)}</td><td>${fmt4(Math.abs(b))}</td>
        </tr>`;
      }).join('');
    }

    // Set rate badge
    setEl(`${pfx}AnalRate`, d.rate_hz.toFixed(1) + ' Hz');

    // ── Allan Deviation (overlapping, gyro Z) ──────────────
    const allanData = overlappingAllanDeviation(gz, dt);
    drawAllan(`allan${isPhone ? 'Phone' : 'Imu'}`, allanData, color);

    // Extract noise parameters
    const arwVal  = extractARW(allanData);    // @ tau=1s slope -1/2
    const biVal   = extractBI(allanData);     // minimum
    const rrwVal  = extractRRW(allanData);    // slope +1/2 region

    // Convert to standard units
    // ARW: deg/s/√Hz → deg/√hr  (multiply by 60 to convert from per-root-s to per-root-hour)
    const arwDeg  = arwVal  * 60;
    const biDeg   = biVal   * 3600;   // deg/s → deg/hr
    const rrwDeg  = rrwVal  * 3600;

    setEl(`${pfx}Arw`,  arwDeg < 0.001 ? '< 0.001' : arwDeg.toFixed(4));
    setEl(`${pfx}Bi`,   biDeg  < 0.001 ? '< 0.001' : biDeg.toFixed(4));
    setEl(`${pfx}Rrw`,  rrwDeg < 0.001 ? '< 0.001' : rrwDeg.toFixed(4));

    // ── Bias drift chart (rolling mean of gz) ─────────────
    const windowSec   = 5;    // 5-second rolling window
    const windowSamps = Math.max(1, Math.round(windowSec * d.rate_hz));
    const driftTs     = [];
    const driftVal    = [];
    for (let i = windowSamps; i < gz.length; i += Math.round(d.rate_hz * 0.5)) {
      const slice = gz.slice(i - windowSamps, i);
      driftTs.push(ts[i]);
      driftVal.push(mean(slice));
    }
    drawDrift(`drift${isPhone ? 'Phone' : 'Imu'}`, driftTs, driftVal, color);
  });

  setStatus('Analysis complete.', '#69F0AE');
}

// ── Maths ──────────────────────────────────────────────────────

function mean(arr) {
  return arr.reduce((s, v) => s + v, 0) / arr.length;
}

function rms(arr) {
  return Math.sqrt(arr.reduce((s, v) => s + v*v, 0) / arr.length);
}

function std(arr) {
  const m = mean(arr);
  return Math.sqrt(arr.reduce((s, v) => s + (v-m)**2, 0) / arr.length);
}

/**
 * Overlapping Allan Deviation (OADEV)
 * Input: data array (deg/s), dt (seconds)
 * Returns: [{tau, adev}, ...]
 */
function overlappingAllanDeviation(data, dt) {
  const N      = data.length;
  const result = [];

  // Cumulative sum for fast cluster averaging
  const cumsum = new Array(N + 1).fill(0);
  for (let i = 0; i < N; i++) cumsum[i+1] = cumsum[i] + data[i];

  // Integrate to phase (multiply by dt) for classical Allan
  const phase = new Array(N + 1).fill(0);
  for (let i = 0; i < N; i++) phase[i+1] = phase[i] + data[i] * dt;

  // Try cluster sizes m = 1, 2, 4, ... up to N/4
  let m = 1;
  while (m <= N / 4) {
    const tau   = m * dt;
    const count = N + 1 - 2 * m;
    if (count < 2) break;

    let sum = 0;
    for (let j = 0; j < count; j++) {
      const d = phase[j + 2*m] - 2*phase[j + m] + phase[j];
      sum += d * d;
    }
    const avar = sum / (2 * tau * tau * count);
    result.push({ tau, adev: Math.sqrt(avar) });

    // Logarithmically spaced taus
    m = Math.max(m + 1, Math.round(m * 1.6));
  }
  return result;
}

function extractARW(pts) {
  // Find point closest to tau = 1s (ARW read-off at tau=1)
  if (!pts.length) return 0;
  const t1 = pts.reduce((a, b) => Math.abs(a.tau - 1) < Math.abs(b.tau - 1) ? a : b);
  return t1.adev;
}

function extractBI(pts) {
  // Bias instability = minimum Allan deviation
  if (!pts.length) return 0;
  return Math.min(...pts.map(p => p.adev));
}

function extractRRW(pts) {
  // Rate Random Walk: estimated from the rising slope region (slope ~+0.5)
  // Approximate: use the last 20% of tau range
  if (pts.length < 4) return 0;
  const tail = pts.slice(-Math.max(2, Math.round(pts.length * 0.2)));
  return tail[tail.length - 1].adev;
}

// ── Allan deviation log-log chart ─────────────────────────────

function drawAllan(canvasId, pts, color) {
  const canvas = document.getElementById(canvasId);
  if (!canvas || !pts.length) return;
  const dpr = devicePixelRatio;
  canvas.width  = canvas.offsetWidth  * dpr;
  canvas.height = canvas.offsetHeight * dpr;
  const ctx = canvas.getContext('2d');
  ctx.scale(dpr, dpr);

  const W   = canvas.offsetWidth;
  const H   = canvas.offsetHeight;
  const PAD = { top: 16, right: 20, bottom: 36, left: 56 };
  const PW  = W - PAD.left - PAD.right;
  const PH  = H - PAD.top  - PAD.bottom;

  const tauMin  = pts[0].tau;
  const tauMax  = pts[pts.length-1].tau;
  const adevMin = Math.min(...pts.map(p => p.adev)) * 0.5;
  const adevMax = Math.max(...pts.map(p => p.adev)) * 2;

  const lx = t  => PAD.left + Math.log10(t   / tauMin)  / Math.log10(tauMax  / tauMin)  * PW;
  const ly = a  => PAD.top  + PH - Math.log10(a / adevMin) / Math.log10(adevMax / adevMin) * PH;

  // Background
  ctx.fillStyle = 'rgba(0,0,0,0)';
  ctx.clearRect(0, 0, W, H);

  // Grid lines (log)
  ctx.strokeStyle = 'rgba(255,255,255,0.05)';
  ctx.lineWidth   = 1;
  for (let decade = Math.floor(Math.log10(tauMin)); decade <= Math.ceil(Math.log10(tauMax)); decade++) {
    for (let sub = 1; sub <= 9; sub++) {
      const t = sub * Math.pow(10, decade);
      if (t < tauMin || t > tauMax) continue;
      const x = lx(t);
      ctx.beginPath(); ctx.moveTo(x, PAD.top); ctx.lineTo(x, PAD.top + PH); ctx.stroke();
    }
  }
  for (let decade = Math.floor(Math.log10(adevMin)); decade <= Math.ceil(Math.log10(adevMax)); decade++) {
    for (let sub = 1; sub <= 9; sub++) {
      const a = sub * Math.pow(10, decade);
      if (a < adevMin || a > adevMax) continue;
      const y = ly(a);
      ctx.beginPath(); ctx.moveTo(PAD.left, y); ctx.lineTo(PAD.left + PW, y); ctx.stroke();
    }
  }

  // Slope reference lines
  const drawSlope = (slope, refTau, refAdev, refColor, label) => {
    const t0 = tauMin * 2, t1 = tauMax / 2;
    const a0 = refAdev * Math.pow(t0 / refTau, slope);
    const a1 = refAdev * Math.pow(t1 / refTau, slope);
    ctx.strokeStyle = refColor;
    ctx.lineWidth   = 1;
    ctx.setLineDash([5, 4]);
    ctx.globalAlpha = 0.35;
    ctx.beginPath();
    ctx.moveTo(lx(t0), ly(Math.max(adevMin * 1.1, Math.min(adevMax * 0.9, a0))));
    ctx.lineTo(lx(t1), ly(Math.max(adevMin * 1.1, Math.min(adevMax * 0.9, a1))));
    ctx.stroke();
    ctx.setLineDash([]);
    ctx.globalAlpha = 1;
    // label
    ctx.fillStyle = refColor; ctx.font = '9px Inter'; ctx.globalAlpha = 0.5;
    ctx.fillText(label, lx(t0) + 2, ly(Math.max(adevMin*1.1, a0)) - 4);
    ctx.globalAlpha = 1;
  };

  const midTau  = pts[Math.floor(pts.length/2)].tau;
  const midAdev = pts[Math.floor(pts.length/2)].adev;
  drawSlope(-0.5, midTau, midAdev, '#FF5252', 'ARW (slope -½)');
  drawSlope(+0.5, midTau, midAdev, '#448AFF', 'RRW (slope +½)');

  // Allan deviation line
  ctx.strokeStyle = color;
  ctx.lineWidth   = 2;
  ctx.lineJoin    = 'round';
  ctx.beginPath();
  pts.forEach((p, i) => {
    const x = lx(p.tau), y = ly(Math.max(adevMin * 1.05, Math.min(adevMax * 0.95, p.adev)));
    if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
  });
  ctx.stroke();

  // Dots
  pts.forEach(p => {
    const x = lx(p.tau), y = ly(Math.max(adevMin*1.05, Math.min(adevMax*0.95, p.adev)));
    ctx.fillStyle = color;
    ctx.beginPath(); ctx.arc(x, y, 2.5, 0, Math.PI*2); ctx.fill();
  });

  // Mark minimum (bias instability)
  const minPt = pts.reduce((a, b) => a.adev < b.adev ? a : b);
  const mx = lx(minPt.tau), my = ly(minPt.adev);
  ctx.beginPath(); ctx.arc(mx, my, 5, 0, Math.PI*2);
  ctx.strokeStyle = '#69F0AE'; ctx.lineWidth = 1.5; ctx.stroke();
  ctx.fillStyle   = '#69F0AE'; ctx.font = '9px Inter'; ctx.globalAlpha = 0.7;
  ctx.fillText('BI', mx + 7, my + 4);
  ctx.globalAlpha = 1;

  // Axes labels
  ctx.fillStyle = 'rgba(255,255,255,0.3)';
  ctx.font      = '10px JetBrains Mono';

  // X axis ticks
  for (let d = Math.ceil(Math.log10(tauMin)); d <= Math.floor(Math.log10(tauMax)); d++) {
    const t = Math.pow(10, d);
    const x = lx(t);
    ctx.fillText(t >= 1 ? t + 's' : (t*1000).toFixed(0)+'ms', x - 12, PAD.top + PH + 18);
  }

  // Y axis ticks
  for (let d = Math.ceil(Math.log10(adevMin)); d <= Math.floor(Math.log10(adevMax)); d++) {
    const a = Math.pow(10, d);
    const y = ly(a);
    ctx.fillText('1e' + d, PAD.left - 42, y + 4);
  }

  // Axis titles
  ctx.fillStyle = 'rgba(255,255,255,0.4)'; ctx.font = '10px Inter';
  ctx.fillText('τ (s)', PAD.left + PW/2 - 10, H - 4);
  ctx.save();
  ctx.translate(12, PAD.top + PH/2);
  ctx.rotate(-Math.PI/2);
  ctx.fillText('ADEV (°/s)', -30, 0);
  ctx.restore();
}

// ── Bias drift chart ───────────────────────────────────────────

function drawDrift(canvasId, ts, vals, color) {
  const canvas = document.getElementById(canvasId);
  if (!canvas || !vals.length) return;
  const dpr = devicePixelRatio;
  canvas.width  = canvas.offsetWidth  * dpr;
  canvas.height = canvas.offsetHeight * dpr;
  const ctx = canvas.getContext('2d');
  ctx.scale(dpr, dpr);

  const W = canvas.offsetWidth, H = canvas.offsetHeight;
  const PL = 50, PR = 16, PT = 10, PB = 28;
  const PW = W - PL - PR, PH = H - PT - PB;

  const tMin = ts[0], tMax = ts[ts.length - 1];
  const vMin = Math.min(...vals), vMax = Math.max(...vals);
  const vPad = (vMax - vMin) * 0.15 || 0.001;

  const mx = t => PL + (t - tMin) / (tMax - tMin || 1) * PW;
  const my = v => PT + PH - (v - (vMin - vPad)) / ((vMax + vPad) - (vMin - vPad)) * PH;

  ctx.clearRect(0, 0, W, H);

  // Grid
  ctx.strokeStyle = 'rgba(255,255,255,0.05)'; ctx.lineWidth = 1;
  for (let i = 0; i <= 4; i++) {
    const y = PT + PH * i / 4;
    ctx.beginPath(); ctx.moveTo(PL, y); ctx.lineTo(PL + PW, y); ctx.stroke();
  }

  // Zero line
  const zy = my(0);
  if (zy > PT && zy < PT + PH) {
    ctx.setLineDash([4, 3]);
    ctx.strokeStyle = 'rgba(255,255,255,0.15)';
    ctx.beginPath(); ctx.moveTo(PL, zy); ctx.lineTo(PL + PW, zy); ctx.stroke();
    ctx.setLineDash([]);
  }

  // Fill area
  ctx.beginPath();
  vals.forEach((v, i) => {
    const x = mx(ts[i]), y = my(v);
    if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
  });
  ctx.lineTo(mx(ts[ts.length-1]), PT + PH);
  ctx.lineTo(mx(ts[0]), PT + PH);
  ctx.closePath();
  ctx.fillStyle = color.replace(')', ', 0.08)').replace('rgb', 'rgba');
  ctx.fill();

  // Line
  ctx.strokeStyle = color; ctx.lineWidth = 1.5;
  ctx.beginPath();
  vals.forEach((v, i) => {
    const x = mx(ts[i]), y = my(v);
    if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
  });
  ctx.stroke();

  // Axes
  ctx.fillStyle = 'rgba(255,255,255,0.25)'; ctx.font = '9px JetBrains Mono';
  ctx.fillText(vMax.toFixed(4), 2, PT + 10);
  ctx.fillText(vMin.toFixed(4), 2, PT + PH);
  ctx.fillText('0s', PL, H - 6);
  ctx.fillText(Math.round(tMax - tMin) + 's', PL + PW - 25, H - 6);
  ctx.fillText('°/s', 2, PT + PH/2);
}

// ── Helpers ────────────────────────────────────────────────────

function setEl(id, html) {
  const el = document.getElementById(id);
  if (el) el.innerHTML = html;
}

function fmt4(v) {
  return v.toFixed(4);
}

function setStatus(msg, color) {
  const el = document.getElementById('recStatus');
  if (el) { el.textContent = msg; el.style.color = color; }
}

// Add research-link style on load
const style = document.createElement('style');
style.textContent = `
  .research-link {
    display: flex; align-items: center; gap: 6px; margin-left: 16px;
    padding: 5px 12px; border-radius: 8px; text-decoration: none;
    border: 1px solid rgba(123,47,255,0.35); background: rgba(123,47,255,0.08);
    color: #B78AFF; font-size: 12px; font-weight: 600; transition: all 0.2s;
  }
  .research-link:hover { background: rgba(123,47,255,0.18); }
`;
document.head.appendChild(style);
