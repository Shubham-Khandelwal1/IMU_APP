/* ═══════════════════════════════════════════════════════════════
   IMU Perception Dashboard — app.js v2.0
   Features: Theme · Recording · Fullscreen · Layout · Toasts
             SVG Arc Gauges · Upgraded 3D Cube · Multi-IMU ready
   ═══════════════════════════════════════════════════════════════ */

'use strict';

// ═══════════════════════════════════════════════════════════════
//  TOAST NOTIFICATION SYSTEM
// ═══════════════════════════════════════════════════════════════
const ToastManager = {
  container: null,
  init() { this.container = document.getElementById('toastContainer'); },

  show(title, msg, type = 'info', durationMs = 4000) {
    if (!this.container) this.init();
    const icons = {
      success: '<svg viewBox="0 0 24 24" fill="#69F0AE"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/></svg>',
      error:   '<svg viewBox="0 0 24 24" fill="#FF5252"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/></svg>',
      info:    '<svg viewBox="0 0 24 24" fill="#00E5FF"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z"/></svg>',
      warning: '<svg viewBox="0 0 24 24" fill="#FFD740"><path d="M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z"/></svg>',
    };
    const el = document.createElement('div');
    el.className = `toast ${type}`;
    el.innerHTML = `
      <div class="toast-icon">${icons[type] || icons.info}</div>
      <div class="toast-body">
        <div class="toast-title">${title}</div>
        ${msg ? `<div class="toast-msg">${msg}</div>` : ''}
      </div>
      <button class="toast-close" onclick="this.closest('.toast').remove()">&times;</button>`;
    this.container.appendChild(el);
    setTimeout(() => {
      el.classList.add('toast-out');
      setTimeout(() => el.remove(), 350);
    }, durationMs);
  },
};

// ═══════════════════════════════════════════════════════════════
//  THEME MANAGER (Dark / Light)
// ═══════════════════════════════════════════════════════════════
const ThemeManager = {
  init() {
    const saved = localStorage.getItem('imu-theme') || 'dark';
    document.documentElement.setAttribute('data-theme', saved);
  },
  toggle() {
    const current = document.documentElement.getAttribute('data-theme');
    const next = current === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', next);
    localStorage.setItem('imu-theme', next);
    ToastManager.show('Theme', `Switched to ${next} mode`, 'info', 2000);
  },
};

