// Settings page: contacts, code words, sensitivity, calibration, disguise.

const el = (id) => document.getElementById(id);
let contacts = [];

// ---- Contacts ----

async function loadContacts() {
  contacts = await Api.Contacts.list();
  if (contacts.length === 0) {
    el("contact-list").innerHTML = '<tr><td class="muted">No contacts yet. Add at least one.</td></tr>';
    return;
  }
  let html = "<tr><th>Order</th><th>Name</th><th>Topic</th><th></th></tr>";
  contacts.forEach((c, i) => {
    html += "<tr><td>" + (i + 1) + "</td>";
    html += "<td>" + escapeHtml(c.name) + "</td>";
    html += '<td class="muted">' + escapeHtml(c.ntfyTopic) + "</td>";
    html += '<td><button data-up="' + i + '">Up</button> ';
    html += '<button data-delete="' + c.id + '">Remove</button></td></tr>';
  });
  el("contact-list").innerHTML = html;
}

// one click handler for all the Up / Remove buttons in the table
el("contact-list").onclick = (e) => {
  if (e.target.dataset.up) moveContactUp(Number(e.target.dataset.up));
  if (e.target.dataset.delete) deleteContact(e.target.dataset.delete);
};

async function deleteContact(id) {
  await Api.Contacts.remove(id);
  loadContacts();
}

// swap a contact with the one above it
async function moveContactUp(i) {
  if (i === 0) return;
  const a = contacts[i], b = contacts[i - 1];
  await Api.Contacts.update(a.id, { ...a, priorityOrder: i - 1 });
  await Api.Contacts.update(b.id, { ...b, priorityOrder: i });
  loadContacts();
}

el("generate-topic-btn").onclick = () => {
  // random topic, hard to guess
  const bytes = crypto.getRandomValues(new Uint8Array(12));
  el("new-contact-topic").value = "silent-signal-" + Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
};

el("add-contact-btn").onclick = async () => {
  const name = el("new-contact-name").value.trim();
  const topic = el("new-contact-topic").value.trim();
  if (!name || !topic) {
    alert("Please fill in the name and the topic.");
    return;
  }
  // new contacts go to the end of the list
  const lastOrder = contacts.length ? contacts[contacts.length - 1].priorityOrder : -1;
  try {
    await Api.Contacts.create({ name: name, ntfyTopic: topic, priorityOrder: lastOrder + 1 });
    el("new-contact-name").value = "";
    el("new-contact-topic").value = "";
    el("contact-status").textContent = "";
    loadContacts();
  } catch (err) {
    el("contact-status").textContent = "Could not add: " + err.message;
  }
};

// ---- Settings ----

async function loadConfig() {
  const c = await Api.Config.get();
  el("code-words-input").value = c.codeWords || "";
  el("cancel-word-input").value = c.cancelCodeWord || "";
  document.querySelector('input[name="sensitivity"][value="' + c.sensitivity + '"]').checked = true;
  el("disguise-enabled").checked = c.disguiseEnabled;
  el("disguise-type").value = c.disguiseType || "calculator";
  el("duress-pin-input").value = ""; // we never get the PIN back, only whether one is set
  el("pin-status").textContent = c.duressPinSet ? "A PIN is set. Leave the box empty to keep it." : "No PIN set yet.";
  el("analysis-active-toggle").checked = c.analysisActive;
  el("share-location-toggle").checked = c.shareLocation;
  el("baseline-status").textContent = c.baselinePitchHz
    ? "Calibrated. Your normal pitch is " + c.baselinePitchHz.toFixed(0) + " Hz."
    : "Not calibrated yet.";
}

el("save-settings-btn").onclick = async () => {
  el("save-status").textContent = "Saving...";
  try {
    await Api.Config.update({
      codeWords: el("code-words-input").value,
      cancelCodeWord: el("cancel-word-input").value,
      sensitivity: document.querySelector('input[name="sensitivity"]:checked').value,
      disguiseEnabled: el("disguise-enabled").checked,
      disguiseType: el("disguise-type").value,
      duressPin: el("duress-pin-input").value,
      analysisActive: el("analysis-active-toggle").checked,
      shareLocation: el("share-location-toggle").checked,
    });
    loadConfig();
    // tell the analysis service to pick up the new code words etc.
    await Api.Analysis.reloadConfig();
    el("save-status").textContent = "Saved.";
  } catch (err) {
    el("save-status").textContent = "Could not save: " + err.message;
  }
};

// ---- Calibration: record 10 seconds of normal talking ----

el("calibrate-btn").onclick = async () => {
  const button = el("calibrate-btn");
  const progress = el("calibrate-progress");
  button.disabled = true;
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    const recorder = new MediaRecorder(stream, { mimeType: "audio/webm;codecs=opus" });
    const parts = [];
    recorder.ondataavailable = (e) => parts.push(e.data);
    const stopped = new Promise((resolve) => (recorder.onstop = resolve));

    recorder.start();
    for (let s = 10; s > 0; s--) {
      progress.textContent = "Recording... talk normally (" + s + ")";
      await new Promise((r) => setTimeout(r, 1000));
    }
    recorder.stop();
    await stopped;
    stream.getTracks().forEach((t) => t.stop());

    progress.textContent = "Checking...";
    const result = await Api.Analysis.calibrate(new Blob(parts, { type: "audio/webm" }));
    progress.textContent = result.message;
    loadConfig();
  } catch (err) {
    progress.textContent = "Calibration failed: " + err.message;
  }
  button.disabled = false;
};

el("demo-baseline-btn").onclick = async () => {
  try {
    const result = await Api.Analysis.calibrateDemo();
    el("calibrate-progress").textContent = result.message;
    loadConfig();
  } catch (err) {
    el("calibrate-progress").textContent = "Failed: " + err.message;
  }
};

// ---- Your data (DPDP rights) ----

el("export-btn").onclick = async () => {
  try {
    const data = await request(Api.MyData.exportUrl);
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = "silent-signal-my-data.json";
    link.click();
  } catch (err) {
    el("data-status").textContent = "Could not export: " + err.message;
  }
};

el("withdraw-btn").onclick = async () => {
  if (!confirm("Withdraw consent? Voice analysis will stop until you agree again.")) return;
  await Api.Config.consent(false);
  await Api.Analysis.reloadConfig().catch(() => {});
  location.href = "consent.html";
};

el("delete-all-btn").onclick = async () => {
  if (!confirm("Delete ALL contacts, settings and alerts? This can't be undone.")) return;
  try {
    await Api.MyData.deleteAll();
    await Api.Analysis.reloadConfig().catch(() => {});
    location.href = "consent.html";
  } catch (err) {
    el("data-status").textContent = "Could not delete: " + err.message;
  }
};

loadContacts().catch((err) => (el("contact-list").innerHTML = "<tr><td>Could not load contacts (is the core API running?)</td></tr>"));
loadConfig().catch(() => {});
