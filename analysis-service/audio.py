# Everything that touches the actual sound:
#   decode()      webm bytes from the browser -> numpy array (16kHz, mono)
#   transcribe()  speech -> text with faster-whisper
#   measure()     pitch, loudness and silence with librosa
#
# The heavy libraries (av, faster_whisper, librosa) are imported inside the
# functions, so the server can still start in STUB_MODE without them.
import io

import numpy as np

SAMPLE_RATE = 16000
FRAME = 2048  # samples per frame (~0.13s)
HOP = 512

_whisper = None


def decode(webm_bytes):
    """Decode the browser's audio clip in memory (never saved to disk)."""
    import av

    samples = []
    try:
        container = av.open(io.BytesIO(webm_bytes))
        resampler = av.AudioResampler(format="flt", layout="mono", rate=SAMPLE_RATE)
        for frame in container.decode(audio=0):
            frame.pts = None
            for out in resampler.resample(frame):
                samples.append(out.to_ndarray().reshape(-1))
        for out in resampler.resample(None):  # flush what's left
            samples.append(out.to_ndarray().reshape(-1))
        container.close()
    except Exception as e:
        print("Could not decode audio:", e)

    if not samples:
        return np.zeros(0, dtype=np.float32)
    return np.concatenate(samples).astype(np.float32)


def load_whisper():
    global _whisper
    if _whisper is None:
        from faster_whisper import WhisperModel

        print("Loading whisper 'base' model (first time downloads ~150MB)...")
        # "base" understands Hindi/Marathi too. int8 = smaller and faster on a laptop CPU.
        _whisper = WhisperModel("base", device="cpu", compute_type="int8")
    return _whisper


def transcribe(samples, code_words):
    if samples.size == 0:
        return ""
    # initial_prompt = "these words might come up", helps whisper spell the code words right
    segments, _ = load_whisper().transcribe(
        samples, initial_prompt=", ".join(code_words) or None, beam_size=1
    )
    return " ".join(s.text.strip() for s in segments).strip()


def measure(samples, baseline_rms=None):
    """Returns (pitch in Hz, loudness, silence ratio 0-1)."""
    if samples.size < FRAME:
        return 0.0, 0.0, 1.0

    import librosa

    loudness = float(np.sqrt(np.mean(samples ** 2)))

    # split into short frames and measure how loud each one is
    frame_rms = librosa.feature.rms(y=samples, frame_length=FRAME, hop_length=HOP)[0]
    # a frame is "silent" if it's quieter than 25% of the user's normal voice
    floor = 0.25 * baseline_rms if baseline_rms else 0.01
    talking = frame_rms >= floor
    silence_ratio = float(1 - talking.mean())

    # pitch of each frame, but only count frames where the person is talking
    f0 = librosa.yin(samples, fmin=65, fmax=1000, sr=SAMPLE_RATE, frame_length=FRAME, hop_length=HOP)
    n = min(len(f0), len(talking))
    voiced = f0[:n][talking[:n]]
    pitch = float(np.median(voiced)) if voiced.size else 0.0

    return pitch, loudness, silence_ratio
