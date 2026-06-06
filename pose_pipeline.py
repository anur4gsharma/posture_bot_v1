import cv2
import mediapipe as mp
import math
import time

mp_pose = mp.solutions.pose
mp_draw = mp.solutions.drawing_utils

pose = mp_pose.Pose(
    min_detection_confidence=0.5,
    min_tracking_confidence=0.5
)

# ---------- CHANGE THIS LINE ----------
# Replace with your phone's IP stream
cap = cv2.VideoCapture("https://10.232.46.104:8080/video")
# --------------------------------------

bad_frames = 0
ALERT_THRESHOLD = 30
CALIBRATION_SECONDS = 5


def distance(p1, p2):
    return math.sqrt((p1[0]-p2[0])**2 + (p1[1]-p2[1])**2)


def shoulder_slope(ls, rs):
    return abs(rs[1]-ls[1]) / (abs(rs[0]-ls[0]) + 1e-5)


def head_tilt(le, re):
    angle = math.degrees(math.atan2(re[1]-le[1], re[0]-le[0]))
    angle = abs(angle)
    if angle > 90:
        angle = 180 - angle
    return angle


def nose_to_shoulder_dist(nose, ls, rs):
    dx = rs[0] - ls[0]
    dy = rs[1] - ls[1]
    line_len = math.sqrt(dx*dx + dy*dy) + 1e-5
    dist = abs(dx*(ls[1]-nose[1]) - dy*(ls[0]-nose[0])) / line_len
    return dist / line_len


def get_landmarks(frame):

    rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
    results = pose.process(rgb)

    if not results.pose_landmarks:
        return None, results

    h, w, _ = frame.shape
    lm = results.pose_landmarks.landmark

    pts = {
        'nose': (int(lm[0].x*w), int(lm[0].y*h)),
        'ls':   (int(lm[11].x*w), int(lm[11].y*h)),
        'rs':   (int(lm[12].x*w), int(lm[12].y*h)),
        'le':   (int(lm[7].x*w), int(lm[7].y*h)),
        're':   (int(lm[8].x*w), int(lm[8].y*h)),
        'lh':   (int(lm[23].x*w), int(lm[23].y*h)),
        'rh':   (int(lm[24].x*w), int(lm[24].y*h)),
    }

    return pts, results


def compute_metrics(pts):

    nose, ls, rs, le, re = pts['nose'], pts['ls'], pts['rs'], pts['le'], pts['re']
    lh, rh = pts['lh'], pts['rh']

    sw = distance(ls, rs)

    sc = (int((ls[0]+rs[0])/2), int((ls[1]+rs[1])/2))
    hc = (int((lh[0]+rh[0])/2), int((lh[1]+rh[1])/2))

    torso_len = distance(sc, hc) / sw

    return {
        'forward':       distance(nose, sc) / sw,
        'slope':         shoulder_slope(ls, rs),
        'tilt':          head_tilt(le, re),
        'nose_shoulder': nose_to_shoulder_dist(nose, ls, rs),
        'torso':         torso_len,
    }


# ===================== CALIBRATION =====================

print(">>> Sit with GOOD POSTURE. Calibrating for", CALIBRATION_SECONDS, "seconds...")

cal_samples = {'forward': [], 'slope': [], 'tilt': [], 'nose_shoulder': [], 'torso': []}
cal_start = time.time()

while time.time() - cal_start < CALIBRATION_SECONDS:

    ret, frame = cap.read()

    if not ret:
        break

    frame = cv2.flip(frame, 1)
    h, w, _ = frame.shape

    pts, results = get_landmarks(frame)

    remaining = max(0, CALIBRATION_SECONDS - (time.time() - cal_start))

    banner_color = (200, 120, 0)

    overlay = frame.copy()
    cv2.rectangle(overlay, (0, 0), (w, 90), banner_color, -1)
    cv2.addWeighted(overlay, 0.6, frame, 0.4, 0, frame)

    cv2.putText(frame, "CALIBRATING - Sit with good posture",
                (20, 35), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (255,255,255), 2)

    cv2.putText(frame, f"Time left: {remaining:.1f}s",
                (20, 70), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255,255,255), 2)

    if pts:

        metrics = compute_metrics(pts)

        for k in cal_samples:
            cal_samples[k].append(metrics[k])

        mp_draw.draw_landmarks(frame, results.pose_landmarks, mp_pose.POSE_CONNECTIONS)

    cv2.imshow("Posture Detection", frame)

    if cv2.waitKey(1) & 0xFF == 27:
        cap.release()
        cv2.destroyAllWindows()
        exit()

