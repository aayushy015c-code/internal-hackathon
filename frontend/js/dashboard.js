// dashboard.js
// ------------
// Two independent data sources feed this page:
//
// 1. A BroadcastChannel ("silent-signal-live") - the /call tab posts every
//    /analyze result onto this channel the instant it gets one back. This
//    is what makes the risk meter feel "live" without any polling.
// 2. A periodic poll of GET /api/alerts on the core API, which is the
//    permanent alert history (and where cancelling a false alarm happens).

const liveChannel = new BroadcastChannel("silent-signal-live");
const noLiveBanner = document.getElementById("no-live-banner");

let lastLiveMessageAt = 0;

liveChannel.onmessage = (event) => {
  lastLiveMessageAt = Date.now();
  noLiveBanner.classList.add("hidden");
  _renderLiveResult(event.data);
};

// If nothing has arrived in a while, assume there's no live call and say so.
setInterval(() => {
  if (lastLiveMessageAt && Date.now() - lastLiveMessageAt > 8000) {
    noLiveBanner.classList.remove("hidden");
    lastLiveMessageAt = 0;
  }
}, 2000);

function _renderLiveResult(result) {
  const rollingBar = document.getElementById("rolling-bar");
  const rollingValue = document.getElementById("rolling-value");
  const lastUpdated = document.getElementById("last-updated");
  const triggerBanner = document.getElementById("trigger-banner");

  const rolling = Math.max(0, Math.min(100, result.rolling_score));
  rollingBar.style.width = `${rolling}%`;
  rollingBar.className =
    "h-full transition-all duration-300 " + (rolling >= 65 ? "bg-red-500" : rolling >= 40 ? "bg-amber-400" : "bg-emerald-500");
  rollingValue.textContent = String(result.rolling_score);
  lastUpdated.textContent = `updated ${new Date().toLocaleTimeString()}`;

  if (result.alert_triggered) {
    triggerBanner.classList.remove("hidden");
    triggerBanner.textContent = `ALERT sent (${result.trigger_path}) at ${new Date().toLocaleTimeString()}`;
  }

  const signals = result.signals || {};
  if (signals.pitch) {
    document.getElementById("pitch-bar").style.width = `${Math.min(100, Math.abs(signals.pitch.delta_pct))}%`;
    document.getElementById("pitch-value").textContent = `${signals.pitch.value_hz} Hz (${signals.pitch.delta_pct}% vs baseline)`;
  }
  if (signals.energy) {
    document.getElementById("energy-bar").style.width = `${Math.min(100, Math.abs(signals.energy.delta_pct))}%`;
    document.getElementById("energy-value").textContent = `${signals.energy.delta_pct}% vs baseline`;
  }
  if (signals.silence) {
    document.getElementById("silence-bar").style.width = `${Math.min(100, signals.silence.ratio * 100)}%`;
    document.getElementById("silence-value").textContent = `${Math.round(signals.silence.ratio * 100)}% of chunk`;
  }
  if (signals.codeword) {
    document.getElementById("codeword-value").textContent = signals.codeword.matched
      ? `MATCHED "${signals.codeword.phrase}" (${signals.codeword.confidence}%)`
      : "not matched";
  }
}

// ---- Alert history (core API) ----

async function refreshAlertHistory() {
  const listEl = document.getElementById("alert-list");
  try {
    const alerts = await Api.Alerts.list();
    if (alerts.length === 0) {
      listEl.innerHTML = '<p class="text-slate-500">No alerts yet.</p>';
      return;
    }
    listEl.innerHTML = alerts.map(_renderAlertRow).join("");
    listEl.querySelectorAll("[data-cancel-id]").forEach((btn) => {
      btn.addEventListener("click", async () => {
        btn.disabled = true;
        btn.textContent = "Cancelling...";
        try {
          await Api.Alerts.cancel(btn.dataset.cancelId);
          await refreshAlertHistory();
        } catch (err) {
          alert(`Could not cancel: ${err.message}`);
          btn.disabled = false;
          btn.textContent = "Mark false alarm";
        }
      });
    });
  } catch (err) {
    listEl.innerHTML = `<p class="text-red-400">Could not load alerts: ${err.message}</p>`;
  }
}

function _renderAlertRow(alert) {
  const statusColor =
    alert.status === "ACKNOWLEDGED" ? "text-emerald-400" : alert.status === "CANCELLED" ? "text-slate-500" : "text-red-400";
  const time = new Date(alert.createdAt).toLocaleString();
  const cancelButton =
    alert.status === "PENDING"
      ? `<button data-cancel-id="${alert.id}" class="text-xs bg-slate-700 hover:bg-slate-600 rounded px-2 py-1">Mark false alarm</button>`
      : "";

  return `
    <div class="border border-slate-700 rounded-lg px-3 py-2">
      <div class="flex justify-between items-start gap-3">
        <div>
          <p class="font-medium">${alert.triggerPath} <span class="${statusColor} text-xs">(${alert.status})</span></p>
          <p class="text-xs text-slate-400">${time}</p>
          <p class="text-xs text-slate-300 mt-1">${alert.reasons || ""}</p>
          ${alert.transcriptSnippet ? `<p class="text-xs text-slate-500 mt-1">"...${alert.transcriptSnippet}"</p>` : ""}
        </div>
        ${cancelButton}
      </div>
    </div>
  `;
}

document.getElementById("test-alert-btn").addEventListener("click", async (event) => {
  const btn = event.currentTarget;
  btn.disabled = true;
  btn.textContent = "Sending...";
  try {
    await Api.Alerts.sendTestAlert();
    await refreshAlertHistory();
  } catch (err) {
    alert(`Could not send test alert: ${err.message}`);
  } finally {
    btn.disabled = false;
    btn.textContent = "Send test alert";
  }
});

refreshAlertHistory();
setInterval(refreshAlertHistory, 5000);
