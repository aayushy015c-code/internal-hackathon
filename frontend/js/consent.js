// Privacy notice page. Nothing is analyzed until the user agrees here.
const agree = document.getElementById("agree");
const acceptBtn = document.getElementById("accept-btn");
const status = document.getElementById("consent-status");

Api.Config.get()
  .then((c) => { if (c.consentGiven) status.textContent = "You already agreed on " + new Date(c.consentAt).toLocaleString() + "."; })
  .catch(() => (status.textContent = "Can't reach the core API. Is it running?"));

agree.onchange = () => (acceptBtn.disabled = !agree.checked);

acceptBtn.onclick = async () => {
  try {
    await Api.Config.consent(true);
    await Api.Analysis.reloadConfig();
    location.href = "settings.html";
  } catch (err) {
    status.textContent = "Could not save: " + err.message;
  }
};
