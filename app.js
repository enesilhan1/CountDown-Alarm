// Aşama 1: yalnızca arayüz. Alarmlar bellekte tutulur, ses/bildirim sonraki aşamada.

const $ = (id) => document.getElementById(id);
const hoursEl = $("hours");
const minutesEl = $("minutes");
const labelEl = $("label");
const createBtn = $("createBtn");
const DIAL_LEN = 2 * Math.PI * 88;
const DIAL_MAX_MIN = 12 * 60; // kadran 12 saatte dolar

const alarms = [];

const pad = (n) => String(n).padStart(2, "0");
const fmtClock = (d) => `${pad(d.getHours())}:${pad(d.getMinutes())}`;

function fmtRemaining(ms) {
  const s = Math.max(0, Math.ceil(ms / 1000));
  return `${pad(Math.floor(s / 3600))}:${pad(Math.floor((s % 3600) / 60))}:${pad(s % 60)}`;
}

function clamp(el) {
  const max = Number(el.max);
  let v = parseInt(el.value, 10);
  if (isNaN(v) || v < 0) v = 0;
  if (v > max) v = max;
  el.value = v;
  return v;
}

function totalMinutes() {
  return clamp(hoursEl) * 60 + clamp(minutesEl);
}

function updateSetup() {
  const total = totalMinutes();
  $("dispH").textContent = Math.floor(total / 60);
  $("dispM").textContent = pad(total % 60);
  $("ringAt").textContent = total ? fmtClock(new Date(Date.now() + total * 60000)) : "--:--";
  $("dialFill").style.strokeDashoffset = DIAL_LEN * (1 - Math.min(total, DIAL_MAX_MIN) / DIAL_MAX_MIN);
  createBtn.disabled = total === 0;
  document.querySelectorAll(".chip").forEach((c) =>
    c.classList.toggle("active", Number(c.dataset.min) === total)
  );
}

function setTotal(min) {
  hoursEl.value = Math.floor(min / 60);
  minutesEl.value = min % 60;
  updateSetup();
}

document.querySelectorAll(".step").forEach((btn) =>
  btn.addEventListener("click", () => {
    const delta = Number(btn.dataset.delta);
    const total = totalMinutes() + (btn.dataset.target === "hours" ? delta * 60 : delta);
    setTotal(Math.max(0, Math.min(total, 99 * 60 + 59)));
  })
);

document.querySelectorAll(".chip").forEach((chip) =>
  chip.addEventListener("click", () => setTotal(Number(chip.dataset.min)))
);

[hoursEl, minutesEl].forEach((el) => el.addEventListener("input", updateSetup));

createBtn.addEventListener("click", () => {
  const total = totalMinutes();
  if (!total) return;
  const now = Date.now();
  alarms.push({
    id: crypto.randomUUID(),
    label: labelEl.value.trim() || "Alarm",
    start: now,
    end: now + total * 60000,
  });
  alarms.sort((a, b) => a.end - b.end);
  labelEl.value = "";
  setTotal(0);
  renderList();
});

function renderList() {
  const ul = $("alarms");
  const tpl = $("alarmTpl");
  ul.innerHTML = "";
  for (const a of alarms) {
    const li = tpl.content.firstElementChild.cloneNode(true);
    li.dataset.id = a.id;
    li.querySelector(".alarm-label").textContent = a.label;
    li.querySelector(".alarm-at").textContent = fmtClock(new Date(a.end));
    li.querySelector(".delete").addEventListener("click", () => {
      alarms.splice(alarms.findIndex((x) => x.id === a.id), 1);
      renderList();
    });
    ul.appendChild(li);
  }
  $("count").textContent = alarms.length;
  $("empty").hidden = alarms.length > 0;
  tick();
}

function tick() {
  const now = Date.now();
  $("now").textContent = fmtClock(new Date(now));
  for (const a of alarms) {
    const li = document.querySelector(`.alarm[data-id="${a.id}"]`);
    if (!li) continue;
    const left = a.end - now;
    li.querySelector(".alarm-remaining").textContent = left > 0 ? fmtRemaining(left) : "Çalıyor!";
    li.querySelector(".progress-bar").style.width =
      Math.min(100, ((now - a.start) / (a.end - a.start)) * 100) + "%";
    li.classList.toggle("done", left <= 0);
  }
  // Çalma saati önizlemesi zamanla kaysın
  if (totalMinutes()) $("ringAt").textContent = fmtClock(new Date(now + totalMinutes() * 60000));
}

$("dialFill").style.strokeDasharray = DIAL_LEN;
updateSetup();
renderList();
setInterval(tick, 1000);
