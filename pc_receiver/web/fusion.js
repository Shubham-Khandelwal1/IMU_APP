const socket = io();

// ── State ─────────────────────────────────────────────────────────────
const COLORS = {
  phone: '#00E5FF',
  chip: '#FFFFFF',
  comp: '#E040FB',
  madg: '#69F0AE',
  maho: '#FFAB40',
  ekf: '#FF5252'
};

const visibility = { phone: true, chip: true, comp: true, madg: true, maho: true, ekf: true };

// Data buffers for rendering (x: time, y1: phone, y2: comp, etc.)
const MAX_POINTS = 200;
let historyYaw = [];
let historyPitch = [];
let historyRoll = [];
let historyErrY = []; 
let historyErrP = []; 
let historyErrR = []; 
let startTime = null;

let isTesting = false;
let testStartTime = 0;
let testData = [];

// ── Setup Canvases ─────────────────────────────────────────────────────
function initCanvas(id) {
  const c = document.getElementById(id);
  const ctx = c.getContext('2d');
  const dpr = window.devicePixelRatio || 1;
  const rect = c.getBoundingClientRect();
  c.width = rect.width * dpr;
  c.height = rect.height * dpr;
  ctx.scale(dpr, dpr);
  return { c, ctx, w: rect.width, h: rect.height };
}

const cvsYaw   = initCanvas('cvsYaw');
const cvsPitch = initCanvas('cvsPitch');
const cvsRoll  = initCanvas('cvsRoll');
const cvsYawErr   = initCanvas('cvsYawErr');
const cvsPitchErr = initCanvas('cvsPitchErr');
const cvsRollErr  = initCanvas('cvsRollErr');

// Handle window resize dynamically
window.addEventListener('resize', () => {
  ['cvsYaw', 'cvsPitch', 'cvsRoll', 'cvsYawErr', 'cvsPitchErr', 'cvsRollErr'].forEach(id => {
    const c = document.getElementById(id);
    if(!c) return;
    const ctx = c.getContext('2d');
    const dpr = window.devicePixelRatio || 1;
    const rect = c.getBoundingClientRect();
    c.width = rect.width * dpr;
    c.height = rect.height * dpr;
    ctx.scale(dpr, dpr);
    // Find matching dict
    let tgt = id === 'cvsYaw' ? cvsYaw : id === 'cvsPitch' ? cvsPitch : id === 'cvsRoll' ? cvsRoll : 
              id === 'cvsYawErr' ? cvsYawErr : id === 'cvsPitchErr' ? cvsPitchErr : cvsRollErr;
    tgt.w = rect.width;
    tgt.h = rect.height;
  });
});

// ── Three.js Spatial Setup ─────────────────────────────────────────────
let scene, camera, renderer, cubes = {};
function initThreeJS() {
  const c = document.getElementById('cvsFusionCube');
  if(!c) return;
  scene = new THREE.Scene();
  // We use orthographic to prevent distortion since it's just meant for comparison
  const aspect = c.clientWidth / c.clientHeight;
  const d = 3;
  camera = new THREE.OrthographicCamera(-d * aspect, d * aspect, d, -d, 1, 1000);
  camera.position.set(5, 5, 5);
  camera.lookAt(scene.position);

  renderer = new THREE.WebGLRenderer({ canvas: c, alpha: true, antialias: true });
  renderer.setPixelRatio(window.devicePixelRatio);
  renderer.setSize(c.clientWidth, c.clientHeight);

  const colors = { phone: 0x00E5FF, chip: 0xFFFFFF, comp: 0xE040FB, madg: 0x69F0AE, maho: 0xFFAB40, ekf: 0xFF5252 };
  const geo = new THREE.BoxGeometry(3, 0.4, 1.8);
  
  // Phone Reference Cube
  const matP = new THREE.MeshBasicMaterial({ color: colors.phone, transparent: true, opacity: 0.8 });
  cubes['phone'] = new THREE.Mesh(geo, matP);
  const edgesP = new THREE.LineSegments(new THREE.EdgesGeometry(geo), new THREE.LineBasicMaterial({ color: 0xffffff, opacity: 0.5, transparent: true }));
  cubes['phone'].add(edgesP);
  scene.add(cubes['phone']);

  // Filters (Wireframe Ghost)
  ['chip', 'comp', 'madg', 'maho', 'ekf'].forEach(k => {
    const geoFilter = new THREE.EdgesGeometry(geo);
    const matEdge = new THREE.LineBasicMaterial({ color: colors[k], linewidth: 2, transparent: true, opacity: k==='chip'?0.4:0.9 });
    cubes[k] = new THREE.LineSegments(geoFilter, matEdge);
    scene.add(cubes[k]);
  });
}
initThreeJS();
window.addEventListener('resize', () => {
    const c = document.getElementById('cvsFusionCube');
    if(!c || !renderer) return;
    const aspect = c.clientWidth / c.clientHeight;
    camera.left = -3 * aspect;
    camera.right = 3 * aspect;
    camera.updateProjectionMatrix();
    renderer.setSize(c.clientWidth, c.clientHeight);
});


