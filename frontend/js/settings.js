// settings.js
// -----------
// Loads the current contacts + config from the core API when the page
// opens, lets the user edit them, and saves changes back. After any config
// save, we also call the analysis service's /reload-config so it picks up
// the new code words/sensitivity immediately instead of waiting for a
// restart.

let contactsCache = [];

// ---- Contacts ----

async function loadContacts() {
  contactsCache = await Api.Contacts.list();
  _renderContacts();
}

function _renderContacts() {
  const listEl = document.getElementById("contact-list");
  if (contactsCache.length === 0) {
    listEl.innerHTML = '<p class="text-slate-500">No contacts yet - add at least one below.</p>';
    return;
  }
  listEl.innerHTML = contactsCache
    .sort((a, b) => a.priorityOrder - b.priorityOrder)
    .map(
      (c) => `
      <div class="flex items-center gap-2 bg-slate-900 rounded-lg px-3 py-2">
        <input type="number" data-priority-id="${c.id}" value="${c.priorityOrder}" class="w-14 bg-slate-800 rounded px-2 py-1 text-xs" title="Priority (lower = notified first)" />
        <div class="flex-1">
          <p class="font-medium">${c.name}</p>
          <p class="text-xs text-slate-500">topic: ${c.ntfyTopic}</p>
        </div>
        <button data-delete-id="${c.id}" class="text-red-400 hover:text-red-300 text-xs">Remove</button>
      </div>
    `
    )
    .join("");

  listEl.querySelectorAll("[data-delete-id]").forEach((btn) => {
    btn.addEventListener("click", async () => {
      await Api.Contacts.remove(btn.dataset.deleteId);
      await loadContacts();
    });
  });

  listEl.querySelectorAll("[data-priority-id]").forEach((input) => {
    input.addEventListener("change", async () => {
      const contact = contactsCache.find((c) => String(c.id) === input.dataset.priorityId);
      if (!contact) return;
      contact.priorityOrder = Number(input.value);
      await Api.Contacts.update(contact.id, contact);
      await loadContacts();
    });
  });
}

document.getElementById("generate-topic-btn").addEventListener("click", () => {
  // A long random string, not a guessable name - this is effectively the "password"
  // that lets someone receive this contact's alerts (see NtfyService.java).
  const random = crypto.getRandomValues(new Uint8Array(12));
  const topic = "silent-signal-" + Array.from(random, (b) => b.toString(16).padStart(2, "0")).join("");
  document.getElementById("new-contact-topic").value = topic;
});

document.getElementById("add-contact-btn").addEventListener("click", async () => {
  const name = document.getElementById("new-contact-name").value.trim();
  const topic = document.getElementById("new-contact-topic").value.trim();
  if (!name || !topic) {
    alert("Please enter both a name and an ntfy topic.");
    return;
  }
  const nextPriority = contactsCache.length; // append to the end of the escalation order
  await Api.Contacts.create({ name, ntfyTopic: topic, priorityOrder: nextPriority });
  document.getElementById("new-contact-name").value = "";
  document.getElementById("new-contact-topic").value = "";
  await loadContacts();
});

// ---- Config (code words, sensitivity, disguise, duress, master switch) ----

async function loadConfig() {
  const config = await Api.Config.get();
  document.getElementById("code-words-input").value = config.codeWords || "";
  document.getElementById("cancel-word-input").value = config.cancelCodeWord || "";
  document.querySelector(`input[name="sensitivity"][value="${config.sensitivity}"]`)?.click();
  document.getElementById("disguise-enabled").checked = !!config.disguiseEnabled;
  document.getElementById("disguise-type").value = config.disguiseType || "calculator";
  document.getElementById("duress-pin-input").value = config.duressPin || "";
  document.getElementById("analysis-active-toggle").checked = config.analysisActive !== false;

  const baselineStatus = document.getElementById("baseline-status");
  baselineStatus.textContent =
    config.baselinePitchHz != null
      ? `Calibrated: ${config.baselinePitchHz.toFixed(1)} Hz baseline pitch.`
      : "Not calibrated yet.";
}

document.getElementById("save-settings-btn").addEventListener("click", async () => {
  const statusEl = document.getElementById("save-status");
  statusEl.textContent = "Saving...";
  try {
    const sensitivity = document.querySelector('input[name="sensitivity"]:checked')?.value || "MEDIUM";
    await Api.Config.update({
      codeWords: document.getElementById("code-words-input").value,
      cancelCodeWord: document.getElementById("cancel-word-input").value,
      sensitivity,
      disguiseEnabled: document.getElementById("disguise-enabled").checked,
      disguiseType: document.getElementById("disguise-type").value,
      duressPin: document.getElementById("duress-pin-input").value,
      analysisActive: document.getElementById("analysis-active-toggle").checked,
    });
    await Api.Analysis.reloadConfig();
    statusEl.textContent = `Saved at ${new Date().toLocaleTimeString()}.`;
  } catch (err) {
    statusEl.textContent = `Could not save: ${err.message}`;
  }
});

// ---- Calibration ----

document.getElementById("calibrate-btn").addEventListener("click", async () => {
  const progressEl = document.getElementById("calibrate-progress");
  const button = document.getElementById("calibrate-btn");
  button.disabled = true;

  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    const recorder = new MediaRecorder(stream, { mimeType: "audio/webm;codecs=opus" });
    const chunks = [];
    recorder.ondataavailable = (e) => e.data.size > 0 && chunks.push(e.data);

    const recordingDone = new Promise((resolve) => {
      recorder.onstop = resolve;
    });

    recorder.start();
    for (let secondsLeft = 10; secondsLeft > 0; secondsLeft--) {
      progressEl.textContent = `Recording... please talk normally (${secondsLeft}s left)`;
      await new Promise((r) => setTimeout(r, 1000));
    }
    recorder.stop();
    await recordingDone;
    stream.getTracks().forEach((t) => t.stop());

    progressEl.textContent = "Analyzing...";
    const blob = new Blob(chunks, { type: "audio/webm" });
    const result = await Api.Analysis.calibrate(blob);
    progressEl.textContent = result.message;
    await loadConfig();
  } catch (err) {
    progressEl.textContent = `Calibration failed: ${err.message}`;
  } finally {
    button.disabled = false;
  }
});

document.getElementById("demo-baseline-btn").addEventListener("click", async () => {
  const result = await Api.Analysis.calibrateDemo();
  document.getElementById("calibrate-progress").textContent = result.message;
  await loadConfig();
});

// ---- Init ----

loadContacts();
loadConfig();
