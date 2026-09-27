// The call page: a voice call using PeerJS (WebRTC), and voice analysis at the same time.
// PeerJS gives us a free server to connect the two browsers, so we don't need our own.
//
// Your call ID (e.g. SS-K7P3-9QDM-X2WA) is permanent and comes from our backend.
// PeerJS needs its own connection ID; we build it from the call ID, so the
// person calling you only ever needs your call ID.

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

const CALL_ID_FORMAT = /^SS-[2-9A-Z]{4}-[2-9A-Z]{4}-[2-9A-Z]{4}$/;

// "SS-K7P3-9QDM-X2WA" -> "silentsignal-ss-k7p3-9qdm-x2wa" (the PeerJS connection ID)
function peerIdFor(callId) {
  return "silentsignal-" + callId.toLowerCase();
}

// accept " ss-k7p3-9qdm-x2wa " as well
function cleanCallId(text) {
  return text.trim().toUpperCase().replace(/\s+/g, "");
}

let myCallId = null;
let idRetries = 0;

async function connectPeer() {
  try {
    myCallId = (await Api.Me.get()).callId;
  } catch (err) {
    el("call-status").textContent = "Can't load your call ID. Is the core API running?";
    return;
  }
  el("my-peer-id").value = myCallId;
  peer = new Peer(peerIdFor(myCallId));
  peer.on("open", () => {
    idRetries = 0;
    if (!currentCall) el("call-status").textContent = "Not connected";
  });
  peer.on("error", (err) => {
    if (err.type === "unavailable-id" && idRetries < 5) {
      // after a page reload PeerJS can hold on to our ID for a few seconds: wait and retry
      idRetries++;
      el("call-status").textContent = "Getting ready...";
      peer.destroy();
      setTimeout(connectPeer, 3000);
      return;
    }
    const messages = {
      "unavailable-id": "Your call ID is already open in another tab or window. Close it and reload this page.",
      "peer-unavailable": "That person isn't online. Their call page must be open.",
      network: "Can't reach the call server. Check your internet.",
    };
    el("call-status").textContent = messages[err.type] || "Error: " + err.type;
  });
  peer.on("disconnected", () => {
    if (!peer.destroyed) peer.reconnect(); // Wi-Fi blip: keep being reachable
  });
  peer.on("call", onIncomingCall);
}
connectPeer();

// someone is calling us
async function onIncomingCall(call) {
  call.answer(await getMic());
  setupCall(call);
}

el("call-btn").onclick = async () => {
  const id = cleanCallId(el("remote-peer-id").value);
  if (!id || !peer) return;
  if (!CALL_ID_FORMAT.test(id)) {
    el("call-status").textContent = "That doesn't look like a call ID (like SS-K7P3-9QDM-X2WA).";
    return;
  }
  if (id === myCallId) {
    el("call-status").textContent = "That's your own call ID.";
    return;
  }
  el("call-status").textContent = "Calling...";
  setupCall(peer.call(peerIdFor(id), await getMic()));
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