// ── Socket Handlers ───────────────────────────────────────────────────

socket.on('connect', () => document.getElementById('connStatus').innerText = 'Connected');
socket.on('disconnect', () => document.getElementById('connStatus').innerText = 'Offline');

socket.on('fusion_data', (data) => {
  if (!startTime) startTime = data.phone.rel_ts || (Date.now()/1000);
  const currentT = Date.now()/1000 - startTime;
  
  // Format point for charts
  const ptY = { t: currentT, phone: data.phone.yaw, chip: data.chip.yaw, comp: data.complementary.yaw, madg: data.madgwick.yaw, maho: data.mahony.yaw, ekf: data.ekf.yaw };
  const ptP = { t: currentT, phone: data.phone.pitch, chip: data.chip.pitch, comp: data.complementary.pitch, madg: data.madgwick.pitch, maho: data.mahony.pitch, ekf: data.ekf.pitch };
  const ptR = { t: currentT, phone: data.phone.roll, chip: data.chip.roll, comp: data.complementary.roll, madg: data.madgwick.roll, maho: data.mahony.roll, ekf: data.ekf.roll };
  
  historyYaw.push(ptY);
  historyPitch.push(ptP);
  historyRoll.push(ptR);
  
  // Angle difference function for error handling wraparound
  const diff = (a, b) => {
    let d = a - b;
    while(d > 180) d -= 360;
    while(d < -180) d += 360;
    return d;
  };
  
  const getErr = (ax) => {
    return {
      t: currentT,
      comp: Math.abs(diff(data.phone[ax], data.complementary[ax])),
      madg: Math.abs(diff(data.phone[ax], data.madgwick[ax])),
      maho: Math.abs(diff(data.phone[ax], data.mahony[ax])),
      ekf:  Math.abs(diff(data.phone[ax], data.ekf[ax]))
    }
  };
  
  historyErrY.push(getErr('yaw'));
  historyErrP.push(getErr('pitch'));
  historyErrR.push(getErr('roll'));
  
  if (historyYaw.length > MAX_POINTS) {
    historyYaw.shift(); historyPitch.shift(); historyRoll.shift(); 
    historyErrY.shift(); historyErrP.shift(); historyErrR.shift();
  }
  
  const rMSErr = (ref, tst) => Math.sqrt((diff(ref.yaw, tst.yaw)**2 + diff(ref.pitch, tst.pitch)**2 + diff(ref.roll, tst.roll)**2) / 3);
  
  // Test Recording
  if (isTesting) {
    const ptE = {
        t: currentT, 
        comp: rMSErr(data.phone, data.complementary),
        madg: rMSErr(data.phone, data.madgwick),
        maho: rMSErr(data.phone, data.mahony),
        ekf: rMSErr(data.phone, data.ekf)
    };
    testData.push({y: ptY, p: ptP, r: ptR, e: ptE, t: currentT - testStartTime});
  }
  
  // Render Checks
  requestAnimationFrame(() => {
    drawChart(cvsYaw, historyYaw, [-180, 180]);
    drawChart(cvsPitch, historyPitch, [-90, 90]);
    drawChart(cvsRoll, historyRoll, [-180, 180]);
    drawErrChart(cvsYawErr, historyErrY);
    drawErrChart(cvsPitchErr, historyErrP);
    drawErrChart(cvsRollErr, historyErrR);

    // Update ThreeJS Rotations
    if(renderer && scene) {
      const keys = ['phone', 'chip', 'comp', 'madg', 'maho', 'ekf'];
      const ref = { phone: data.phone, chip: data.chip, comp: data.complementary, madg: data.madgwick, maho: data.mahony, ekf: data.ekf };
      keys.forEach(k => {
          if(!cubes[k]) return;
          cubes[k].visible = !!visibility[k];
          if(visibility[k]) {
             const rY = ref[k].yaw * Math.PI/180;
             const rP = ref[k].pitch * Math.PI/180;
             const rR = ref[k].roll * Math.PI/180;
             // Unity-style mapping
             cubes[k].rotation.set(-rP, -rY, -rR, 'YXZ');
          }
      });
      renderer.render(scene, camera);
    }
  });
  
  // Update compute cost text
  document.getElementById('compCost').innerText = data.compute_us.complementary + ' µs';
  document.getElementById('madgCost').innerText = data.compute_us.madgwick + ' µs';
  document.getElementById('mahoCost').innerText = data.compute_us.mahony + ' µs';
  document.getElementById('ekfCost').innerText = data.compute_us.ekf + ' µs';
  
  window.last_compute = data.compute_us;
});

