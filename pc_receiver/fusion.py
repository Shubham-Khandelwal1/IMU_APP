"""
AHRS Sensor Fusion Library
===========================
Four attitude estimation algorithms with identical interfaces.

Each filter:
  - Maintains internal quaternion state [w, x, y, z]
  - update(accel, gyro, mag, dt) — feed raw sensor data
  - get_euler() -> {"yaw": float, "pitch": float, "roll": float}  (degrees)
  - get_quaternion() -> [w, x, y, z]
  - reset() — re-initialize to identity

Units expected:
  accel: m/s^2 (3-element list)
  gyro:  deg/s (converted to rad/s internally)
  mag:   uT    (3-element list, used for yaw reference)
"""

import math
import numpy as np

DEG2RAD = math.pi / 180.0
RAD2DEG = 180.0 / math.pi


def _normalize(v):
    """Normalize a numpy array, return zeros if norm is near-zero."""
    n = np.linalg.norm(v)
    return v / n if n > 1e-10 else np.zeros_like(v)


def _quat_to_euler(q):
    """Convert quaternion [w,x,y,z] to Euler angles [yaw, pitch, roll] in degrees.
    Uses ZYX (aerospace) convention."""
    w, x, y, z = q
    # Roll (X)
    sinr = 2.0 * (w * x + y * z)
    cosr = 1.0 - 2.0 * (x * x + y * y)
    roll = math.atan2(sinr, cosr)
    # Pitch (Y)
    sinp = 2.0 * (w * y - z * x)
    sinp = max(-1.0, min(1.0, sinp))
    pitch = math.asin(sinp)
    # Yaw (Z)
    siny = 2.0 * (w * z + x * y)
    cosy = 1.0 - 2.0 * (y * y + z * z)
    yaw = math.atan2(siny, cosy)
    return {"yaw": yaw * RAD2DEG, "pitch": pitch * RAD2DEG, "roll": roll * RAD2DEG}


def _quat_multiply(a, b):
    """Multiply two quaternions [w,x,y,z]."""
    return np.array([
        a[0]*b[0] - a[1]*b[1] - a[2]*b[2] - a[3]*b[3],
        a[0]*b[1] + a[1]*b[0] + a[2]*b[3] - a[3]*b[2],
        a[0]*b[2] - a[1]*b[3] + a[2]*b[0] + a[3]*b[1],
        a[0]*b[3] + a[1]*b[2] - a[2]*b[1] + a[3]*b[0],
    ])


def _angle_diff(target, current):
    d = target - current
    while d > 180.0: d -= 360.0
    while d < -180.0: d += 360.0
    return d

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
#  1. COMPLEMENTARY FILTER
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

