// The call page: a voice call using PeerJS (WebRTC), and voice analysis at the same time.
// PeerJS gives us a free server to connect the two browsers, so we don't need our own.

let peer = null;
let localStream = null; // our microphone, shared by the call and the recorder
let currentCall = null;
let timer = null;

// Sends each result to the dashboard tab (browser tab-to-tab messaging, no server needed)
const liveChannel = new BroadcastChannel("silent-signal-live");

const el = (id) => document.getElementById(id);

async function getMic() {
  if (!localStream) {
    localStream = await navigator.mediaDevices.getUserMedia({ audio: true });
  }
  return localStream;
}

// turn the microphone off when nothing needs it
function releaseMicIfUnused() {
  if (!currentCall && !Recorder.running && localStream) {
    localStream.getTracks().forEach((t) => t.stop());
    localStream = null;
  }
}

// ---- Call ----

peer = new Peer();
peer.on("open", (id) => (el("my-peer-id").value = id));
peer.on("error", (err) => (el("call-status").textContent = "Error: " + err.type));

// someone is calling us
peer.on("call", async (call) => {
  call.answer(await getMic());
  setupCall(call);
});

el("call-btn").onclick = async () => {
  const id = el("remote-peer-id").value.trim();
  if (!id) return;
  el("call-status").textContent = "Calling...";
  setupCall(peer.call(id, await getMic()));
};

function setupCall(call) {
  currentCall = call;
  call.on("stream", (remoteStream) => {
    if (timer) return; // "stream" can fire twice, only set up once
    el("remote-audio").srcObject = remoteStream;
    showInCall();
    startAnalysis(); // start listening as soon as the call connects
  });
  call.on("close", endCall);
  call.on("error", endCall);
}

el("hangup-btn").onclick = () => {
  if (currentCall) currentCall.close();
  endCall();
};

function showInCall() {
  el("setup-panel").classList.add("hidden");
  el("in-call-panel").classList.remove("hidden");
  el("call-timer").classList.remove("hidden");
  el("call-status").textContent = "Connected";
  const start = Date.now();
  timer = setInterval(() => {
    const s = Math.floor((Date.now() - start) / 1000);
    el("call-timer").textContent = String(Math.floor(s / 60)).padStart(2, "0") + ":" + String(s % 60).padStart(2, "0");
  }, 1000);
}

function endCall() {
  clearInterval(timer);
  timer = null;
  currentCall = null;
  el("setup-panel").classList.remove("hidden");
  el("in-call-panel").classList.add("hidden");
  el("call-timer").classList.add("hidden");
  el("call-timer").textContent = "00:00";
  el("call-status").textContent = "Not connected";
  stopAnalysis();
  releaseMicIfUnused();
}

el("mute-btn").onclick = () => {
  const track = localStream && localStream.getAudioTracks()[0];
  if (!track) return;
  track.enabled = !track.enabled;
  el("mute-btn").textContent = track.enabled ? "Mute" : "Unmute";
};

el("copy-id-btn").onclick = () => navigator.clipboard.writeText(el("my-peer-id").value);

// ---- Voice analysis ----

async function startAnalysis() {
  let shareLocation = false;
  try {
    shareLocation = (await Api.Config.get()).shareLocation;
  } catch (err) {}
  Recorder.start(
    await getMic(),
    (result) => {
      el("analysis-error").textContent = "";
      // Nothing is shown here on purpose, even for alerts. Someone looking at
      // the phone should only see a normal call. Results go to the dashboard.
      liveChannel.postMessage(result);
    },
    (err) => (el("analysis-error").textContent = "Analysis problem: " + err.message),
    shareLocation
  );
  el("analysis-dot").classList.add("on");
  el("analysis-label").textContent = "Voice analysis: on";
  el("analysis-toggle-btn").textContent = "Stop";
}

function stopAnalysis() {
  Recorder.stop();
  el("analysis-dot").classList.remove("on");
  el("analysis-label").textContent = "Voice analysis: off";
  el("analysis-toggle-btn").textContent = "Start";
}

el("analysis-toggle-btn").onclick = () => {
  if (Recorder.running) {
    stopAnalysis();
    releaseMicIfUnused();
  } else {
    startAnalysis();
  }
};
