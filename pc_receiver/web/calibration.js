const socket = io();

let currentSource = 'phone'; // 'phone' or 'imu'
let isCollecting = false;
let collectedPoints = []; // array of [x, y, z]

const MAX_POINTS = 5000;

// ── UI Setup ──────────────────────────────────────────────────
function setSource(src) {
  currentSource = src;
  document.getElementById('btnPhone').className = 'src-btn ' + (src === 'phone' ? 'active-phone' : '');
  document.getElementById('btnImu').className   = 'src-btn ' + (src === 'imu' ? 'active-imu' : '');
  clearPoints();
}

function toggleCollection() {
  isCollecting = !isCollecting;
  const btn = document.getElementById('btnStart');
  const dot = document.getElementById('dotRec');
  const txt = document.getElementById('btnStartText');
  
  if (isCollecting) {
    btn.classList.replace('start', 'stop');
    dot.classList.add('rec-dot-pulse');
    txt.textContent = "Stop Collection";
  } else {
    btn.classList.replace('stop', 'start');
    dot.classList.remove('rec-dot-pulse');
    txt.textContent = "Start Collection";
  }
}

function clearPoints() {
  collectedPoints = [];
  document.getElementById('numPoints').textContent = 0;
  document.getElementById('btnCompute').disabled = true;
  updateRawCloud();
  clearCorrectedCloud();
  
  // reset UI matrix
  const ids = ['biasX','biasY','biasZ', 'w00','w01','w02','w10','w11','w12','w20','w21','w22'];
  ids.forEach(id => {
    document.getElementById(id).textContent = id.startsWith('w') && id[1]===id[2] ? '1.000' : '0.000';
  });
}

function computeCalibration() {
  if (collectedPoints.length < 10) return;
  document.getElementById('btnCompute').disabled = true;
  document.getElementById('btnCompute').textContent = "Computing...";
  
  socket.emit('compute_mag_calibration', { points: collectedPoints });
}

socket.on('mag_calibration_result', (res) => {
  document.getElementById('btnCompute').disabled = false;
  document.getElementById('btnCompute').textContent = "Compute Matrix";
  
  if (res.error) {
    alert("Error: " + res.error);
    return;
  }
  
  const V = res.hard_iron;
  const W = res.soft_iron;
  
  // UI Format
  document.getElementById('biasX').textContent = V[0].toFixed(3);
  document.getElementById('biasY').textContent = V[1].toFixed(3);
  document.getElementById('biasZ').textContent = V[2].toFixed(3);
  
  document.getElementById('w00').textContent = W[0][0].toFixed(3);
  document.getElementById('w01').textContent = W[0][1].toFixed(3);
  document.getElementById('w02').textContent = W[0][2].toFixed(3);
  document.getElementById('w10').textContent = W[1][0].toFixed(3);
  document.getElementById('w11').textContent = W[1][1].toFixed(3);
  document.getElementById('w12').textContent = W[1][2].toFixed(3);
  document.getElementById('w20').textContent = W[2][0].toFixed(3);
  document.getElementById('w21').textContent = W[2][1].toFixed(3);
  document.getElementById('w22').textContent = W[2][2].toFixed(3);
  
  // Compute corrected points natively in JS for display
  const corPts = [];
  for (let i = 0; i < collectedPoints.length; i++) {
    const P = collectedPoints[i];
    // P_shifted = P - V
    const px = P[0] - V[0];
    const py = P[1] - V[1];
    const pz = P[2] - V[2];
    
    // P_scaled = M_shifted * W^T
    // c_x = px*w00 + py*w01 + pz*w02
    const cx = px * W[0][0] + py * W[0][1] + pz * W[0][2];
    const cy = px * W[1][0] + py * W[1][1] + pz * W[1][2];
    const cz = px * W[2][0] + py * W[2][1] + pz * W[2][2];
    
    corPts.push([cx, cy, cz]);
  }
  
  updateCorrectedCloud(corPts);
});