class ComplementaryFilter:
    """
    Classic alpha-blend complementary filter.
    
    angle = alpha * (angle + gyro * dt) + (1 - alpha) * accel_angle
    
    Works in Euler angle space directly (simple but has gimbal lock limitations).
    Parameter: alpha (0.80 - 0.99, default 0.98)
    """
    
    def __init__(self, alpha=0.98):
        self.alpha = alpha
        self.roll = 0.0
        self.pitch = 0.0
        self.yaw = 0.0
        self._initialized = False
    
    def reset(self):
        self.roll = 0.0
        self.pitch = 0.0
        self.yaw = 0.0
        self._initialized = False
    
    def update(self, accel, gyro, mag, dt):
        if dt <= 0:
            return
        
        ax, ay, az = accel
        gx, gy, gz = [g * DEG2RAD for g in gyro]
        mx, my, mz = mag
        
        # Accelerometer-based pitch and roll
        accel_roll  = math.atan2(ay, az) * RAD2DEG
        accel_pitch = math.atan2(-ax, math.sqrt(ay*ay + az*az)) * RAD2DEG
        
        # Magnetometer-based yaw (tilt-compensated)
        roll_r  = self.roll * DEG2RAD if self._initialized else accel_roll * DEG2RAD
        pitch_r = self.pitch * DEG2RAD if self._initialized else accel_pitch * DEG2RAD
        
        mx_comp = mx * math.cos(pitch_r) + mz * math.sin(pitch_r)
        my_comp = (mx * math.sin(roll_r) * math.sin(pitch_r) 
                   + my * math.cos(roll_r) 
                   - mz * math.sin(roll_r) * math.cos(pitch_r))
        mag_yaw = math.atan2(-my_comp, mx_comp) * RAD2DEG
        
        if not self._initialized:
            self.roll  = accel_roll
            self.pitch = accel_pitch
            self.yaw   = mag_yaw
            self._initialized = True
            return
        
        # Integrate gyro (in degrees)
        gyro_roll  = self.roll  + gx * RAD2DEG * dt
        gyro_pitch = self.pitch + gy * RAD2DEG * dt
        gyro_yaw   = self.yaw   + gz * RAD2DEG * dt
        
        # Complementary blend with proper angle wrapping
        a = self.alpha
        self.roll  = gyro_roll  + (1 - a) * _angle_diff(accel_roll, gyro_roll)
        self.pitch = gyro_pitch + (1 - a) * _angle_diff(accel_pitch, gyro_pitch)
        self.yaw   = gyro_yaw   + (1 - a) * _angle_diff(mag_yaw, gyro_yaw)
        
        # Wrap to +/- 180
        self.roll  = _angle_diff(self.roll, 0)
        self.pitch = _angle_diff(self.pitch, 0)
        self.yaw   = _angle_diff(self.yaw, 0)
    
    def get_euler(self):
        return {"yaw": self.yaw, "pitch": self.pitch, "roll": self.roll}
    
    def get_quaternion(self):
        # Convert current Euler to quaternion for consistency
        y, p, r = self.yaw * DEG2RAD, self.pitch * DEG2RAD, self.roll * DEG2RAD
        cy, sy = math.cos(y/2), math.sin(y/2)
        cp, sp = math.cos(p/2), math.sin(p/2)
        cr, sr = math.cos(r/2), math.sin(r/2)
        return [
            cr*cp*cy + sr*sp*sy,
            sr*cp*cy - cr*sp*sy,
            cr*sp*cy + sr*cp*sy,
            cr*cp*sy - sr*sp*cy,
        ]
    
    def set_params(self, **kwargs):
        if "alpha" in kwargs:
            self.alpha = float(kwargs["alpha"])


