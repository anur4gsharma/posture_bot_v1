"""
posture_analyzer.py — Compares current posture metrics against a calibrated baseline.
=====================================================================================
After calibration establishes what "good posture" looks like for this user,
the analyzer checks each frame's metrics and determines if posture is
GOOD, BAD (with specific issues), or in ALERT state (persistent bad posture).

Usage:
    analyzer = PostureAnalyzer(baseline)
    result = analyzer.analyze(metrics)
    print(result.state, result.issues)
"""

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

        # Compute thresholds from baseline + config multipliers
        self.thresholds = {
            'forward':       baseline['forward'] * config.FORWARD_MULTIPLIER,
            'slope':         baseline['slope'] + config.SLOPE_OFFSET,
            'tilt':          baseline['tilt'] + config.TILT_OFFSET,
            'nose_shoulder': baseline['nose_shoulder'] * config.NOSE_SHOULDER_MULTIPLIER,
            'torso':         baseline['torso'] * config.TORSO_MULTIPLIER,
        }

    def analyze(self, metrics):
        """
        Analyze a single frame's posture metrics.

        Args:
            metrics: dict from compute_metrics() with keys
                     'forward', 'slope', 'tilt', 'nose_shoulder', 'torso'.

        Returns:
            PostureResult with state, issues, and bad frame count.
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

        # Determine state
        if is_bad and self.bad_frames > config.ALERT_THRESHOLD:
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
        )

    def reset(self):
        """Reset the bad frame counter."""
        self.bad_frames = 0
