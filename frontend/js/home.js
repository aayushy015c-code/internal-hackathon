// Home page.

// needed so Chrome lets you install the site as an app
if ("serviceWorker" in navigator) {
  navigator.serviceWorker.register("service-worker.js");
}

// If disguise mode is on, the home page opens the fake calculator/notes app instead.
Api.Disguise.info()
  .then((d) => { if (d.enabled) location.replace("disguise.html"); })
  .catch(() => {});

// "Install as an app" button (Chrome shows this event when the app can be installed)
let installPrompt = null;
const installBtn = document.getElementById("install-btn");
window.addEventListener("beforeinstallprompt", (e) => {
  e.preventDefault();
  installPrompt = e;
  installBtn.classList.remove("hidden");
});
installBtn.onclick = () => {
  installPrompt.prompt();
  installBtn.classList.add("hidden");
};