class ComplementaryQuatFilter:
    """
    Quaternion-based Complementary Filter using Spherical Linear Interpolation (SLERP).
    
    Prevents gimbal lock while keeping the classic alpha-blend simplicity.
    Parameter: alpha (0.80 - 0.99, default 0.98)
    """
    
    def __init__(self, alpha=0.98):
        self.alpha = alpha
        self.q = np.array([1.0, 0.0, 0.0, 0.0])
    
    def reset(self):
        self.q = np.array([1.0, 0.0, 0.0, 0.0])
        
    def _slerp(self, q1, q2, t):
        # Normalize to prevent accumulated errors
        q1 = _normalize(q1)
        q2 = _normalize(q2)
        dot = np.sum(q1 * q2)
        
        # Ensure shortest path
        if dot < 0.0:
            q1 = -q1
            dot = -dot
            
        # If very close, use linear interpolation
        if dot > 0.9995:
            res = q1 + t * (q2 - q1)
            return _normalize(res)
            
        theta_0 = math.acos(dot)
        theta = theta_0 * t
        sin_theta = math.sin(theta)
        sin_theta_0 = math.sin(theta_0)
        
        s0 = math.cos(theta) - dot * sin_theta / sin_theta_0
        s1 = sin_theta / sin_theta_0
        
        return _normalize((s0 * q1) + (s1 * q2))

    def update(self, accel, gyro, mag, dt):
        if dt <= 0: return
        
        # 1. Integrate Gyro (Predicted Quaternion)
        gx, gy, gz = [g * DEG2RAD for g in gyro]
        qDot = 0.5 * _quat_multiply(self.q, [0, gx, gy, gz])
        q_pred = _normalize(self.q + qDot * dt)
        
        # 2. Absolute Orientation from Accel/Mag
        ax, ay, az = accel
        mx, my, mz = mag
        
        if np.linalg.norm(accel) < 1e-10:
            self.q = q_pred
            return
            
        accel_roll  = math.atan2(ay, az)
        accel_pitch = math.atan2(-ax, math.sqrt(ay*ay + az*az))
        
        # Tilt-compensated mag
        my_comp = mx * math.sin(accel_roll) * math.sin(accel_pitch) + my * math.cos(accel_roll) - mz * math.sin(accel_roll) * math.cos(accel_pitch)
        mx_comp = mx * math.cos(accel_pitch) + mz * math.sin(accel_pitch)
        mag_yaw = math.atan2(-my_comp, mx_comp) if np.linalg.norm(mag) > 1e-10 else 0.0
        
        # Convert absolute euler to quaternion
        cy, sy = math.cos(mag_yaw/2), math.sin(mag_yaw/2)
        cp, sp = math.cos(accel_pitch/2), math.sin(accel_pitch/2)
        cr, sr = math.cos(accel_roll/2), math.sin(accel_roll/2)
        
        q_am = np.array([
            cr*cp*cy + sr*sp*sy,
            sr*cp*cy - cr*sp*sy,
            cr*sp*cy + sr*cp*sy,
            cr*cp*sy - sr*sp*cy,
        ])
        
        # 3. Slerp
        self.q = self._slerp(q_am, q_pred, self.alpha)

    def get_euler(self):
        return _quat_to_euler(self.q)
    
    def get_quaternion(self):
        return self.q.tolist()
        
    def set_params(self, **kwargs):
        if "alpha" in kwargs:
            self.alpha = float(kwargs["alpha"])
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
#  2. MADGWICK FILTER
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

