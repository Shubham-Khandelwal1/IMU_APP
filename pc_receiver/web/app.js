/* ═══════════════════════════════════════════════════════════════
   IMU Comparison Dashboard — app.js
   WebSocket client + Canvas charts + Canvas gauges + Three.js cubes
═══════════════════════════════════════════════════════════════ */

'use strict';

// ── SocketIO connection ────────────────────────────────────────
const socket = io();

// ── Zero reference controls ────────────────────────────────────
function zeroReference() {
  socket.emit('zero_reference');
  document.getElementById('zeroBtn').classList.add('active');
  document.getElementById('zeroBtn').textContent = '\u2713 Zeroed';
}

function resetReference() {
  socket.emit('reset_reference');
  const btn = document.getElementById('zeroBtn');
  btn.classList.remove('active');
  btn.innerHTML = `<svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67V7z"/></svg> Set Zero`;
}

function setRate(hz) {
  socket.emit('set_rate', hz);
}

// Acknowledge rate change from server
socket.on('rate_ack', data => {
  const hz = data.target_rate_hz;
  set('syncRateOut', hz + ' Hz');
  document.querySelectorAll('.rate-btn').forEach(b => {
    b.classList.toggle('active', parseInt(b.textContent) === hz);
  });
});

// ── Mode state ─────────────────────────────────────────────────
let currentMode = 'phone';   // 'phone' | 'imu' | 'both'

function setMode(mode) {
  currentMode = mode;

  document.querySelectorAll('.mode-btn').forEach(b =>
    b.classList.toggle('active', b.dataset.mode === mode)
  );

  document.getElementById('phonePanel').style.display = mode === 'phone' ? 'flex' : 'none';
  document.getElementById('imuPanel').style.display   = mode === 'imu'   ? 'flex' : 'none';
  document.getElementById('bothPanel').style.display  = mode === 'both'  ? 'block': 'none';

  // Force chart redraw after layout change
  Object.values(rollingCharts).forEach(c => c.dirty = true);
}

// ── Rolling chart engine ───────────────────────────────────────
const CHART_LEN = 200;

class RollingChart {
  constructor(canvasId, colors, maxAbs) {
    this.canvas  = document.getElementById(canvasId);
    this.ctx     = this.canvas ? this.canvas.getContext('2d') : null;
    this.colors  = colors;           // ['#FF5252', '#69F0AE', '#448AFF']
    this.maxAbs  = maxAbs ?? null;   // fixed y-range or null for auto
    this.series  = colors.map(() => []);
    this.dirty   = false;
  }

  push(...values) {
    values.forEach((v, i) => {
      this.series[i].push(v);
      if (this.series[i].length > CHART_LEN) this.series[i].shift();
    });
    this.draw();
  }

  draw() {
    if (!this.ctx) return;
    const { canvas, ctx, series, colors, maxAbs } = this;

    // Match canvas pixel size to CSS size
    canvas.width  = canvas.offsetWidth  * devicePixelRatio;
    canvas.height = canvas.offsetHeight * devicePixelRatio;
    ctx.scale(devicePixelRatio, devicePixelRatio);

    const W   = canvas.offsetWidth;
    const H   = canvas.offsetHeight;
    const PAD = 4;

    ctx.clearRect(0, 0, W, H);

    // Auto Y range
    const all = series.flat();
    const lo  = maxAbs != null ? -maxAbs : (Math.min(...all) - 0.5);
    const hi  = maxAbs != null ?  maxAbs : (Math.max(...all) + 0.5);
    const rng = hi - lo || 1;

    const mapY = v => PAD + (H - 2*PAD) * (1 - (v - lo) / rng);

    // Grid
    ctx.strokeStyle = 'rgba(255,255,255,0.04)';
    ctx.lineWidth   = 1;
    for (let i = 0; i <= 4; i++) {
      const y = PAD + (H - 2*PAD) * i / 4;
      ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(W, y); ctx.stroke();
    }

    // Zero line
    const zy = mapY(0);
    if (zy > PAD && zy < H - PAD) {
      ctx.setLineDash([4, 3]);
      ctx.strokeStyle = 'rgba(255,255,255,0.08)';
      ctx.beginPath(); ctx.moveTo(0, zy); ctx.lineTo(W, zy); ctx.stroke();
      ctx.setLineDash([]);
    }

    // Series lines
    series.forEach((data, si) => {
      if (data.length < 2) return;
      ctx.strokeStyle = colors[si];
      ctx.lineWidth   = 1.5;
      ctx.lineJoin    = 'round';
      ctx.lineCap     = 'round';
      ctx.beginPath();
      const step = (W - 2*PAD) / (CHART_LEN - 1);
      const off  = CHART_LEN - data.length;
      data.forEach((v, i) => {
        const x = PAD + (off + i) * step;
        const y = mapY(v);
        if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
      });
      ctx.stroke();
    });
  }
}

