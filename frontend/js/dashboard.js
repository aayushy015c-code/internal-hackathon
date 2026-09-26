// Dashboard: live scores from the call tab + alert history from the core API.

const el = (id) => document.getElementById(id);
const liveChannel = new BroadcastChannel("silent-signal-live");
let lastMessageTime = 0;

// a new result from the call tab (every ~4 seconds)
liveChannel.onmessage = (event) => {
  lastMessageTime = Date.now();
  el("no-live-banner").classList.add("hidden");
  showResult(event.data);
};

// no results for 10 seconds = no call running
setInterval(() => {
  if (Date.now() - lastMessageTime > 10000) el("no-live-banner").classList.remove("hidden");
}, 2000);

function setBar(id, percent) {
  el(id).style.width = Math.max(0, Math.min(100, percent)) + "%";
}

function showResult(r) {
  setBar("rolling-bar", r.rolling_score);
  el("rolling-bar").style.background = r.rolling_score >= 65 ? "#c0392b" : r.rolling_score >= 40 ? "#e67e22" : "#27ae60";
  el("rolling-value").textContent = r.rolling_score;
  el("last-updated").textContent = "(updated " + new Date().toLocaleTimeString() + ")";

  if (r.alert_triggered) {
    el("trigger-banner").classList.remove("hidden");
    el("trigger-banner").textContent = "Alert sent (" + r.trigger_path + ") at " + new Date().toLocaleTimeString();
  }

  const s = r.signals;
  setBar("pitch-bar", s.pitch.delta_pct);
  el("pitch-value").textContent = s.pitch.value_hz + " Hz (" + s.pitch.delta_pct + "% vs normal)";
  setBar("energy-bar", s.energy.delta_pct);
  el("energy-value").textContent = s.energy.delta_pct + "% vs normal";
  setBar("silence-bar", s.silence.ratio * 100);
  el("silence-value").textContent = Math.round(s.silence.ratio * 100) + "% of the clip";
  el("codeword-value").textContent = s.codeword.matched ? 'heard "' + s.codeword.phrase + '"' : "not heard";
}

// ---- Alert history ----

async function loadAlerts() {
  try {
    const alerts = await Api.Alerts.list();
    if (alerts.length === 0) {
      el("alert-list").innerHTML = '<p class="muted">No alerts yet.</p>';
      return;
    }
    let html = "<table><tr><th>Time</th><th>Why</th><th>Status</th></tr>";
    for (const a of alerts) {
      html += "<tr>";
      html += "<td>" + new Date(a.createdAt).toLocaleString() + "</td>";
      html += "<td>" + escapeHtml(a.reasons);
      if (a.transcriptSnippet) html += '<br><span class="muted">"...' + escapeHtml(a.transcriptSnippet) + '"</span>';
      html += "</td>";
      html += '<td class="status-' + a.status + '">' + a.status;
      if (a.status === "PENDING") html += '<br><button onclick="cancelAlert(' + a.id + ', this)">False alarm</button>';
      html += "</td></tr>";
    }
    el("alert-list").innerHTML = html + "</table>";
  } catch (err) {
    el("alert-list").textContent = "Could not load alerts (is the core API running?) " + err.message;
  }
}

async function cancelAlert(id, button) {
  button.disabled = true;
  try {
    await Api.Alerts.cancel(id);
  } catch (err) {
    alert("Could not cancel: " + err.message);
  }
  loadAlerts();
}

el("test-alert-btn").onclick = async () => {
  el("test-alert-btn").disabled = true;
  try {
    await Api.Alerts.sendTestAlert();
  } catch (err) {
    alert("Could not send test alert: " + err.message);
  }
  el("test-alert-btn").disabled = false;
  loadAlerts();
};

loadAlerts();
setInterval(loadAlerts, 5000);
