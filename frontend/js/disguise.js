// The fake app: a calculator (or notes) that really works.
// If someone types the duress PIN, we show a fake empty dashboard.

const el = (id) => document.getElementById(id);
let duressPin = "0000";
let fakeApp = "calculator";

Api.Config.get()
  .then((config) => {
    duressPin = config.duressPin || "0000";
    if (config.disguiseType === "notes") {
      fakeApp = "notes";
      showOnly("notes-view");
    }
  })
  .catch(() => {}); // server down? still show the calculator, it must look normal

function showOnly(id) {
  ["calculator-view", "notes-view", "decoy-dashboard-view"].forEach((v) => el(v).classList.add("hidden"));
  el(id).classList.remove("hidden");
}

function showFakeDashboard() {
  document.body.classList.remove("disguise-page");
  showOnly("decoy-dashboard-view");
}

// tap the title 3 times to go back to the calculator/notes
let taps = 0;
el("decoy-header").onclick = () => {
  taps++;
  setTimeout(() => (taps = 0), 1500);
  if (taps >= 3) {
    taps = 0;
    document.body.classList.add("disguise-page");
    showOnly(fakeApp === "notes" ? "notes-view" : "calculator-view");
  }
};

// ---- Calculator ----

const buttons = ["C", "±", "%", "÷", "7", "8", "9", "×", "4", "5", "6", "−", "1", "2", "3", "+", "0", ".", "="];
let display = "0";
let first = null;    // first number
let operator = null; // + − × ÷
let newNumber = false;

buttons.forEach((label) => {
  const b = document.createElement("button");
  b.textContent = label;
  if ("÷×−+=".includes(label)) b.className = "op";
  if (label === "0") b.className = "wide";
  b.onclick = () => press(label);
  el("calc-buttons").appendChild(b);
});

function calculate(a, b, op) {
  if (op === "+") return a + b;
  if (op === "−") return a - b;
  if (op === "×") return a * b;
  if (op === "÷") return b === 0 ? 0 : a / b;
  return b;
}

function press(label) {
  if (/[0-9]/.test(label)) {
    display = newNumber || display === "0" ? label : display + label;
    newNumber = false;
  } else if (label === ".") {
    if (newNumber) { display = "0"; newNumber = false; }
    if (!display.includes(".")) display += ".";
  } else if (label === "C") {
    display = "0"; first = null; operator = null; newNumber = false;
  } else if (label === "±") {
    display = String(-parseFloat(display));
  } else if (label === "%") {
    display = String(parseFloat(display) / 100);
  } else if (label === "=") {
    // the secret: "=" on the duress PIN opens the fake dashboard
    if (display === duressPin) {
      display = "0";
      el("calc-display").textContent = display;
      showFakeDashboard();
      return;
    }
    if (operator !== null) {
      display = String(calculate(first, parseFloat(display), operator));
      first = null; operator = null; newNumber = true;
    }
  } else {
    // + − × ÷
    if (operator !== null && !newNumber) {
      display = String(calculate(first, parseFloat(display), operator));
    }
    first = parseFloat(display);
    operator = label;
    newNumber = true;
  }
  el("calc-display").textContent = display;
}

// ---- Notes ----

const notes = el("notes-textarea");
notes.value = localStorage.getItem("decoy-notes") || "";
notes.oninput = () => {
  // the secret: a note that is exactly the duress PIN opens the fake dashboard
  if (notes.value.trim() === duressPin) {
    notes.value = ""; // don't leave the PIN sitting in the notes
    showFakeDashboard();
  }
  localStorage.setItem("decoy-notes", notes.value);
};