# Baseline
if all(len(v) > 0 for v in cal_samples.values()):

    baseline = {k: sum(v)/len(v) for k, v in cal_samples.items()}

    print(">>> Calibration done!", {k: round(v,3) for k,v in baseline.items()})

else:

    baseline = {'forward':0.75,'slope':0.08,'tilt':5.0,'nose_shoulder':0.7,'torso':1.5}

    print(">>> No person detected during calibration")

# Thresholds
FORWARD_THRESH       = baseline['forward'] * 1.35
SLOPE_THRESH         = baseline['slope'] + 0.15
TILT_THRESH          = baseline['tilt'] + 15
NOSE_SHOULDER_THRESH = baseline['nose_shoulder'] * 0.65
TORSO_THRESH         = baseline['torso'] * 0.90


# ===================== MAIN LOOP =====================

while True:

    ret, frame = cap.read()

    if not ret:
        break

    frame = cv2.flip(frame, 1)

    h, w, _ = frame.shape

    pts, results = get_landmarks(frame)

    posture_status = "GOOD POSTURE"
    status_color = (0,200,0)
    detail_text = ""
    bad_posture = False

    if pts:

        metrics = compute_metrics(pts)

        nose, ls, rs = pts['nose'], pts['ls'], pts['rs']

        issues = []

        if metrics['forward'] > FORWARD_THRESH:
            issues.append("FORWARD HEAD")

        if metrics['slope'] > SLOPE_THRESH:
            issues.append("UNEVEN SHOULDERS")

        if metrics['tilt'] > TILT_THRESH:
            issues.append("HEAD TILT")

        if metrics['nose_shoulder'] < NOSE_SHOULDER_THRESH:
            issues.append("SLOUCHING")

        if metrics['torso'] < TORSO_THRESH:
            issues.append("SPINE NOT STRAIGHT")

        bad_posture = len(issues) > 0

        if bad_posture:
            bad_frames += 1
            posture_status = "BAD POSTURE"
            status_color = (0,0,255)
            detail_text = " | ".join(issues)
        else:
            bad_frames = 0

        mp_draw.draw_landmarks(frame, results.pose_landmarks, mp_pose.POSE_CONNECTIONS)

        cv2.line(frame, ls, rs, (255,255,255), 2)
        cv2.circle(frame, nose, 5, (0,0,255), -1)

    else:

        posture_status = "NO PERSON DETECTED"
        status_color = (128,128,128)

    banner_h = 80

    overlay = frame.copy()

    cv2.rectangle(overlay, (0,0),(w,banner_h),status_color,-1)

    cv2.addWeighted(overlay,0.55,frame,0.45,0,frame)

    cv2.putText(frame,posture_status,(20,35),
                cv2.FONT_HERSHEY_SIMPLEX,1,(255,255,255),3)

    if detail_text:

        cv2.putText(frame,detail_text,(20,65),
                    cv2.FONT_HERSHEY_SIMPLEX,0.6,(255,255,255),2)

    if bad_frames > ALERT_THRESHOLD:

        bar_y = h-50

        cv2.rectangle(frame,(0,bar_y),(w,h),(0,0,180),-1)

        cv2.putText(frame,"!! FIX YOUR POSTURE !!",
                    (int(w/2)-180,bar_y+35),
                    cv2.FONT_HERSHEY_SIMPLEX,0.9,(255,255,255),2)

    cv2.imshow("Posture Detection",frame)

    if cv2.waitKey(1) & 0xFF == 27:
        break


cap.release()
cv2.destroyAllWindows()