// ── Socket Handler ────────────────────────────────────────────
socket.on('sensor_data', data => {
  const isPhoneMsg = (data.phone && data.phone.connected);
  const isImuMsg = (data.imu && data.imu.connected);
  
  if (currentSource === 'phone') {
    document.getElementById('connStatus').textContent = isPhoneMsg ? "Phone Connected" : "Phone Disconnected";
    document.getElementById('connStatus').style.color = isPhoneMsg ? "#00E5FF" : "";
  } else {
    document.getElementById('connStatus').textContent = isImuMsg ? "IMU Connected" : "IMU Disconnected";
    document.getElementById('connStatus').style.color = isImuMsg ? "#FF6D00" : "";
  }
  
  if (!isCollecting) return;
  
  let mx = 0, my = 0, mz = 0;
  if (currentSource === 'phone' && isPhoneMsg) {
    [mx, my, mz] = data.phone.mag;
  } else if (currentSource === 'imu' && isImuMsg && data.imu.mag) {
    [mx, my, mz] = data.imu.mag; // Note: current backend imu data format might not include mag, make sure it does.
  } else {
    return;
  }
  
  // Skip trivial (0,0,0) usually invalid
  if (mx === 0 && my === 0 && mz === 0) return;
  
  if (collectedPoints.length < MAX_POINTS) {
    collectedPoints.push([mx, my, mz]);
    document.getElementById('numPoints').textContent = collectedPoints.length;
    
    if (collectedPoints.length >= 10) {
      document.getElementById('btnCompute').disabled = false;
    }
    
    // throttle render updates to roughly 10hz to save CPU
    if (collectedPoints.length % 5 === 0) {
      updateRawCloud();
    }
  } else {
    if (isCollecting) toggleCollection(); // auto stop
  }
});


// ── Three.js Scene ────────────────────────────────────────────
const canvas = document.getElementById('magCanvas');
const renderer = new THREE.WebGLRenderer({ canvas, alpha: true, antialias: true });
renderer.setPixelRatio(window.devicePixelRatio);

const scene = new THREE.Scene();
const camera = new THREE.PerspectiveCamera(45, 1, 0.1, 1000);
camera.position.set(100, 100, 150);

const controls = new THREE.OrbitControls(camera, renderer.domElement);
controls.enableDamping = true;
controls.dampingFactor = 0.05;

// Reference Sphere (target 50uT)
const sphereGeo = new THREE.SphereGeometry(50, 32, 32);
const sphereMat = new THREE.MeshBasicMaterial({ color: 0x69F0AE, wireframe: true, transparent: true, opacity: 0.15 });
const refSphere = new THREE.Mesh(sphereGeo, sphereMat);
scene.add(refSphere);

// Axes helper
const axesHelper = new THREE.AxesHelper(60);
scene.add(axesHelper);

// Point clouds
const rawGeo = new THREE.BufferGeometry();
const rawMat = new THREE.PointsMaterial({ color: 0xFF5252, size: 2.5, sizeAttenuation: true });
const rawPointsMesh = new THREE.Points(rawGeo, rawMat);
scene.add(rawPointsMesh);

const corGeo = new THREE.BufferGeometry();
const corMat = new THREE.PointsMaterial({ color: 0x69F0AE, size: 3.5, sizeAttenuation: true });
const corPointsMesh = new THREE.Points(corGeo, corMat);
scene.add(corPointsMesh);

function resize() {
  const container = canvas.parentElement;
  const w = container.clientWidth - 32; // padding
  const h = container.clientHeight - 32;
  
  renderer.setSize(w, h, false);
  camera.aspect = w / h;
  camera.updateProjectionMatrix();
}
window.addEventListener('resize', resize);
setTimeout(resize, 100); // init

function animate() {
  requestAnimationFrame(animate);
  controls.update();
  renderer.render(scene, camera);
}
animate();

function updateRawCloud() {
  const positions = new Float32Array(collectedPoints.length * 3);
  for (let i=0; i<collectedPoints.length; i++) {
    positions[i*3] = collectedPoints[i][0];
    positions[i*3+1] = collectedPoints[i][1];
    positions[i*3+2] = collectedPoints[i][2];
  }
  rawGeo.setAttribute('position', new THREE.BufferAttribute(positions, 3));
  rawGeo.computeBoundingSphere();
}

function clearCorrectedCloud() {
  corGeo.setAttribute('position', new THREE.BufferAttribute(new Float32Array(), 3));
}

function updateCorrectedCloud(pts) {
  const positions = new Float32Array(pts.length * 3);
  for (let i=0; i<pts.length; i++) {
    positions[i*3] = pts[i][0];
    positions[i*3+1] = pts[i][1];
    positions[i*3+2] = pts[i][2];
  }
  corGeo.setAttribute('position', new THREE.BufferAttribute(positions, 3));
  corGeo.computeBoundingSphere();
}