// ── Rendering Engine ──────────────────────────────────────────────────

function drawChart(cvs, data, range) {
  const {ctx, w, h} = cvs;
  ctx.clearRect(0, 0, w, h);
  if(data.length < 2) return;
  
  // Graph boundaries
  const pad = 20;
  const gw = w;
  const gh = h - pad*2;
  
  const tMin = data[0].t;
  const tMax = data[data.length-1].t;
  const dt = tMax - tMin || 1;
  const [vMin, vMax] = range;
  const dv = vMax - vMin;
  
  // Grid lines
  ctx.strokeStyle = 'rgba(255,255,255,0.05)';
  ctx.lineWidth = 1;
  ctx.beginPath();
  for(let v=vMin; v<=vMax; v+=45) {
    let y = pad + gh - ((v - vMin) / dv) * gh;
    ctx.moveTo(0, y); ctx.lineTo(w, y);
  }
  ctx.stroke();
  
  // Zero line
  ctx.strokeStyle = 'rgba(255,255,255,0.1)';
  ctx.beginPath();
  let zY = pad + gh - ((0 - vMin) / dv) * gh;
  ctx.moveTo(0, zY); ctx.lineTo(w, zY);
  ctx.stroke();
  
  // Function to map values to x,y
  const px = t => ((t - tMin) / dt) * gw;
  const py = v => pad + gh - ((v - vMin) / dv) * gh;
  
  // Draw each series
  ['phone', 'chip', 'comp', 'madg', 'maho', 'ekf'].forEach(key => {
    if (!visibility[key]) return;
    
    ctx.beginPath();
    ctx.strokeStyle = COLORS[key];
    ctx.lineWidth = key === 'phone' ? 2 : 1.5;
    if (key === 'phone') ctx.setLineDash([4,4]);
    else if (key === 'chip') ctx.setLineDash([2,4]);
    else ctx.setLineDash([]);
    
    // Check for wraps
    ctx.moveTo(px(data[0].t), py(data[0][key]));
    for(let i=1; i<data.length; i++) {
        let yPrev = data[i-1][key];
        let yCurr = data[i][key];
        // Don't draw line across wrap boundaries
        let wrapThreshold = (vMax - vMin) * 0.8;
        if (Math.abs(yCurr - yPrev) > wrapThreshold) {
            ctx.moveTo(px(data[i].t), py(yCurr));
        } else {
            ctx.lineTo(px(data[i].t), py(yCurr));
        }
    }
    ctx.stroke();
  });
  ctx.setLineDash([]);
}