class MadgwickFilter:
    """
    Madgwick's gradient descent AHRS algorithm (2010).
    
    Uses accelerometer and magnetometer as reference vectors to correct
    gyro-integrated quaternion via gradient descent on an error surface.
    
    Parameter: beta (0.01 - 0.50, default 0.1)
      - Higher beta = faster convergence but noisier
      - Lower beta  = smoother but slower to correct drift
    """
    
    def __init__(self, beta=0.1):
        self.beta = beta
        self.q = np.array([1.0, 0.0, 0.0, 0.0])  # [w, x, y, z]
    
    def reset(self):
        self.q = np.array([1.0, 0.0, 0.0, 0.0])
    
    def update(self, accel, gyro, mag, dt):
        if dt <= 0:
            return
        
        q = self.q.copy()
        q1, q2, q3, q4 = q  # w, x, y, z
        
        gx, gy, gz = [g * DEG2RAD for g in gyro]
        a = _normalize(np.array(accel, dtype=float))
        m = _normalize(np.array(mag, dtype=float))
        
        if np.linalg.norm(a) < 1e-10 or np.linalg.norm(m) < 1e-10:
            # No valid reference — just integrate gyro
            qDot = 0.5 * _quat_multiply(q, [0, gx, gy, gz])
            self.q = _normalize(q + qDot * dt)
            return
        
        ax, ay, az = a
        mx, my, mz = m
        
        # Auxiliary variables
        _2q1 = 2*q1; _2q2 = 2*q2; _2q3 = 2*q3; _2q4 = 2*q4
        _4q1 = 4*q1; _4q2 = 4*q2; _4q3 = 4*q3
        _8q2 = 8*q2; _8q3 = 8*q3
        q1q1 = q1*q1; q2q2 = q2*q2; q3q3 = q3*q3; q4q4 = q4*q4
        q1q2 = q1*q2; q1q3 = q1*q3; q1q4 = q1*q4
        q2q3 = q2*q3; q2q4 = q2*q4; q3q4 = q3*q4
        
        # Reference direction of Earth's magnetic field
        _2q1mx = 2*q1*mx; _2q1my = 2*q1*my; _2q1mz = 2*q1*mz
        _2q2mx = 2*q2*mx
        hx = mx*q1q1 - _2q1my*q4 + _2q1mz*q3 + mx*q2q2 + _2q2*my*q3 + _2q2*mz*q4 - mx*q3q3 - mx*q4q4
        hy = _2q1mx*q4 + my*q1q1 - _2q1mz*q2 + _2q2mx*q3 - my*q2q2 + my*q3q3 + _2q3*mz*q4 - my*q4q4
        _2bx = math.sqrt(hx*hx + hy*hy)
        _2bz = -_2q1mx*q3 + _2q1my*q2 + mz*q1q1 + _2q2mx*q4 - mz*q2q2 + _2q3*my*q4 - mz*q3q3 + mz*q4q4
        _4bx = 2*_2bx; _4bz = 2*_2bz
        
        # Gradient descent step
        s1 = (-_2q3*(2*q2q4 - _2q1*q3 - ax) + _2q2*(2*q1q2 + _2q3*q4 - ay)
               - _2bz*q3*(_2bx*(0.5 - q3q3 - q4q4) + _2bz*(q2q4 - q1q3) - mx)
               + (-_2bx*q4 + _2bz*q2)*(_2bx*(q2q3 - q1q4) + _2bz*(q1q2 + q3q4) - my)
               + _2bx*q3*(_2bx*(q1q3 + q2q4) + _2bz*(0.5 - q2q2 - q3q3) - mz))
        
        s2 = (_2q4*(2*q2q4 - _2q1*q3 - ax) + _2q1*(2*q1q2 + _2q3*q4 - ay)
              - 4*q2*(1 - 2*q2q2 - 2*q3q3 - az)
              + _2bz*q4*(_2bx*(0.5 - q3q3 - q4q4) + _2bz*(q2q4 - q1q3) - mx)
              + (_2bx*q3 + _2bz*q1)*(_2bx*(q2q3 - q1q4) + _2bz*(q1q2 + q3q4) - my)
              + (_2bx*q4 - _4bz*q2)*(_2bx*(q1q3 + q2q4) + _2bz*(0.5 - q2q2 - q3q3) - mz))
        
        s3 = (-_2q1*(2*q2q4 - _2q1*q3 - ax) + _2q4*(2*q1q2 + _2q3*q4 - ay)
              - 4*q3*(1 - 2*q2q2 - 2*q3q3 - az)
              + (-_4bx*q3 - _2bz*q1)*(_2bx*(0.5 - q3q3 - q4q4) + _2bz*(q2q4 - q1q3) - mx)
              + (_2bx*q2 + _2bz*q4)*(_2bx*(q2q3 - q1q4) + _2bz*(q1q2 + q3q4) - my)
              + (_2bx*q1 - _4bz*q3)*(_2bx*(q1q3 + q2q4) + _2bz*(0.5 - q2q2 - q3q3) - mz))
        
        s4 = (_2q2*(2*q2q4 - _2q1*q3 - ax) + _2q3*(2*q1q2 + _2q3*q4 - ay)
              + (-_4bx*q4 + _2bz*q2)*(_2bx*(0.5 - q3q3 - q4q4) + _2bz*(q2q4 - q1q3) - mx)
              + (-_2bx*q1 + _2bz*q3)*(_2bx*(q2q3 - q1q4) + _2bz*(q1q2 + q3q4) - my)
              + _2bx*q2*(_2bx*(q1q3 + q2q4) + _2bz*(0.5 - q2q2 - q3q3) - mz))
        
        s = _normalize(np.array([s1, s2, s3, s4]))
        
        # Rate of change of quaternion
        qDot = 0.5 * _quat_multiply(q, [0, gx, gy, gz]) - self.beta * s
        
        # Integrate
        self.q = _normalize(q + qDot * dt)
    
    def get_euler(self):
        return _quat_to_euler(self.q)
    
    def get_quaternion(self):
        return self.q.tolist()
    
    def set_params(self, **kwargs):
        if "beta" in kwargs:
            self.beta = float(kwargs["beta"])


# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
#  3. MAHONY FILTER
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

class MahonyFilter:
    """
    Mahony's Proportional-Integral AHRS filter (2008).
    
    Uses cross-product error between measured and estimated gravity/mag vectors,
    fed into a PI controller that corrects the gyro before quaternion integration.
    
    Parameters:
      Kp (0.1 - 10.0, default 1.0): Proportional gain — convergence speed
      Ki (0.0 - 1.0,  default 0.0): Integral gain — gyro bias tracking
    """
    
    def __init__(self, kp=1.0, ki=0.0):
        self.kp = kp
        self.ki = ki
        self.q = np.array([1.0, 0.0, 0.0, 0.0])
        self.integral_error = np.array([0.0, 0.0, 0.0])
    
    def reset(self):
        self.q = np.array([1.0, 0.0, 0.0, 0.0])
        self.integral_error = np.array([0.0, 0.0, 0.0])
    
    def update(self, accel, gyro, mag, dt):
        if dt <= 0:
            return
        
        q = self.q.copy()
        q1, q2, q3, q4 = q
        gx, gy, gz = [g * DEG2RAD for g in gyro]
        
        a = _normalize(np.array(accel, dtype=float))
        m = _normalize(np.array(mag, dtype=float))
        
        if np.linalg.norm(a) < 1e-10:
            qDot = 0.5 * _quat_multiply(q, [0, gx, gy, gz])
            self.q = _normalize(q + qDot * dt)
            return
        
        # Estimated direction of gravity from quaternion
        vx = 2*(q2*q4 - q1*q3)
        vy = 2*(q1*q2 + q3*q4)
        vz = q1*q1 - q2*q2 - q3*q3 + q4*q4
        
        # Error is cross product between measured and estimated gravity
        ex = a[1]*vz - a[2]*vy
        ey = a[2]*vx - a[0]*vz
        ez = a[0]*vy - a[1]*vx
        
        # Magnetometer correction
        if np.linalg.norm(m) > 1e-10:
            # Reference direction of Earth's magnetic field
            hx = 2*(m[0]*(0.5 - q3*q3 - q4*q4) + m[1]*(q2*q3 - q1*q4) + m[2]*(q2*q4 + q1*q3))
            hy = 2*(m[0]*(q2*q3 + q1*q4) + m[1]*(0.5 - q2*q2 - q4*q4) + m[2]*(q3*q4 - q1*q2))
            bx = math.sqrt(hx*hx + hy*hy)
            bz = 2*(m[0]*(q2*q4 - q1*q3) + m[1]*(q3*q4 + q1*q2) + m[2]*(0.5 - q2*q2 - q3*q3))
            
            # Estimated mag direction
            wx = bx*(0.5 - q3*q3 - q4*q4) + bz*(q2*q4 - q1*q3)
            wy = bx*(q2*q3 - q1*q4) + bz*(q1*q2 + q3*q4)
            wz = bx*(q1*q3 + q2*q4) + bz*(0.5 - q2*q2 - q3*q3)
            
            # Cross product error for mag
            ex += m[1]*wz - m[2]*wy
            ey += m[2]*wx - m[0]*wz
            ez += m[0]*wy - m[1]*wx
        
        # Integral feedback
        if self.ki > 0:
            self.integral_error += np.array([ex, ey, ez]) * dt
            gx += self.kp * ex + self.ki * self.integral_error[0]
            gy += self.kp * ey + self.ki * self.integral_error[1]
            gz += self.kp * ez + self.ki * self.integral_error[2]
        else:
            gx += self.kp * ex
            gy += self.kp * ey
            gz += self.kp * ez
        
        # Integrate quaternion
        qDot = 0.5 * _quat_multiply(q, [0, gx, gy, gz])
        self.q = _normalize(q + qDot * dt)
    
    def get_euler(self):
        return _quat_to_euler(self.q)
    
    def get_quaternion(self):
        return self.q.tolist()
    
    def set_params(self, **kwargs):
        if "kp" in kwargs:
            self.kp = float(kwargs["kp"])
        if "ki" in kwargs:
            self.ki = float(kwargs["ki"])


# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
#  4. EXTENDED KALMAN FILTER (EKF)
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

class EKFFilter:
    """
    Extended Kalman Filter for attitude estimation.
    
    State vector: quaternion [q0, q1, q2, q3] (4 elements)
    Process model: quaternion kinematics from gyro
    Measurement model: gravity vector (accel) + magnetic field vector (mag)
    
    Parameters:
      process_noise (default 0.01): Scales Q matrix — how much we trust the gyro
      measurement_noise (default 0.5): Scales R matrix — how much we trust accel/mag
    
    Higher process_noise = less trust in gyro = more responsive to accel/mag
    Higher measurement_noise = less trust in accel/mag = smoother but more drift
    """
    
    def __init__(self, process_noise=0.01, measurement_noise=0.5):
        self.process_noise = process_noise
        self.measurement_noise = measurement_noise
        self.q = np.array([1.0, 0.0, 0.0, 0.0])
        self.P = np.eye(4) * 1.0  # State covariance
    
    def reset(self):
        self.q = np.array([1.0, 0.0, 0.0, 0.0])
        self.P = np.eye(4) * 1.0
    
    def update(self, accel, gyro, mag, dt):
        if dt <= 0:
            return
        
        gx, gy, gz = [g * DEG2RAD for g in gyro]
        a = np.array(accel, dtype=float)
        m = np.array(mag, dtype=float)
        q = self.q.copy()
        
        # ── PREDICT ──────────────────────────────────────────
        # State transition: q_new = q + 0.5 * Omega(gyro) * q * dt
        Omega = np.array([
            [0,  -gx, -gy, -gz],
            [gx,  0,   gz, -gy],
            [gy, -gz,  0,   gx],
            [gz,  gy, -gx,  0],
        ])
        
        F = np.eye(4) + 0.5 * Omega * dt  # State transition Jacobian
        q_pred = F @ q
        q_pred = q_pred / np.linalg.norm(q_pred)
        
        Q = np.eye(4) * self.process_noise * dt  # Process noise
        P_pred = F @ self.P @ F.T + Q
        
        # ── UPDATE (accelerometer) ───────────────────────────
        a_norm = np.linalg.norm(a)
        if a_norm > 1e-10:
            a_n = a / a_norm
            
            # Expected gravity in body frame from predicted quaternion
            q0, q1, q2, q3 = q_pred
            g_pred = np.array([
                2*(q1*q3 - q0*q2),
                2*(q0*q1 + q2*q3),
                q0*q0 - q1*q1 - q2*q2 + q3*q3,
            ])
            
            # Measurement Jacobian (dg/dq)
            H_a = 2 * np.array([
                [-q2,  q3, -q0,  q1],
                [ q1,  q0,  q3,  q2],
                [ q0, -q1, -q2,  q3],
            ])
            
            # Innovation
            y_a = a_n - g_pred
            
            R_a = np.eye(3) * self.measurement_noise
            S_a = H_a @ P_pred @ H_a.T + R_a
            
            try:
                K_a = P_pred @ H_a.T @ np.linalg.inv(S_a)
                q_pred = q_pred + K_a @ y_a
                q_pred = q_pred / np.linalg.norm(q_pred)
                P_pred = (np.eye(4) - K_a @ H_a) @ P_pred
            except np.linalg.LinAlgError:
                pass  # Skip update if singular
        
        # ── UPDATE (magnetometer) ────────────────────────────
        m_norm = np.linalg.norm(m)
        if m_norm > 1e-10:
            m_n = m / m_norm
            q0, q1, q2, q3 = q_pred
            
            # Rotate mag to earth frame, flatten to horizontal
            R_mat = np.array([
                [q0*q0+q1*q1-q2*q2-q3*q3, 2*(q1*q2-q0*q3),         2*(q1*q3+q0*q2)],
                [2*(q1*q2+q0*q3),         q0*q0-q1*q1+q2*q2-q3*q3, 2*(q2*q3-q0*q1)],
                [2*(q1*q3-q0*q2),         2*(q2*q3+q0*q1),         q0*q0-q1*q1-q2*q2+q3*q3],
            ])
            h = R_mat @ m_n
            bx = math.sqrt(h[0]**2 + h[1]**2)
            bz = h[2]
            
            # Expected mag in body frame
            m_pred = np.array([
                bx*(q0*q0+q1*q1-q2*q2-q3*q3) + bz*2*(q1*q3-q0*q2),
                bx*2*(q1*q2-q0*q3) + bz*2*(q0*q1+q2*q3),
                bx*2*(q0*q2+q1*q3) + bz*(q0*q0-q1*q1-q2*q2+q3*q3),
            ])
            
            # Simplified Jacobian for mag (numerical approximation)
            eps = 1e-5
            H_m = np.zeros((3, 4))
            for i in range(4):
                q_plus = q_pred.copy()
                q_plus[i] += eps
                q_plus = q_plus / np.linalg.norm(q_plus)
                q0p, q1p, q2p, q3p = q_plus
                
                R_p = np.array([
                    [q0p*q0p+q1p*q1p-q2p*q2p-q3p*q3p, 2*(q1p*q2p-q0p*q3p),           2*(q1p*q3p+q0p*q2p)],
                    [2*(q1p*q2p+q0p*q3p),           q0p*q0p-q1p*q1p+q2p*q2p-q3p*q3p, 2*(q2p*q3p-q0p*q1p)],
                    [2*(q1p*q3p-q0p*q2p),           2*(q2p*q3p+q0p*q1p),           q0p*q0p-q1p*q1p-q2p*q2p+q3p*q3p],
                ])
                h_p = R_p @ m_n
                bxp = math.sqrt(h_p[0]**2 + h_p[1]**2)
                bzp = h_p[2]
                m_pred_p = np.array([
                    bxp*(q0p*q0p+q1p*q1p-q2p*q2p-q3p*q3p) + bzp*2*(q1p*q3p-q0p*q2p),
                    bxp*2*(q1p*q2p-q0p*q3p) + bzp*2*(q0p*q1p+q2p*q3p),
                    bxp*2*(q0p*q2p+q1p*q3p) + bzp*(q0p*q0p-q1p*q1p-q2p*q2p+q3p*q3p),
                ])
                H_m[:, i] = (m_pred_p - m_pred) / eps
            
            y_m = m_n - m_pred
            R_m = np.eye(3) * self.measurement_noise * 2  # Mag is noisier
            S_m = H_m @ P_pred @ H_m.T + R_m
            
            try:
                K_m = P_pred @ H_m.T @ np.linalg.inv(S_m)
                q_pred = q_pred + K_m @ y_m
                q_pred = q_pred / np.linalg.norm(q_pred)
                P_pred = (np.eye(4) - K_m @ H_m) @ P_pred
            except np.linalg.LinAlgError:
                pass
        
        self.q = q_pred
        self.P = P_pred
    
    def get_euler(self):
        return _quat_to_euler(self.q)
    
    def get_quaternion(self):
        return self.q.tolist()
    
    def set_params(self, **kwargs):
        if "process_noise" in kwargs:
            self.process_noise = float(kwargs["process_noise"])
        if "measurement_noise" in kwargs:
            self.measurement_noise = float(kwargs["measurement_noise"])
