"""
posture_analyzer.py — Compares current posture metrics against a calibrated baseline.
=====================================================================================
After calibration establishes what "good posture" looks like for this user,
the analyzer checks each frame's metrics and determines if posture is
GOOD, BAD (with specific issues), or in ALERT state.

Buzzer Rule (sliding window):
  The buzzer activates ONLY when posture has been bad for ≥25 seconds
  within the last 30 seconds. This prevents false alarms from brief
  movements or adjustments.

Usage:
    analyzer = PostureAnalyzer(baseline)
    result = analyzer.analyze(metrics)
    print(result.state, result.issues, result.buzz_active)
"""

import time
from collections import deque
from dataclasses import dataclass, field
from posture_monitor import config


@dataclass
class PostureResult:
    """Result of analyzing a single frame's posture."""
    state: str = "GOOD"             # "GOOD", "BAD", "ALERT", "NO_PERSON"
    is_bad: bool = False            # True if any issue detected
    issues: list = field(default_factory=list)  # e.g. ["FORWARD HEAD", "SLOUCHING"]
    metrics: dict = field(default_factory=dict) # Raw metric values for this frame
    bad_frame_count: int = 0        # How many consecutive bad frames
    buzz_active: bool = False       # True when 25/30s rule is satisfied
    bad_seconds_in_window: float = 0.0  # How many seconds of bad posture in the window


class PostureAnalyzer:
    """Compares posture metrics against a calibrated baseline."""

    def __init__(self, baseline):
        """
        Args:
            baseline: dict with keys 'forward', 'slope', 'tilt',
                      'nose_shoulder', 'torso' — averaged from calibration.
        """
        self.baseline = baseline
        self.bad_frames = 0

        # Sliding window: deque of (timestamp, is_bad) entries
        # Used to calculate how many seconds of bad posture in the last 30s
        self._window = deque()
        self._last_sample_time = None

        # Compute thresholds from baseline + config multipliers
        self.thresholds = {
            'forward':       baseline['forward'] * config.FORWARD_MULTIPLIER,
            'slope':         baseline['slope'] + config.SLOPE_OFFSET,
            'tilt':          baseline['tilt'] + config.TILT_OFFSET,
            'nose_shoulder': baseline['nose_shoulder'] * config.NOSE_SHOULDER_MULTIPLIER,
            'torso':         baseline['torso'] * config.TORSO_MULTIPLIER,
        }

    def _update_window(self, is_bad):
        """
        Add a sample to the sliding window and prune old entries.

        Each entry stores (timestamp, is_bad). We measure the duration
        each sample covers as the gap between consecutive timestamps.
        """
        now = time.time()
        self._window.append((now, is_bad))

        # Remove entries older than the window
        cutoff = now - config.BAD_POSTURE_WINDOW
        while self._window and self._window[0][0] < cutoff:
            self._window.popleft()

    def _get_bad_seconds(self):
        """
        Calculate total seconds of bad posture within the current window.

        Each sample's duration is the time gap to the next sample.
        The last sample's duration extends to 'now'.
        """
        if len(self._window) < 2:
            return 0.0

        bad_seconds = 0.0
        items = list(self._window)

        for i in range(len(items) - 1):
            ts, is_bad = items[i]
            next_ts = items[i + 1][0]
            if is_bad:
                bad_seconds += (next_ts - ts)

        # Last sample extends to now
        last_ts, last_bad = items[-1]
        if last_bad:
            bad_seconds += (time.time() - last_ts)

        return bad_seconds

    def analyze(self, metrics):
        """
        Analyze a single frame's posture metrics.

        Args:
            metrics: dict from compute_metrics() with keys
                     'forward', 'slope', 'tilt', 'nose_shoulder', 'torso'.

        Returns:
            PostureResult with state, issues, bad frame count, and buzz status.
        """
        issues = []

        if metrics['forward'] > self.thresholds['forward']:
            issues.append("FORWARD HEAD")

        if metrics['slope'] > self.thresholds['slope']:
            issues.append("UNEVEN SHOULDERS")

        if metrics['tilt'] > self.thresholds['tilt']:
            issues.append("HEAD TILT")

        if metrics['nose_shoulder'] < self.thresholds['nose_shoulder']:
            issues.append("SLOUCHING")

        if metrics['torso'] < self.thresholds['torso']:
            issues.append("SPINE NOT STRAIGHT")

        is_bad = len(issues) > 0

        if is_bad:
            self.bad_frames += 1
        else:
            self.bad_frames = 0

        # Update the sliding window
        self._update_window(is_bad)
        bad_seconds = self._get_bad_seconds()

        # Buzzer activates only when bad posture ≥ threshold in the window
        buzz_active = bad_seconds >= config.BAD_POSTURE_THRESHOLD

        # Determine state
        if is_bad and buzz_active:
            state = "ALERT"
        elif is_bad:
            state = "BAD"
        else:
            state = "GOOD"

        return PostureResult(
            state=state,
            is_bad=is_bad,
            issues=issues,
            metrics=metrics,
            bad_frame_count=self.bad_frames,
            buzz_active=buzz_active,
            bad_seconds_in_window=round(bad_seconds, 1),
        )

    def reset(self):
        """Reset the bad frame counter and sliding window."""
        self.bad_frames = 0
        self._window.clear()