// ═══════════════════════════════════════════════════════════════
//  RECORDING & PLAYBACK MANAGER
// ═══════════════════════════════════════════════════════════════
const RecordingManager = {
  isRecording: false,
  isPlaying: false,
  frames: [],
  playbackIndex: 0,
  playbackTimer: null,
  startTime: 0,

  toggleRecord() {
    if (this.isRecording) {
      this.stopRecord();
    } else {
      this.startRecord();
    }
  },

  startRecord() {
    this.frames = [];
    this.startTime = Date.now();
    this.isRecording = true;
    this._updateUI();
    ToastManager.show('Recording', 'Session recording started', 'error', 2000);
  },

  stopRecord() {
    this.isRecording = false;
    this._updateUI();
    const dur = ((Date.now() - this.startTime) / 1000).toFixed(1);
    ToastManager.show('Recording Saved', `${this.frames.length} frames (${dur}s) captured`, 'success', 3000);
  },

  addFrame(data) {
    if (!this.isRecording) return;
    this.frames.push({ t: Date.now() - this.startTime, data: JSON.parse(JSON.stringify(data)) });
    this._updateStats();
  },

  togglePlayback() {
    if (this.isPlaying) {
      this.stopPlayback();
    } else {
      this.startPlayback();
    }
  },

  startPlayback() {
    if (this.frames.length === 0) return;
    this.isPlaying = true;
    this.playbackIndex = 0;
    this._updateUI();
    ToastManager.show('Playback', 'Playing recorded session', 'info', 2000);

    const playNext = () => {
      if (this.playbackIndex >= this.frames.length || !this.isPlaying) {
        this.stopPlayback();
        return;
      }
      const frame = this.frames[this.playbackIndex];
      handleSensorData(frame.data);
      this._updatePlaybackProgress();
      this.playbackIndex++;

      if (this.playbackIndex < this.frames.length) {
        const delay = this.frames[this.playbackIndex].t - frame.t;
        this.playbackTimer = setTimeout(playNext, Math.max(delay, 10));
      } else {
        this.stopPlayback();
      }
    };
    playNext();
  },

  stopPlayback() {
    this.isPlaying = false;
    clearTimeout(this.playbackTimer);
    this._updateUI();
  },

  scrub(event) {
    if (this.frames.length === 0) return;
    const rect = event.currentTarget.getBoundingClientRect();
    const pct = Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width));
    const idx = Math.floor(pct * (this.frames.length - 1));
    handleSensorData(this.frames[idx].data);
    document.getElementById('recTimelineFill').style.width = (pct * 100) + '%';
  },

  download() {
    if (this.frames.length === 0) return;
    const blob = new Blob([JSON.stringify(this.frames, null, 2)], { type: 'application/json' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `imu_recording_${new Date().toISOString().slice(0,19).replace(/:/g,'-')}.json`;
    a.click();
    URL.revokeObjectURL(a.href);
    ToastManager.show('Download', 'Recording saved as JSON', 'success', 3000);
  },

  _updateUI() {
    const bar = document.getElementById('recordingBar');
    const startBtn = document.getElementById('recStartBtn');
    const startLabel = document.getElementById('recStartLabel');
    const dot = document.getElementById('recDotIndicator');
    const playBtn = document.getElementById('recPlayBtn');
    const playLabel = document.getElementById('recPlayLabel');
    const dlBtn = document.getElementById('recDownloadBtn');

    bar.classList.toggle('is-recording', this.isRecording);
    bar.classList.toggle('is-playing', this.isPlaying);

    if (this.isRecording) {
      startBtn.classList.add('active');
      startLabel.textContent = 'Stop';
      dot.classList.add('pulse');
    } else {
      startBtn.classList.remove('active');
      startLabel.textContent = 'Record';
      dot.classList.remove('pulse');
    }

    playBtn.disabled = this.frames.length === 0 || this.isRecording;
    dlBtn.disabled = this.frames.length === 0 || this.isRecording;

    if (this.isPlaying) {
      playLabel.textContent = 'Pause';
    } else {
      playLabel.textContent = 'Play';
    }
  },

  _updateStats() {
    const dur = ((Date.now() - this.startTime) / 1000).toFixed(1);
    set('recDuration', dur + 's');
    set('recFrames', this.frames.length.toString());
  },

  _updatePlaybackProgress() {
    const pct = this.playbackIndex / Math.max(1, this.frames.length - 1);
    document.getElementById('recTimelineFill').style.width = (pct * 100) + '%';
  },
};

// ═══════════════════════════════════════════════════════════════
//  FULLSCREEN CHART MANAGER
// ═══════════════════════════════════════════════════════════════
const FullscreenManager = {
  activeChart: null,
  modal: null,
  canvas: null,
  ctx: null,

  init() {
    this.modal = document.getElementById('fullscreenModal');
    this.canvas = document.getElementById('fullscreenChart');
    this.ctx = this.canvas ? this.canvas.getContext('2d') : null;
  },

  open(chartId, title) {
    if (!this.modal) this.init();
    this.activeChart = chartId;
    document.getElementById('fullscreenTitle').textContent = title || 'Chart';
    this.modal.classList.add('visible');
    document.body.style.overflow = 'hidden';
    this._draw();
  },

  close(event) {
    if (event && event.target !== this.modal && !event.target.closest('.fullscreen-close')) return;
    if (!this.modal) return;
    this.modal.classList.remove('visible');
    document.body.style.overflow = '';
    this.activeChart = null;
  },

  _draw() {
    if (!this.activeChart || !this.ctx) return;
    const chart = rollingCharts[
      this.activeChart.replace('chart', '')
        .replace(/([A-Z])/g, (m) => m.toLowerCase())
        .replace('phone', 'phone').replace('imu', 'imu')
    ];

    // Map chart IDs to rollingCharts keys
    const idMap = {
      'chartPhoneAccel': 'phoneAccel',
      'chartPhoneGyro':  'phoneGyro',
      'chartPhoneMag':   'phoneMag',
      'chartImuAccel':   'imuAccel',
      'chartImuGyro':    'imuGyro',
      'chartImuMag':     'imuMag',
      'chartOverlayYaw': 'overlayYaw',
      'chartDeltaYaw':   'deltaYaw',
      'chartDeltaPitch': 'deltaPitch',
      'chartDeltaRoll':  'deltaRoll',
    };

    const key = idMap[this.activeChart];
    if (!key || !rollingCharts[key]) return;

    const rc = rollingCharts[key];
    const canvas = this.canvas;
    const ctx = this.ctx;

    canvas.width = canvas.offsetWidth * devicePixelRatio;
    canvas.height = canvas.offsetHeight * devicePixelRatio;
    ctx.scale(devicePixelRatio, devicePixelRatio);

    const W = canvas.offsetWidth;
    const H = canvas.offsetHeight;
    const PAD = 20;

    ctx.clearRect(0, 0, W, H);

    const { series, colors, maxAbs } = rc;
    const all = series.flat();
    if (all.length === 0) return;

    const lo = maxAbs != null ? -maxAbs : (Math.min(...all) - 0.5);
    const hi = maxAbs != null ?  maxAbs : (Math.max(...all) + 0.5);
    const rng = hi - lo || 1;
    const mapY = v => PAD + (H - 2*PAD) * (1 - (v - lo) / rng);

    // Grid
    ctx.strokeStyle = 'rgba(128,128,128,0.1)';
    ctx.lineWidth = 1;
    ctx.font = '11px JetBrains Mono, monospace';
    ctx.fillStyle = 'rgba(128,128,128,0.4)';
    for (let i = 0; i <= 6; i++) {
      const y = PAD + (H - 2*PAD) * i / 6;
      const val = hi - (i / 6) * rng;
      ctx.beginPath(); ctx.moveTo(PAD, y); ctx.lineTo(W - PAD, y); ctx.stroke();
      ctx.textAlign = 'right';
      ctx.fillText(val.toFixed(1), PAD - 4, y + 4);
    }

    // Zero line
    const zy = mapY(0);
    if (zy > PAD && zy < H - PAD) {
      ctx.setLineDash([6, 4]);
      ctx.strokeStyle = 'rgba(128,128,128,0.2)';
      ctx.beginPath(); ctx.moveTo(PAD, zy); ctx.lineTo(W - PAD, zy); ctx.stroke();
      ctx.setLineDash([]);
    }

    // Series
    series.forEach((data, si) => {
      if (data.length < 2) return;
      ctx.strokeStyle = colors[si];
      ctx.lineWidth = 2;
      ctx.lineJoin = 'round'; ctx.lineCap = 'round';
      ctx.beginPath();
      const step = (W - 2*PAD) / (CHART_LEN - 1);
      const off = CHART_LEN - data.length;
      data.forEach((v, i) => {
        const x = PAD + (off + i) * step;
        const y = mapY(v);
        if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
      });
      ctx.stroke();
    });

    if (this.activeChart) requestAnimationFrame(() => this._draw());
  },

  exportPNG() {
    if (!this.canvas) return;
    const a = document.createElement('a');
    a.download = `chart_${this.activeChart || 'export'}.png`;
    a.href = this.canvas.toDataURL('image/png');
    a.click();
    ToastManager.show('Export', 'Chart saved as PNG', 'success', 2000);
  },
};

// ═══════════════════════════════════════════════════════════════
//  LAYOUT MANAGER (Custom Dashboard)
// ═══════════════════════════════════════════════════════════════
const LayoutManager = {
  isOpen: false,
  widgets: {},

  init() {
    // Discover all widgets
    document.querySelectorAll('.widget').forEach(w => {
      const id = w.dataset.widget;
      if (id) this.widgets[id] = true;
    });

    // Load saved layout
    const saved = localStorage.getItem('imu-layout');
    if (saved) {
      try {
        this.widgets = JSON.parse(saved);
      } catch(e) {}
    }

    this._applyLayout();
    this._buildToggles();
  },

  toggle() {
    this.isOpen = !this.isOpen;
    document.getElementById('layoutEditor').classList.toggle('open', this.isOpen);
  },

  setWidget(id, visible) {
    this.widgets[id] = visible;
    this._applyLayout();
    this._save();
    this._buildToggles();
  },

  preset(name) {
    const all = Object.keys(this.widgets);
    if (name === 'full') {
      all.forEach(k => this.widgets[k] = true);
    } else if (name === 'compact') {
      all.forEach(k => this.widgets[k] = k.includes('gauges') || k.includes('cube'));
    } else if (name === 'charts') {
      all.forEach(k => this.widgets[k] = k.includes('charts'));
    }
    this._applyLayout();
    this._save();
    this._buildToggles();
    // Update preset button active state
    document.querySelectorAll('.layout-preset-btn').forEach(b => {
      b.classList.toggle('active', b.textContent.toLowerCase() === name);
    });
    ToastManager.show('Layout', `Applied "${name}" preset`, 'info', 2000);
  },

  _applyLayout() {
    Object.entries(this.widgets).forEach(([id, visible]) => {
      const el = document.querySelector(`[data-widget="${id}"]`);
      if (el) el.classList.toggle('hidden-widget', !visible);
    });
  },

  _buildToggles() {
    const container = document.getElementById('widgetToggles');
    if (!container) return;
    container.innerHTML = '';
    const labels = {
      'gauges-phone': 'Phone Gauges',
      'charts-phone': 'Phone Charts',
      'cube-phone':   'Phone 3D Cube',
      'gauges-imu':   'IMU Gauges',
      'charts-imu':   'IMU Charts',
      'cube-imu':     'IMU 3D Cube',
    };
    Object.entries(this.widgets).forEach(([id, visible]) => {
      const div = document.createElement('div');
      div.className = `widget-toggle ${visible ? 'active' : ''}`;
      div.onclick = () => this.setWidget(id, !visible);
      div.innerHTML = `
        <span class="widget-toggle-label">${labels[id] || id}</span>
        <div class="widget-toggle-switch"></div>`;
      container.appendChild(div);
    });
  },

  _save() {
    localStorage.setItem('imu-layout', JSON.stringify(this.widgets));
  },
};

// ═══════════════════════════════════════════════════════════════
//  SOCKET CONNECTION
// ═══════════════════════════════════════════════════════════════
const socket = io();

// ═══════════════════════════════════════════════════════════════
//  ZERO / RESET / ALIGN CONTROLS
// ═══════════════════════════════════════════════════════════════
function zeroReference() {
  socket.emit('zero_reference');
  document.getElementById('zeroBtn').classList.add('active');
  document.getElementById('zeroBtn').textContent = '✓ Zeroed';
}

function resetReference() {
  socket.emit('reset_reference');
  const btn = document.getElementById('zeroBtn');
  btn.classList.remove('active');
  btn.innerHTML = `<svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67V7z"/></svg> Set Zero`;
}

let alignInterval = null;
function startAxisAlignment() { socket.emit('start_axis_align'); }

socket.on('axis_align_ack', data => {
  let timeLeft = data.duration_s;
  const modal = document.getElementById('alignModal');
  const timerEl = document.getElementById('alignTimer');
  const btn = document.getElementById('alignBtn');
  if (modal && timerEl && btn) {
    modal.style.display = 'flex';
    btn.classList.add('active');
    btn.textContent = 'Aligning...';
    timerEl.textContent = timeLeft;
    alignInterval = setInterval(() => {
      timeLeft--;
      if (timeLeft >= 0) timerEl.textContent = timeLeft;
    }, 1000);
  }
});

socket.on('axis_align_complete', data => {
  clearInterval(alignInterval);
  const modal = document.getElementById('alignModal');
  const btn = document.getElementById('alignBtn');
  if (modal && btn) {
    modal.style.display = 'none';
    btn.classList.remove('active');
    btn.innerHTML = `<svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2L4.5 20.29l.71.71L12 18l6.79 3 .71-.71z"/></svg> Auto Align`;
  }
  if (data.success) {
    ToastManager.show('Axis Alignment Complete',
      `Correlation: ${data.corr}/3.0\n${data.detail.join(' | ')}`, 'success', 6000);
    zeroReference();
  } else {
    ToastManager.show('Alignment Failed',
      'Ensure you are wiggling the sensors sufficiently in all three axes.', 'error', 5000);
  }
});

function setRate(hz) { socket.emit('set_rate', hz); }

socket.on('rate_ack', data => {
  const hz = data.target_rate_hz;
  set('syncRateOut', hz + ' Hz');
  document.querySelectorAll('.rate-btn').forEach(b => {
    b.classList.toggle('active', parseInt(b.textContent) === hz);
  });
});

// ═══════════════════════════════════════════════════════════════
//  MODE STATE
// ═══════════════════════════════════════════════════════════════
let currentMode = 'phone';

function setMode(mode) {
  currentMode = mode;
  document.querySelectorAll('.mode-btn').forEach(b =>
    b.classList.toggle('active', b.dataset.mode === mode)
  );
  document.getElementById('phonePanel').style.display = mode === 'phone' ? 'flex' : 'none';
  document.getElementById('imuPanel').style.display   = mode === 'imu'   ? 'flex' : 'none';
  document.getElementById('bothPanel').style.display   = mode === 'both'  ? 'block': 'none';
  Object.values(rollingCharts).forEach(c => c.dirty = true);
}

// ═══════════════════════════════════════════════════════════════
//  ROLLING CHART ENGINE
// ═══════════════════════════════════════════════════════════════
const CHART_LEN = 200;

class RollingChart {
  constructor(canvasId, colors, maxAbs) {
    this.canvas = document.getElementById(canvasId);
    this.ctx = this.canvas ? this.canvas.getContext('2d') : null;
    this.colors = colors;
    this.maxAbs = maxAbs ?? null;
    this.series = colors.map(() => []);
    this.dirty = false;
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
    canvas.width = canvas.offsetWidth * devicePixelRatio;
    canvas.height = canvas.offsetHeight * devicePixelRatio;
    ctx.scale(devicePixelRatio, devicePixelRatio);
    const W = canvas.offsetWidth, H = canvas.offsetHeight, PAD = 4;
    ctx.clearRect(0, 0, W, H);

    const all = series.flat();
    const lo = maxAbs != null ? -maxAbs : (Math.min(...all) - 0.5);
    const hi = maxAbs != null ?  maxAbs : (Math.max(...all) + 0.5);
    const rng = hi - lo || 1;
    const mapY = v => PAD + (H - 2*PAD) * (1 - (v - lo) / rng);

    // Grid
    ctx.strokeStyle = 'rgba(128,128,128,0.06)';
    ctx.lineWidth = 1;
    for (let i = 0; i <= 4; i++) {
      const y = PAD + (H - 2*PAD) * i / 4;
      ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(W, y); ctx.stroke();
    }

    // Zero line
    const zy = mapY(0);
    if (zy > PAD && zy < H - PAD) {
      ctx.setLineDash([4, 3]);
      ctx.strokeStyle = 'rgba(128,128,128,0.12)';
      ctx.beginPath(); ctx.moveTo(0, zy); ctx.lineTo(W, zy); ctx.stroke();
      ctx.setLineDash([]);
    }

    // Series
    series.forEach((data, si) => {
      if (data.length < 2) return;
      ctx.strokeStyle = colors[si];
      ctx.lineWidth = 1.5;
      ctx.lineJoin = 'round'; ctx.lineCap = 'round';
      ctx.beginPath();
      const step = (W - 2*PAD) / (CHART_LEN - 1);
      const off = CHART_LEN - data.length;
      data.forEach((v, i) => {
        const x = PAD + (off + i) * step;
        const y = mapY(v);
        if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
      });
      ctx.stroke();

      // Glow effect
      ctx.globalAlpha = 0.15;
      ctx.lineWidth = 4;
      ctx.stroke();
      ctx.globalAlpha = 1;
      ctx.lineWidth = 1.5;
    });
  }
}

// ═══════════════════════════════════════════════════════════════
//  SVG ARC GAUGE ENGINE
// ═══════════════════════════════════════════════════════════════
class ArcGauge {
  constructor(elementId, color) {
    this.el = document.getElementById(elementId);
    this.color = color;
    this.totalLen = 0;
    if (this.el) {
      this.totalLen = this.el.getTotalLength ? this.el.getTotalLength() : 126;
      this.el.style.strokeDasharray = this.totalLen;
      this.el.style.strokeDashoffset = this.totalLen;
    }
  }

  set(fraction) {
    if (!this.el) return;
    const f = Math.max(0, Math.min(1, fraction));
    this.el.style.strokeDashoffset = this.totalLen * (1 - f);
  }
}

// ═══════════════════════════════════════════════════════════════
//  3D CUBE (Three.js) — Upgraded with solid mesh + grid
// ═══════════════════════════════════════════════════════════════
class Cube3D {
  constructor(canvasId, edgeColor) {
    const canvas = document.getElementById(canvasId);
    if (!canvas || typeof THREE === 'undefined') return;

    this.renderer = new THREE.WebGLRenderer({ canvas, alpha: true, antialias: true });
    this.renderer.setPixelRatio(devicePixelRatio);
    this.renderer.setClearColor(0x000000, 0);

    this.scene = new THREE.Scene();
    this.camera = new THREE.PerspectiveCamera(45, 1, 0.1, 100);
    this.camera.position.set(0, 1, 3.5);
    this.camera.lookAt(0, 0, 0);

    // Translucent solid cube
    const geo = new THREE.BoxGeometry(1.3, 1.3, 1.3);
    const solidMat = new THREE.MeshBasicMaterial({
      color: edgeColor,
      transparent: true,
      opacity: 0.06,
      depthWrite: false,
    });
    const solidMesh = new THREE.Mesh(geo, solidMat);
    this.scene.add(solidMesh);

    // Wireframe edges
    const edges = new THREE.EdgesGeometry(geo);
    const lineMat = new THREE.LineBasicMaterial({ color: edgeColor, transparent: true, opacity: 0.8 });
    this.cube = new THREE.LineSegments(edges, lineMat);
    this.scene.add(this.cube);

    // Solid mesh follows cube rotation
    this.solidMesh = solidMesh;

    // Grid floor
    const gridGeo = new THREE.PlaneGeometry(4, 4, 8, 8);
    const gridMat = new THREE.MeshBasicMaterial({
      color: edgeColor,
      transparent: true,
      opacity: 0.03,
      wireframe: true,
    });
    const grid = new THREE.Mesh(gridGeo, gridMat);
    grid.rotation.x = -Math.PI / 2;
    grid.position.y = -1.0;
    this.scene.add(grid);

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
    const mat = new THREE.LineBasicMaterial({ color });
    const pts = [new THREE.Vector3(0,0,0), new THREE.Vector3(...dir).multiplyScalar(1.1)];
    const geo = new THREE.BufferGeometry().setFromPoints(pts);
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
    if (this.solidMesh) this.solidMesh.rotation.copy(this._euler);
    this.renderer.render(this.scene, this.camera);
  }

  update(yawDeg, pitchDeg, rollDeg) {
    const d2r = Math.PI / 180;
    this._euler.set(pitchDeg * d2r, yawDeg * d2r, rollDeg * d2r);
  }
}

// ═══════════════════════════════════════════════════════════════
//  INSTANTIATE CHARTS, GAUGES, CUBES
// ═══════════════════════════════════════════════════════════════
const C = '#00E5FF', I = '#FF6D00', G = '#69F0AE';
const XYZ = ['#FF5252', '#69F0AE', '#448AFF'];

const rollingCharts = {
  phoneAccel:  new RollingChart('chartPhoneAccel',  XYZ),
  phoneGyro:   new RollingChart('chartPhoneGyro',   XYZ),
  phoneMag:    new RollingChart('chartPhoneMag',    XYZ),
  imuAccel:    new RollingChart('chartImuAccel',    XYZ),
  imuGyro:     new RollingChart('chartImuGyro',     XYZ),
  imuMag:      new RollingChart('chartImuMag',      XYZ),
  overlayYaw:  new RollingChart('chartOverlayYaw',  [C, I]),
  deltaYaw:    new RollingChart('chartDeltaYaw',    [G]),
  deltaPitch:  new RollingChart('chartDeltaPitch',  [G]),
  deltaRoll:   new RollingChart('chartDeltaRoll',   [G]),
};

const gauges = {
  phoneYaw:   new ArcGauge('arcPhoneYaw',   C),
  phonePitch: new ArcGauge('arcPhonePitch', '#D500F9'),
  phoneRoll:  new ArcGauge('arcPhoneRoll',  I),
  imuYaw:     new ArcGauge('arcImuYaw',     C),
  imuPitch:   new ArcGauge('arcImuPitch',   '#D500F9'),
  imuRoll:    new ArcGauge('arcImuRoll',    I),
};

const cubes = {
  phone:        new Cube3D('cubePhone',        0x00E5FF),
  imu:          new Cube3D('cubeImu',          0xFF6D00),
  comparePhone: new Cube3D('cubeComparePhone', 0x00E5FF),
  compareImu:   new Cube3D('cubeCompareImu',   0xFF6D00),
};

// ═══════════════════════════════════════════════════════════════
//  FORMAT HELPERS
// ═══════════════════════════════════════════════════════════════
const fmt = (v, d=2) => (v >= 0 ? '+' : '') + v.toFixed(d);
const set = (id, txt) => { const el = document.getElementById(id); if (el) el.textContent = txt; };

function xyzHtml(x, y, z) {
  return `<span style="color:#FF5252">X ${fmt(x,3)}</span>
          <span style="color:#69F0AE">Y ${fmt(y,3)}</span>
          <span style="color:#448AFF">Z ${fmt(z,3)}</span>`;
}

// ═══════════════════════════════════════════════════════════════
//  SENSOR DATA HANDLER
// ═══════════════════════════════════════════════════════════════
function handleSensorData(data) {
  const p = data.phone;
  const m = data.imu;

  // Sync zeroed state
  if (data.zeroed !== undefined) {
    const btn = document.getElementById('zeroBtn');
    if (data.zeroed) {
      btn.classList.add('active');
      btn.textContent = '✓ Zeroed';
    }
  }

  if (data.target_rate_hz !== undefined) {
    set('syncRateOut', data.target_rate_hz + ' Hz');
  }

  // Sync status
  set('syncMode', data.zeroed ? 'Relative (zeroed)' : 'Absolute');

  if (p.connected && m.connected) {
    const off = data.sync_offset_ms;
    const offEl = document.getElementById('syncOffset');
    if (offEl) {
      offEl.textContent = (off >= 0 ? '+' : '') + off.toFixed(1) + ' ms';
      offEl.className = 'sync-offset ' +
        (Math.abs(off) < 20  ? 'good' :
         Math.abs(off) < 100 ? 'ok'   : 'bad');
    }
  }

  // Connection pills
  const phoneConn = document.getElementById('phoneConn');
  const imuConn = document.getElementById('imuConn');
  if (phoneConn) phoneConn.classList.toggle('active', p.connected);
  if (imuConn) imuConn.classList.toggle('active', m.connected);

  // Calibration badges
  const cal = m.calib || [0, 0, 0, 0];
  ['calibSys', 'calibGyr', 'calibAcc', 'calibMag'].forEach((id, i) => {
    const el = document.getElementById(id);
    if (el) {
      el.textContent = ['SYS', 'GYR', 'ACC', 'MAG'][i] + ': ' + cal[i];
      el.className = 'calib-badge calib-' + cal[i];
    }
  });

  // ── Phone panel ──────────────────────────────────────────────
  if (currentMode === 'phone' || currentMode === 'both') {
    const e = p.euler;
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
    const xyzGyroEl = document.getElementById('xyzPhoneGyro');
    if (xyzGyroEl) xyzGyroEl.innerHTML = xyzHtml(gx,gy,gz);
    const xyzMagEl = document.getElementById('xyzPhoneMag');
    if (xyzMagEl) xyzMagEl.innerHTML = xyzHtml(mx,my,mz);
    cubes.phone?.update(e.yaw, e.pitch, e.roll);
  }

  // ── IMU panel ────────────────────────────────────────────────
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
    const [mx,my,mz] = m.mag || [0, 0, 0];
    rollingCharts.imuAccel.push(ax, ay, az);
    rollingCharts.imuGyro .push(gx, gy, gz);
    if (rollingCharts.imuMag) rollingCharts.imuMag.push(mx, my, mz);
    const xyzAEl = document.getElementById('xyzImuAccel');
    if (xyzAEl) xyzAEl.innerHTML = xyzHtml(ax,ay,az);
    const xyzGEl = document.getElementById('xyzImuGyro');
    if (xyzGEl) xyzGEl.innerHTML = xyzHtml(gx,gy,gz);
    const xyzMEl = document.getElementById('xyzImuMag');
    if (xyzMEl) xyzMEl.innerHTML = xyzHtml(mx,my,mz);
    cubes.imu?.update(e.yaw, e.pitch, e.roll);
  }

  // ── Compare panel ────────────────────────────────────────────
  if (currentMode === 'both') {
    const pe = p.euler, me = m.euler;
    set('cmpPhoneRate', p.rate_hz + ' Hz');
    set('cmpImuRate',   m.rate_hz + ' Hz');
    const dY = pe.yaw - me.yaw, dP = pe.pitch - me.pitch, dR = pe.roll - me.roll;
    set('deltaYaw',   fmt(dY, 2) + '°');
    set('deltaPitch', fmt(dP, 2) + '°');
    set('deltaRoll',  fmt(dR, 2) + '°');
    rollingCharts.overlayYaw .push(pe.yaw, me.yaw);
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
}

// Socket listener -> record + handle
socket.on('sensor_data', data => {
  RecordingManager.addFrame(data);
  handleSensorData(data);
});

// ═══════════════════════════════════════════════════════════════
//  KEYBOARD SHORTCUTS
// ═══════════════════════════════════════════════════════════════
document.addEventListener('keydown', e => {
  if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return;
  switch(e.key) {
    case '1': setMode('phone'); break;
    case '2': setMode('imu'); break;
    case '3': setMode('both'); break;
    case ' ': e.preventDefault(); zeroReference(); break;
    case 'r': case 'R': resetReference(); break;
    case 'Escape':
      FullscreenManager.close();
      if (LayoutManager.isOpen) LayoutManager.toggle();
      break;
  }
});

// ═══════════════════════════════════════════════════════════════
//  INIT
// ═══════════════════════════════════════════════════════════════
ThemeManager.init();
LayoutManager.init();
ToastManager.init();
setMode('phone');
