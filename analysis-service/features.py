"""
features.py
------------
Pulls three acoustic features out of a chunk of audio, each compared
against the user's calibrated baseline to decide "is this different from
how they normally sound?":

  - pitch (Hz): via librosa.yin, chosen over the fancier librosa.pyin
    because the spec calls it out as faster, and "faster" matters when we
    have a new chunk arriving every 4 seconds.
  - RMS energy: a simple measure of loudness/vocal effort.
  - silence ratio: what fraction of the chunk has almost no energy at all,
    used both as an "unusual pause" signal and as a cheap proxy for
    "is anyone speaking right now".

None of this is a trained model - it is straightforward signal processing,
which is exactly what the spec asks for ("Do not build a classifier" for
the breathing stretch goal, and nothing here claims to be more than a
heuristic). Keep it that way; resist the urge to add ML here.

Note: `librosa` is imported inside each function that needs it, not at the
top of this file, so this module (and the FastAPI app) can still be
imported before librosa is installed - see audio_utils.py's docstring for
why that matters (the Step 1 skeleton).
"""
from __future__ import annotations

import numpy as np

SAMPLE_RATE = 16000


def compute_pitch_hz(samples: np.ndarray) -> float:
    """Median fundamental frequency across voiced frames, or 0.0 if nothing voiced was found."""
    if samples.size < 512:
        return 0.0
    import librosa

    try:
        f0 = librosa.yin(
            samples,
            fmin=librosa.note_to_hz("C2"),  # ~65 Hz - below the lowest realistic voice
            fmax=librosa.note_to_hz("C7"),  # ~2093 Hz - above the highest realistic voice/shout
            sr=SAMPLE_RATE,
        )
    except Exception:
        return 0.0
    voiced = f0[f0 > 0]
    if voiced.size == 0:
        return 0.0
    return float(np.median(voiced))


def compute_rms(samples: np.ndarray) -> float:
    """Root-mean-square energy of the whole chunk - a simple loudness measure."""
    if samples.size == 0:
        return 0.0
    return float(np.sqrt(np.mean(np.square(samples))))


def compute_silence_ratio(samples: np.ndarray, baseline_rms: float | None,
                           frame_length: int = 1024, hop_length: int = 512) -> float:
    """
    Fraction of short frames in this chunk whose energy is below a "silent"
    floor. The floor adapts to the user's own baseline loudness when one
    exists (25% of their normal RMS), falling back to a fixed small
    constant before calibration.
    """
    if samples.size < frame_length:
        return 1.0  # a near-empty chunk counts as fully silent

    energy_floor = 0.25 * baseline_rms if baseline_rms else 0.01

    import librosa

    frame_rms = librosa.feature.rms(y=samples, frame_length=frame_length, hop_length=hop_length)[0]
    if frame_rms.size == 0:
        return 1.0

    silent_frames = int(np.sum(frame_rms < energy_floor))
    return float(silent_frames / frame_rms.size)
