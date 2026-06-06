"""
calibrator.py — Collects posture samples during calibration and computes a baseline.
=====================================================================================
The user sits with good posture for a few seconds. Each frame's metrics
are recorded. After the duration, we average all samples to establish
what "good posture" looks like for this specific user.

Usage:
    calibrator = Calibrator(duration_seconds=5)
    calibrator.start()
    while not calibrator.is_complete():
        metrics = compute_metrics(pts)
        calibrator.add_sample(metrics)
        print(f"Progress: {calibrator.get_progress():.0%}")
    baseline = calibrator.compute_baseline()
"""

import time
from posture_monitor import config


class Calibrator:
    """Collects posture metric samples and computes an averaged baseline."""

    def __init__(self, duration_seconds=None):
        """
        Args:
            duration_seconds: How long to collect samples (default from config).
        """
        self.duration = duration_seconds or config.CALIBRATION_SECONDS
        self._samples = {
            'forward': [],
            'slope': [],
            'tilt': [],
            'nose_shoulder': [],
            'torso': [],
        }
        self._start_time = None

    def start(self):
        """Begin the calibration timer."""
        self._start_time = time.time()
        self._samples = {k: [] for k in self._samples}

    def add_sample(self, metrics):
        """
        Record one frame's metrics during calibration.

        Args:
            metrics: dict from compute_metrics() with the 5 metric keys.
        """
        for key in self._samples:
            if key in metrics:
                self._samples[key].append(metrics[key])

    def get_progress(self):
        """
        Get calibration progress as a float from 0.0 to 1.0.
        Returns 0.0 if calibration hasn't started.
        """
        if self._start_time is None:
            return 0.0
        elapsed = time.time() - self._start_time
        return min(elapsed / self.duration, 1.0)

    def get_remaining(self):
        """Get remaining calibration time in seconds."""
        if self._start_time is None:
            return self.duration
        return max(0, self.duration - (time.time() - self._start_time))

    def is_complete(self):
        """Check if the calibration duration has elapsed."""
        if self._start_time is None:
            return False
        return (time.time() - self._start_time) >= self.duration

    def compute_baseline(self):
        """
        Compute the averaged baseline from collected samples.

        Returns:
            dict with averaged values for each metric.
            Falls back to defaults if no samples were collected.
        """
        # Check if we got any valid samples
        if all(len(v) > 0 for v in self._samples.values()):
            baseline = {
                k: sum(v) / len(v)
                for k, v in self._samples.items()
            }
            print(f">>> Calibration done! {{{', '.join(f'{k}: {v:.3f}' for k, v in baseline.items())}}}")
            return baseline
        else:
            print(">>> No person detected during calibration — using defaults")
            return config.DEFAULT_BASELINE.copy()

    def reset(self):
        """Clear all samples and reset the timer."""
        self._start_time = None
        self._samples = {k: [] for k in self._samples}