function drawErrChart(cvs, data) {
  const {ctx, w, h} = cvs;
  ctx.clearRect(0, 0, w, h);
  if(data.length < 2) return;
  
  // Find max error
  let maxE = 0.1;
  for(let pt of data) {
      if(visibility.comp) maxE = Math.max(maxE, pt.comp);
      if(visibility.madg) maxE = Math.max(maxE, pt.madg);
      if(visibility.maho) maxE = Math.max(maxE, pt.maho);
      if(visibility.ekf) maxE = Math.max(maxE, pt.ekf);
  }
  maxE = Math.ceil(maxE / 5) * 5; // round up to multiple of 5
  if(maxE > 45) maxE = 45;
  
  const pad = 10;
  const gw = w;
  const gh = h - pad*2;
  const tMin = data[0].t;
  const tMax = data[data.length-1].t;
  const dt = tMax - tMin || 1;
  const px = t => ((t - tMin) / dt) * gw;
  const py = v => pad + gh - (v / maxE) * gh;
  
  // Text label for Max Error
  ctx.fillStyle = 'rgba(255,255,255,0.3)';
  ctx.font = '10px JetBrains Mono';
  ctx.fillText(maxE + '°', 5, pad);
  
  // Draw lines
  ['comp', 'madg', 'maho', 'ekf'].forEach(key => {
    if (!visibility[key]) return;
    ctx.beginPath();
    ctx.strokeStyle = COLORS[key];
    ctx.lineWidth = 1.5;
    ctx.moveTo(px(data[0].t), py(data[0][key]));
    for(let i=1; i<data.length; i++) ctx.lineTo(px(data[i].t), py(data[i][key]));
    ctx.stroke();
  });
}

// ── UI Controls ───────────────────────────────────────────────────────

document.querySelectorAll('.legend-item').forEach(item => {
  item.addEventListener('click', (e) => {
    const id = e.currentTarget.dataset.id;
    visibility[id] = !visibility[id];
    
    // Update all matching legend items
    document.querySelectorAll(`.legend-item[data-id="${id}"]`).forEach(el => {
      el.classList.toggle('dim', !visibility[id]);
    });
  });
});

function bindSlider(idSlider, idVal, filter, param) {
  const sl = document.getElementById(idSlider);
  const v = document.getElementById(idVal);
  sl.addEventListener('input', () => {
    v.innerText = parseFloat(sl.value).toFixed(sl.step.includes('00') ? 3 : 2);
    socket.emit('set_fusion_param', {filter: filter, param: param, value: parseFloat(sl.value)});
  });
}

bindSlider('slCompAlpha', 'valCompAlpha', 'complementary', 'alpha');
bindSlider('slMadgBeta',  'valMadgBeta',  'madgwick',      'beta');
bindSlider('slMahoKp',    'valMahoKp',    'mahony',        'kp');
bindSlider('slMahoKi',    'valMahoKi',    'mahony',        'ki');
bindSlider('slEkfQ',      'valEkfQ',      'ekf',           'process_noise');
bindSlider('slEkfR',      'valEkfR',      'ekf',           'measurement_noise');

function resetFilters() {
  socket.emit('reset_fusion');
}

function runAutoTune() {
  const btn = document.getElementById('btnAutoTune');
  if(!btn) return;
  btn.innerText = "Recording (10s)...";
  btn.classList.add('active');
  socket.emit('start_recording');
  
  setTimeout(() => {
    btn.innerText = "Optimizing...";
    socket.emit('stop_recording');
    setTimeout(() => {
      socket.emit('run_auto_tune');
    }, 500);
  }, 10000);
}