// ── Gauge engine (water bottle fill) ────────────────────────────
class ArcGauge { // Kept name the same to avoid refactoring instances
  constructor(elementId, color) {
    this.el = document.getElementById(elementId);
    this.color = color;
    if (this.el) {
      // Create a linear gradient based on the passed color
      this.el.style.background = `linear-gradient(0deg, ${hexAlpha(color, 0.4)} 0%, ${hexAlpha(color, 0.05)} 100%)`;
    }
    this._frac = 0;
  }

  set(fraction) {
    // Fraction ranges from [0, 1] based on degrees
    const target = Math.max(0, Math.min(1, fraction));
    // Smooth transition using CSS transition handle
    if (this.el) {
      this.el.style.transform = `scaleY(${target})`;
    }
  }
}

function hexAlpha(hex, a) {
  const r = parseInt(hex.slice(1,3),16);
  const g = parseInt(hex.slice(3,5),16);
  const b = parseInt(hex.slice(5,7),16);
  return `rgba(${r},${g},${b},${a})`;
}

// ── 3-D Cube (Three.js) ────────────────────────────────────────
class Cube3D {
  constructor(canvasId, edgeColor) {
    const canvas = document.getElementById(canvasId);
    if (!canvas || typeof THREE === 'undefined') return;

    this.renderer = new THREE.WebGLRenderer({ canvas, alpha: true, antialias: true });
    this.renderer.setPixelRatio(devicePixelRatio);
    this.renderer.setClearColor(0x000000, 0);

    this.scene  = new THREE.Scene();
    this.camera = new THREE.PerspectiveCamera(45, 1, 0.1, 100);
    this.camera.position.set(0, 0, 3.2);

    // Wireframe cube
    const geo   = new THREE.BoxGeometry(1.4, 1.4, 1.4);
    const edges = new THREE.EdgesGeometry(geo);
    const mat   = new THREE.LineBasicMaterial({ color: edgeColor });
    this.cube   = new THREE.LineSegments(edges, mat);
    this.scene.add(this.cube);

    // Axes
    this._addAxis([1,0,0], 0xFF5252);
    this._addAxis([0,1,0], 0x69F0AE);
    this._addAxis([0,0,1], 0x448AFF);

    this._euler = new THREE.Euler(0, 0, 0, 'YXZ');
    this._resize(canvas);
    this._animate();

    const ro = new ResizeObserver(() => this._resize(canvas));
    ro.observe(canvas);
  }

  _addAxis(dir, color) {
    const mat   = new THREE.LineBasicMaterial({ color });
    const pts   = [new THREE.Vector3(0,0,0), new THREE.Vector3(...dir).multiplyScalar(1.1)];
    const geo   = new THREE.BufferGeometry().setFromPoints(pts);
    this.scene.add(new THREE.Line(geo, mat));
  }

  _resize(canvas) {
    const w = canvas.clientWidth || 300;
    const h = canvas.clientHeight || 220;
    this.renderer.setSize(w, h, false);
    this.camera.aspect = w / h;
    this.camera.updateProjectionMatrix();
  }

  _animate() {
    requestAnimationFrame(() => this._animate());
    this.cube.rotation.copy(this._euler);
    this.renderer.render(this.scene, this.camera);
  }

  update(yawDeg, pitchDeg, rollDeg) {
    const d2r = Math.PI / 180;
    this._euler.set(pitchDeg * d2r, yawDeg * d2r, rollDeg * d2r);
  }
}

// ── Instantiate all charts, gauges, cubes ──────────────────────
const C = '#00E5FF';   // phone cyan
const I = '#FF6D00';   // imu orange
const G = '#69F0AE';   // diff green
const XYZ = ['#FF5252','#69F0AE','#448AFF'];

