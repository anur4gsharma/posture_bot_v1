"""
metrics.py — Pure math functions for posture metric computation.
================================================================
These functions have ZERO external dependencies (no OpenCV, no MediaPipe).
They take simple (x, y) tuples and return numbers.

Metrics computed:
  - forward:       How far the nose is from the shoulder center (normalized)
  - slope:         How tilted the shoulders are (left vs right)
  - tilt:          How much the head is tilted sideways (ear alignment)
  - nose_shoulder: Perpendicular distance from nose to shoulder line (normalized)
  - torso:         Torso length normalized by shoulder width
"""

import math


def distance(p1, p2):
    """Euclidean distance between two 2D points."""
    return math.sqrt((p1[0] - p2[0]) ** 2 + (p1[1] - p2[1]) ** 2)


def shoulder_slope(ls, rs):
    """
    Slope between left and right shoulders.
    A value close to 0 means level shoulders.
    """
    return abs(rs[1] - ls[1]) / (abs(rs[0] - ls[0]) + 1e-5)


def head_tilt(le, re):
    """
    Head tilt angle from ear positions, in degrees.
    0 means perfectly level head. Higher = more tilted.
    """
    angle = math.degrees(math.atan2(re[1] - le[1], re[0] - le[0]))
    angle = abs(angle)
    if angle > 90:
        angle = 180 - angle
    return angle


def nose_to_shoulder_dist(nose, ls, rs):
    """
    Normalized perpendicular distance from nose to the shoulder line.
    Lower values indicate slouching (nose dropping toward shoulder line).
    """
    dx = rs[0] - ls[0]
    dy = rs[1] - ls[1]
    line_len = math.sqrt(dx * dx + dy * dy) + 1e-5
    dist = abs(dx * (ls[1] - nose[1]) - dy * (ls[0] - nose[0])) / line_len
    return dist / line_len


def compute_metrics(pts):
    """
    Compute all five posture metrics from landmark points.

    Args:
        pts: dict with keys 'nose', 'ls', 'rs', 'le', 're', 'lh', 'rh',
             each being an (x, y) tuple in pixel coordinates.

    Returns:
        dict with keys: 'forward', 'slope', 'tilt', 'nose_shoulder', 'torso'
    """
    nose = pts['nose']
    ls, rs = pts['ls'], pts['rs']
    le, re = pts['le'], pts['re']
    lh, rh = pts['lh'], pts['rh']

    # Shoulder width (used for normalization)
    sw = distance(ls, rs)

    # Shoulder center and hip center
    sc = (int((ls[0] + rs[0]) / 2), int((ls[1] + rs[1]) / 2))
    hc = (int((lh[0] + rh[0]) / 2), int((lh[1] + rh[1]) / 2))

    # Torso length normalized by shoulder width
    torso_len = distance(sc, hc) / sw

    return {
        'forward':       distance(nose, sc) / sw,
        'slope':         shoulder_slope(ls, rs),
        'tilt':          head_tilt(le, re),
        'nose_shoulder': nose_to_shoulder_dist(nose, ls, rs),
        'torso':         torso_len,
    }