socket.on('auto_tune_result', (res) => {
  const btn = document.getElementById('btnAutoTune');
  if(btn) {
      btn.innerText = "Auto Tune Gains";
      btn.classList.remove('active');
  }
  
  if(res.error) {
    alert("Auto-tune failed: " + res.error);
    return;
  }
  
  // Update sliders
  document.getElementById('slCompAlpha').value = res.complementary.alpha;
  document.getElementById('valCompAlpha').innerText = res.complementary.alpha.toFixed(3);
  document.getElementById('slMadgBeta').value = res.madgwick.beta;
  document.getElementById('valMadgBeta').innerText = res.madgwick.beta.toFixed(2);
  document.getElementById('slMahoKp').value = res.mahony.kp;
  document.getElementById('valMahoKp').innerText = res.mahony.kp.toFixed(2);
  
  alert("Auto-Tuning Complete! Check the updated sliders.");
});

// ── Metrics Scoring & Testing ─────────────────────────────────────────

function toggleTest() {
  const btn = document.getElementById('btnTest');
  if(!isTesting) {
    testData = [];
    testStartTime = Date.now()/1000;
    isTesting = true;
    btn.innerText = "Stop Scoring Run";
    btn.classList.add('active');
    
    // Clear table
    const keys = ['Comp','Madg','Maho','Ekf'];
    for(let k of keys) {
      document.getElementById(`m${k}Rms`).innerText = "—";
      document.getElementById(`m${k}Max`).innerText = "—";
      document.getElementById(`m${k}Drift`).innerText = "—";
      document.getElementById(`m${k}Noise`).innerText = "—";
      document.getElementById(`m${k}Us`).innerText = "—";
      document.getElementById(`score${k}`).innerText = "—";
    }
  } else {
    isTesting = false;
    btn.innerText = "Start Scoring Run";
    btn.classList.remove('active');
    computeScores();
  }
}

