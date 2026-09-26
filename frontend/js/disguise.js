// disguise.js
// -----------
// Powers disguise.html: a real, working calculator or notes app, with one
// hidden behavior - entering the configured duress PIN shows a fake, empty
// dashboard instead of doing the normal thing.

const calculatorView = document.getElementById("calculator-view");
const notesView = document.getElementById("notes-view");
const decoyDashboardView = document.getElementById("decoy-dashboard-view");

let duressPin = "0000";
let activeDecoyType = "calculator"; // which screen to return to after the decoy dashboard

async function init() {
  try {
    const config = await Api.Config.get();
    duressPin = config.duressPin || "0000";
    if (config.disguiseType === "notes") {
      activeDecoyType = "notes";
      calculatorView.classList.add("hidden");
      notesView.classList.remove("hidden");
    }
  } catch (err) {
    // If the core API is unreachable, still show a convincing calculator -
    // a decoy screen that visibly fails to load is not much of a decoy.
    console.warn("[disguise] could not load config, defaulting to calculator:", err);
  }
}

function _showDecoyDashboard() {
  calculatorView.classList.add("hidden");
  notesView.classList.add("hidden");
  decoyDashboardView.classList.remove("hidden");
}

function _returnToDecoyScreen() {
  decoyDashboardView.classList.add("hidden");
  if (activeDecoyType === "notes") {
    notesView.classList.remove("hidden");
  } else {
    calculatorView.classList.remove("hidden");
  }
}

// Tapping the decoy dashboard's header 3 times lets the real owner get back
// to the calculator/notes screen without it being an obvious "exit" button.
let tapCount = 0;
let tapResetTimer = null;
document.getElementById("decoy-header").addEventListener("click", () => {
  tapCount += 1;
  clearTimeout(tapResetTimer);
  tapResetTimer = setTimeout(() => (tapCount = 0), 1500);
  if (tapCount >= 3) {
    tapCount = 0;
    _returnToDecoyScreen();
  }
});

// ---- Calculator ----

const CALC_BUTTONS = [
  "C", "±", "%", "÷",
  "7", "8", "9", "×",
  "4", "5", "6", "−",
  "1", "2", "3", "+",
  "0", ".", "=",
];

let displayValue = "0";
let firstOperand = null;
let pendingOperator = null;
let awaitingSecondOperand = false;

const displayEl = document.getElementById("calc-display");
const buttonsEl = document.getElementById("calc-buttons");

CALC_BUTTONS.forEach((label) => {
  const btn = document.createElement("button");
  btn.textContent = label;
  btn.className =
    "h-14 rounded-xl text-xl font-medium " +
    (label === "="
      ? "bg-amber-500 hover:bg-amber-400 col-span-1"
      : "=+−×÷".includes(label)
      ? "bg-slate-700 hover:bg-slate-600"
      : "bg-slate-800 hover:bg-slate-700");
  if (label === "0") btn.classList.add("col-span-2");
  btn.addEventListener("click", () => _handleCalcButton(label));
  buttonsEl.appendChild(btn);
});

function _handleCalcButton(label) {
  if (/\d/.test(label)) {
    _inputDigit(label);
  } else if (label === ".") {
    _inputDecimal();
  } else if (label === "C") {
    _clear();
  } else if (label === "±") {
    displayValue = String(parseFloat(displayValue) * -1);
  } else if (label === "%") {
    displayValue = String(parseFloat(displayValue) / 100);
  } else if (label === "=") {
    _equals();
    return; // _equals already re-renders (or redirects to the decoy dashboard)
  } else {
    _inputOperator(label);
  }
  _render();
}

function _inputDigit(digit) {
  if (awaitingSecondOperand) {
    displayValue = digit;
    awaitingSecondOperand = false;
  } else {
    displayValue = displayValue === "0" ? digit : displayValue + digit;
  }
}

function _inputDecimal() {
  if (awaitingSecondOperand) {
    displayValue = "0.";
    awaitingSecondOperand = false;
    return;
  }
  if (!displayValue.includes(".")) displayValue += ".";
}

function _clear() {
  displayValue = "0";
  firstOperand = null;
  pendingOperator = null;
  awaitingSecondOperand = false;
}

function _inputOperator(operator) {
  const inputValue = parseFloat(displayValue);
  if (pendingOperator && awaitingSecondOperand) {
    pendingOperator = operator;
    return;
  }
  if (firstOperand === null) {
    firstOperand = inputValue;
  } else if (pendingOperator) {
    firstOperand = _compute(firstOperand, inputValue, pendingOperator);
    displayValue = String(firstOperand);
  }
  pendingOperator = operator;
  awaitingSecondOperand = true;
}

function _compute(a, b, operator) {
  switch (operator) {
    case "+": return a + b;
    case "−": return a - b;
    case "×": return a * b;
    case "÷": return b === 0 ? 0 : a / b;
    default: return b;
  }
}

function _equals() {
  // The hidden trigger: if what's on the display right now is the duress
  // PIN, don't calculate anything - show the decoy dashboard instead.
  if (displayValue.trim() === duressPin.trim()) {
    _showDecoyDashboard();
    return;
  }
  if (pendingOperator && firstOperand !== null) {
    displayValue = String(_compute(firstOperand, parseFloat(displayValue), pendingOperator));
    firstOperand = null;
    pendingOperator = null;
    awaitingSecondOperand = false;
  }
  _render();
}

function _render() {
  displayEl.textContent = displayValue;
}

// ---- Notes ----

const NOTES_STORAGE_KEY = "silent-signal-decoy-notes";
const notesTextarea = document.getElementById("notes-textarea");
notesTextarea.value = localStorage.getItem(NOTES_STORAGE_KEY) || "";

notesTextarea.addEventListener("input", () => {
  localStorage.setItem(NOTES_STORAGE_KEY, notesTextarea.value);
  // The hidden trigger for the notes decoy: type the duress PIN as the
  // ENTIRE contents of the note.
  if (notesTextarea.value.trim() === duressPin.trim()) {
    _showDecoyDashboard();
  }
});

init();