const rollingCharts = {
  phoneAccel:  new RollingChart('chartPhoneAccel',  XYZ),
  phoneGyro:   new RollingChart('chartPhoneGyro',   XYZ),
  phoneMag:    new RollingChart('chartPhoneMag',    XYZ),
  imuAccel:    new RollingChart('chartImuAccel',    XYZ),
  imuGyro:     new RollingChart('chartImuGyro',     XYZ),
  overlayYaw:  new RollingChart('chartOverlayYaw',  [C, I]),
  deltaYaw:    new RollingChart('chartDeltaYaw',    [G]),
  deltaPitch:  new RollingChart('chartDeltaPitch',  [G]),
  deltaRoll:   new RollingChart('chartDeltaRoll',   [G]),
};

const gauges = {
  phoneYaw:   new ArcGauge('gaugePhoneYaw',   C),
  phonePitch: new ArcGauge('gaugePhonePitch', '#AA00FF'),
  phoneRoll:  new ArcGauge('gaugePhoneRoll',  I),
  imuYaw:     new ArcGauge('gaugeImuYaw',     C),
  imuPitch:   new ArcGauge('gaugeImuPitch',   '#AA00FF'),
  imuRoll:    new ArcGauge('gaugeImuRoll',    I),
};

const cubes = {
  phone:        new Cube3D('cubePhone',        0x00E5FF),
  imu:          new Cube3D('cubeImu',          0xFF6D00),
  comparePhone: new Cube3D('cubeComparePhone', 0x00E5FF),
  compareImu:   new Cube3D('cubeCompareImu',   0xFF6D00),
};

// ── Format helpers ─────────────────────────────────────────────
const fmt    = (v, d=2) => (v >= 0 ? '+' : '') + v.toFixed(d);
const set    = (id, txt) => { const el = document.getElementById(id); if (el) el.textContent = txt; };

/** Format a relative timestamp in seconds as "t=+12.345s" or wall clock. */
function fmtTs(relTs, zeroed) {
  if (!zeroed) {
    // Show wall-clock time of last packet (local time)
    const d = new Date();
    return d.toTimeString().slice(0,8) + '.' + String(d.getMilliseconds()).padStart(3,'0');
  }
  const sign = relTs >= 0 ? '+' : '';
  return `t=${sign}${relTs.toFixed(3)}s`;
}

function xyzHtml(x, y, z) {
  return `<span style="color:#FF5252">X ${fmt(x,3)}</span>
          <span style="color:#69F0AE">Y ${fmt(y,3)}</span>
          <span style="color:#448AFF">Z ${fmt(z,3)}</span>`;
}