function computeScores() {
  if (testData.length < 50) {
    alert("Not enough data to score (need >1s)");
    return;
  }
  
  const N = testData.length;
  const dt = testData[N-1].t - testData[0].t;
  
  // Metric accumulators
  let res = {
    comp: { rmsSq: 0, maxE: 0, dV: 0 },
    madg: { rmsSq: 0, maxE: 0, dV: 0 },
    maho: { rmsSq: 0, maxE: 0, dV: 0 },
    ekf:  { rmsSq: 0, maxE: 0, dV: 0 }
  };
  
  // 1. Dynamic Error (RMS & Max)
  for(let pt of testData) {
    for(let k of ['comp','madg','maho','ekf']) {
      res[k].rmsSq += pt.e[k]**2;
      res[k].maxE = Math.max(res[k].maxE, pt.e[k]);
    }
  }
  
  for(let k of Object.keys(res)) {
    res[k].rms = Math.sqrt(res[k].rmsSq / N);
    document.getElementById(`m${k[0].toUpperCase()+k.substring(1)}Rms`).innerText = res[k].rms.toFixed(2);
    document.getElementById(`m${k[0].toUpperCase()+k.substring(1)}Max`).innerText = res[k].maxE.toFixed(2);
    
    // 2. Drift (rate of change over test period)
    const errFirst = testData[10].e[k];
    const errLast = testData[N-10].e[k];
    res[k].driftMin = (Math.abs(errLast - errFirst) / dt) * 60; // °/min
    document.getElementById(`m${k[0].toUpperCase()+k.substring(1)}Drift`).innerText = res[k].driftMin.toFixed(2);
    
    // 3. Noise (std dev of error)
    const meanE = Math.sqrt(res[k].rmsSq/N);
    let varSq = 0;
    for(let pt of testData) varSq += (pt.e[k] - meanE)**2;
    res[k].noise = Math.sqrt(varSq / N);
    document.getElementById(`m${k[0].toUpperCase()+k.substring(1)}Noise`).innerText = res[k].noise.toFixed(2);
    
    // 4. Compute cost
    res[k].us = window.last_compute ? window.last_compute[k === 'ekf' ? 'ekf' : k === 'comp' ? 'complementary' : k === 'madg' ? 'madgwick' : 'mahony'] : 0;
    document.getElementById(`m${k[0].toUpperCase()+k.substring(1)}Us`).innerText = res[k].us;
  }
  
  // Calculate Final Score (0-100)
  // Weights: RMS (30), Drift (30), Noise (20), Max (10), Compute (10)
  for(let k of Object.keys(res)) {
     // Baseline acceptable limits mapping to 0 pts (linear scaling up to 100)
     const s_rms = Math.max(0, 100 - (res[k].rms / 5.0)*100) * 0.30;
     const s_drf = Math.max(0, 100 - (res[k].driftMin / 10.0)*100) * 0.30;
     const s_noi = Math.max(0, 100 - (res[k].noise / 2.0)*100) * 0.20;
     const s_max = Math.max(0, 100 - (res[k].maxE / 15.0)*100) * 0.10;
     const s_cmp = Math.max(0, 100 - (res[k].us / 500.0)*100) * 0.10;
     
     let totalScore = s_rms + s_drf + s_noi + s_max + s_cmp;
     document.getElementById(`score${k[0].toUpperCase()+k.substring(1)}`).innerText = Math.round(totalScore);
  }
  
  // Draw Radar chart
  const cvs = document.getElementById('cvsRadar');
  const ctx = cvs.getContext('2d');
  const w = cvs.width;
  const h = cvs.height;
  const cx = w/2;
  const cy = h/2;
  const rMax = Math.min(cx, cy) - 40;
  
  ctx.clearRect(0,0,w,h);
  
  const labels = ['Tracking (RMS)', 'Max Error', 'Stability (Drift)', 'Noise', 'Compute Cost'];
  const numAxis = labels.length;
  const angleStep = (Math.PI * 2) / numAxis;
  
  // Background webs
  ctx.strokeStyle = 'rgba(255,255,255,0.1)';
  ctx.lineWidth = 1;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.font = '10px Inter';
  
  for(let level=1; level<=5; level++) {
    const r = (rMax / 5) * level;
    ctx.beginPath();
    for(let i=0; i<numAxis; i++) {
        let a = i * angleStep - Math.PI/2;
        let x = cx + Math.cos(a) * r;
        let y = cy + Math.sin(a) * r;
        if(i===0) ctx.moveTo(x,y);
        else ctx.lineTo(x,y);
    }
    ctx.closePath();
    ctx.stroke();
  }
  
  // Axes and Labels
  for(let i=0; i<numAxis; i++) {
    let a = i * angleStep - Math.PI/2;
    ctx.beginPath();
    ctx.moveTo(cx, cy);
    ctx.lineTo(cx + Math.cos(a)*rMax, cy + Math.sin(a)*rMax);
    ctx.stroke();
    
    ctx.fillStyle = 'rgba(255,255,255,0.5)';
    let lx = cx + Math.cos(a)*(rMax + 20);
    let ly = cy + Math.sin(a)*(rMax + 15);
    ctx.fillText(labels[i], lx, ly);
  }
  
  const colors = { comp: '#E040FB', madg: '#69F0AE', maho: '#FFAB40', ekf: '#FF5252' };
  
  for(let k of Object.keys(res)) {
      if(!visibility[k]) continue;
      
      const s_rms = Math.max(0, 100 - (res[k].rms / 5.0)*100);
      const s_max = Math.max(0, 100 - (res[k].maxE / 15.0)*100);
      const s_drf = Math.max(0, 100 - (res[k].driftMin / 10.0)*100);
      const s_noi = Math.max(0, 100 - (res[k].noise / 2.0)*100);
      const s_cmp = Math.max(0, 100 - (res[k].us / 500.0)*100);
      
      const pts = [s_rms, s_max, s_drf, s_noi, s_cmp];
      
      ctx.beginPath();
      for(let i=0; i<numAxis; i++) {
          let a = i * angleStep - Math.PI/2;
          let r = (pts[i] / 100) * rMax;
          let x = cx + Math.cos(a) * r;
          let y = cy + Math.sin(a) * r;
          if(i===0) ctx.moveTo(x,y);
          else ctx.lineTo(x,y);
      }
      ctx.closePath();
      
      ctx.fillStyle = colors[k] + '33'; // 20% opacity
      ctx.fill();
      ctx.strokeStyle = colors[k];
      ctx.lineWidth = 2;
      ctx.stroke();
  }
}
