// Records YOUR microphone in 4 second clips and sends each clip to the analysis service.
//
// Two things we learned the hard way:
// 1. We make a NEW MediaRecorder for every clip. If you use one recorder with
//    start(4000), only the first clip has the WebM header and the server
//    can't read the rest.
// 2. If the server is still busy with the last clip, we skip this one
//    instead of queueing it. Otherwise alerts would arrive later and later.
const Recorder = {
  CLIP_MS: 4000,
  stream: null,
  recorder: null,
  sessionId: null,
  running: false,
  busy: false,
  location: { latitude: null, longitude: null },
  watchId: null,
  onResult: null,
  onError: null,

  start(stream, onResult, onError, shareLocation) {
    if (this.running) return;
    this.stream = stream;
    this.onResult = onResult;
    this.onError = onError;
    this.sessionId = crypto.randomUUID(); // one id per call
    this.running = true;
    if (shareLocation) this.watchLocation();
    this.recordClip();
  },

  stop() {
    if (!this.running) return;
    this.running = false;
    if (this.recorder && this.recorder.state !== "inactive") {
      this.recorder.stop();
    }
    if (this.watchId != null) navigator.geolocation.clearWatch(this.watchId);
    this.watchId = null;
    this.location = { latitude: null, longitude: null };
    // tell the server to forget this call's text and scores
    Api.Analysis.endSession(this.sessionId).catch(() => {});
  },

  recordClip() {
    if (!this.running) return;
    const parts = [];
    this.recorder = new MediaRecorder(this.stream, { mimeType: "audio/webm;codecs=opus" });
    this.recorder.ondataavailable = (e) => {
      if (e.data.size > 0) parts.push(e.data);
    };
    this.recorder.onstop = () => {
      if (!this.running) return; // stopped by the user: don't send the last half clip
      this.sendClip(new Blob(parts, { type: "audio/webm" }));
      this.recordClip(); // start the next clip straight away
    };
    this.recorder.start();
    setTimeout(() => {
      if (this.recorder.state !== "inactive") this.recorder.stop();
    }, this.CLIP_MS);
  },

  async sendClip(blob) {
    if (this.busy) return; // server still working on the last clip, skip this one
    this.busy = true;
    try {
      const result = await Api.Analysis.analyzeChunk(this.sessionId, blob, this.location);
      this.onResult(result);
    } catch (err) {
      this.onError(err);
    }
    this.busy = false;
  },

  // location is optional, alerts still work without it
  watchLocation() {
    if (!navigator.geolocation) return;
    this.watchId = navigator.geolocation.watchPosition(
      (pos) => {
        this.location.latitude = pos.coords.latitude;
        this.location.longitude = pos.coords.longitude;
      },
      () => {}
    );
  },
};