// ── Socket data handler ────────────────────────────────────────
socket.on('sensor_data', data => {
  const p = data.phone;
  const m = data.imu;

  // Sync zeroed state to button (in case of page refresh)
  if (data.zeroed !== undefined) {
    const btn = document.getElementById('zeroBtn');
    if (data.zeroed) {
      btn.classList.add('active');
      btn.textContent = '\u2713 Zeroed';
    }
  }

  // Update output rate display (keep in sync on page refresh)
  if (data.target_rate_hz !== undefined) {
    set('syncRateOut', data.target_rate_hz + ' Hz');
  }

  // ── Sync status bar ───────────────────────────────────────
  set('syncPhoneTs', p.connected ? fmtTs(p.rel_ts, data.zeroed) : 'no signal');
  set('syncImuTs',   m.connected ? fmtTs(m.rel_ts, data.zeroed) : 'no signal');
  set('syncMode',    data.zeroed ? 'relative (zeroed)' : 'absolute');

  if (p.connected && m.connected) {
    const off    = data.sync_offset_ms;
    const offEl  = document.getElementById('syncOffset');
    if (offEl) {
      offEl.textContent = (off >= 0 ? '+' : '') + off.toFixed(1) + ' ms';
      offEl.className   = 'sync-offset ' +
        (Math.abs(off) < 20  ? 'good' :
         Math.abs(off) < 100 ? 'ok'   : 'bad');
    }
  }

  // Connection pills
  const phoneConn = document.getElementById('phoneConn');
  const imuConn   = document.getElementById('imuConn');
  phoneConn.classList.toggle('active', p.connected);
  imuConn.classList.toggle('active',   m.connected);

  // ── Phone panel ────────────────────────────────────────────
  if (currentMode === 'phone' || currentMode === 'both') {
    const e = p.euler;

    // Gauges (correct fractions)
    gauges.phoneYaw  .set((e.yaw   + 180) / 360);
    gauges.phonePitch.set((e.pitch  + 90) / 180);
    gauges.phoneRoll .set((e.roll  + 180) / 360);

    set('valPhoneYaw',   fmt(e.yaw,   1) + '°');
    set('valPhonePitch', fmt(e.pitch, 1) + '°');
    set('valPhoneRoll',  fmt(e.roll,  1) + '°');
    set('phoneRate',     p.rate_hz + ' Hz');

    const [ax,ay,az] = p.accel;
    const [gx,gy,gz] = p.gyro;
    const [mx,my,mz] = p.mag;

    rollingCharts.phoneAccel.push(ax, ay, az);
    rollingCharts.phoneGyro .push(gx, gy, gz);
    rollingCharts.phoneMag  .push(mx, my, mz);

    const xyzAccelEl = document.getElementById('xyzPhoneAccel');
    if (xyzAccelEl) xyzAccelEl.innerHTML = xyzHtml(ax,ay,az);
    const xyzGyroEl  = document.getElementById('xyzPhoneGyro');
    if (xyzGyroEl)  xyzGyroEl.innerHTML  = xyzHtml(gx,gy,gz);
    const xyzMagEl   = document.getElementById('xyzPhoneMag');
    if (xyzMagEl)    xyzMagEl.innerHTML  = xyzHtml(mx,my,mz);

    cubes.phone?.update(e.yaw, e.pitch, e.roll);
  }

  // ── IMU panel ──────────────────────────────────────────────
  if (currentMode === 'imu' || currentMode === 'both') {
    const e = m.euler;

    gauges.imuYaw  .set((e.yaw   + 180) / 360);
    gauges.imuPitch.set((e.pitch  + 90) / 180);
    gauges.imuRoll .set((e.roll  + 180) / 360);

    set('valImuYaw',   fmt(e.yaw,   1) + '°');
    set('valImuPitch', fmt(e.pitch, 1) + '°');
    set('valImuRoll',  fmt(e.roll,  1) + '°');
    set('imuRate',     m.rate_hz + ' Hz');

    const [ax,ay,az] = m.accel;
    const [gx,gy,gz] = m.gyro;

    rollingCharts.imuAccel.push(ax, ay, az);
    rollingCharts.imuGyro .push(gx, gy, gz);

    const xyzAEl = document.getElementById('xyzImuAccel');
    if (xyzAEl) xyzAEl.innerHTML = xyzHtml(ax,ay,az);
    const xyzGEl = document.getElementById('xyzImuGyro');
    if (xyzGEl) xyzGEl.innerHTML = xyzHtml(gx,gy,gz);

    cubes.imu?.update(e.yaw, e.pitch, e.roll);
  }

  // ── Compare panel ──────────────────────────────────────────
  if (currentMode === 'both') {
    const pe = p.euler;
    const me = m.euler;

    set('cmpPhoneRate', p.rate_hz + ' Hz');
    set('cmpImuRate',   m.rate_hz + ' Hz');

    const dY = pe.yaw   - me.yaw;
    const dP = pe.pitch - me.pitch;
    const dR = pe.roll  - me.roll;

    set('deltaYaw',   fmt(dY, 2) + '°');
    set('deltaPitch', fmt(dP, 2) + '°');
    set('deltaRoll',  fmt(dR, 2) + '°');

    rollingCharts.overlayYaw .push(pe.yaw,  me.yaw);
    rollingCharts.deltaYaw   .push(dY);
    rollingCharts.deltaPitch .push(dP);
    rollingCharts.deltaRoll  .push(dR);

    set('cmpPhoneYaw',   fmt(pe.yaw,   2) + '°');
    set('cmpPhonePitch', fmt(pe.pitch, 2) + '°');
    set('cmpPhoneRoll',  fmt(pe.roll,  2) + '°');
    set('cmpImuYaw',     fmt(me.yaw,   2) + '°');
    set('cmpImuPitch',   fmt(me.pitch, 2) + '°');
    set('cmpImuRoll',    fmt(me.roll,  2) + '°');
    set('cmpDeltaYaw',   fmt(dY, 2) + '°');
    set('cmpDeltaPitch', fmt(dP, 2) + '°');
    set('cmpDeltaRoll',  fmt(dR, 2) + '°');

    cubes.comparePhone?.update(pe.yaw, pe.pitch, pe.roll);
    cubes.compareImu  ?.update(me.yaw, me.pitch, me.roll);
  }
});

// ── Init: show Phone mode by default ──────────────────────────
setMode('phone');
