"""
pose_detector.py — MediaPipe Pose landmark extraction.
=======================================================
Wraps MediaPipe Pose into a reusable class so the main loop
doesn't need to know about MediaPipe internals.

Usage:
    detector = PoseDetector()
    pts, results = detector.detect(frame)
    # pts is a dict of named landmark points, or None if no person found
    detector.release()
"""

import cv2
import mediapipe as mp


class PoseDetector:
    """Extracts body landmarks from a video frame using MediaPipe Pose."""

    def __init__(self, min_detection=0.5, min_tracking=0.5):
        """
        Args:
            min_detection: Minimum confidence for initial pose detection (0-1).
            min_tracking:  Minimum confidence for landmark tracking (0-1).
        """
        self._mp_pose = mp.solutions.pose
        self._mp_draw = mp.solutions.drawing_utils
        self._pose = self._mp_pose.Pose(
            min_detection_confidence=min_detection,
            min_tracking_confidence=min_tracking,
        )

    def detect(self, frame):
        """
        Run pose detection on a single BGR frame.

        Args:
            frame: OpenCV BGR image (numpy array).

        Returns:
            (pts, results) where:
              - pts: dict with keys 'nose', 'ls', 'rs', 'le', 're', 'lh', 'rh'
                     as (x, y) pixel coordinates, or None if no person found.
              - results: raw MediaPipe results (needed for drawing landmarks).
        """
        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        results = self._pose.process(rgb)

        if not results.pose_landmarks:
            return None, results

        h, w, _ = frame.shape
        lm = results.pose_landmarks.landmark

        # Extract the 7 key landmarks we need for posture analysis
        pts = {
            'nose': (int(lm[0].x * w), int(lm[0].y * h)),   # Nose tip
            'ls':   (int(lm[11].x * w), int(lm[11].y * h)),  # Left shoulder
            'rs':   (int(lm[12].x * w), int(lm[12].y * h)),  # Right shoulder
            'le':   (int(lm[7].x * w), int(lm[7].y * h)),    # Left ear
            're':   (int(lm[8].x * w), int(lm[8].y * h)),    # Right ear
            'lh':   (int(lm[23].x * w), int(lm[23].y * h)),  # Left hip
            'rh':   (int(lm[24].x * w), int(lm[24].y * h)),  # Right hip
        }

        return pts, results

    def draw_landmarks(self, frame, results):
        """Draw MediaPipe pose skeleton on the frame."""
        if results.pose_landmarks:
            self._mp_draw.draw_landmarks(
                frame,
                results.pose_landmarks,
                self._mp_pose.POSE_CONNECTIONS,
            )

    def release(self):
        """Clean up MediaPipe resources."""
        self._pose.close()
