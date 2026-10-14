// Android'de alarmlar yerel Alarm eklentisiyle çalar (www dışı: android/.../alarm/).
// Tarayıcıda ses + titreşim + bildirim + çalma ekranı bu dosyada.

const $ = (id) => document.getElementById(id);
const hoursEl = $("hours");
const minutesEl = $("minutes");
const labelEl = $("label");
const createBtn = $("createBtn");
const DIAL_LEN = 2 * Math.PI * 88;
const DIAL_MAX_MIN = 12 * 60; // kadran 12 saatte dolar
const STORAGE_KEY = "sureAlarm.alarms";

const pad = (n) => String(n).padStart(2, "0");
const fmtClock = (d) => `${pad(d.getHours())}:${pad(d.getMinutes())}`;

function fmtRemaining(ms) {
  const s = Math.max(0, Math.ceil(ms / 1000));
  return `${pad(Math.floor(s / 3600))}:${pad(Math.floor((s % 3600) / 60))}:${pad(s % 60)}`;
}

function fmtDuration(min) {
  const h = Math.floor(min / 60), m = min % 60;
  return [h && `${h} sa`, m && `${m} dk`].filter(Boolean).join(" ") || "0 dk";
}

/* ---------- Kalıcılık ---------- */

function loadAlarms() {
  try {
    const data = JSON.parse(localStorage.getItem(STORAGE_KEY));
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
}

function saveAlarms() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(alarms));
  } catch {
    // Gizli sekme vb. — alarmlar sadece bu oturumda kalır
  }
}

const alarms = loadAlarms();
// Eski kayıtlara sistem bildirimi için sayısal kimlik ver
alarms.forEach((a) => (a.nid ??= newNid()));
saveAlarms();

/* ---------- Ses ve titreşim ---------- */

let audioCtx = null;
let ringTimer = null;

function ensureAudio() {
  try {
    audioCtx ??= new (window.AudioContext || window.webkitAudioContext)();
    if (audioCtx.state === "suspended") audioCtx.resume();
  } catch {
    // Ses desteklenmiyor
  }
}

// Tarayıcılar sesi ancak kullanıcı dokunduktan sonra açar
document.addEventListener("pointerdown", ensureAudio);
document.addEventListener("keydown", ensureAudio);

function beep() {
  if (!audioCtx || audioCtx.state !== "running") return;
  const t = audioCtx.currentTime;
  for (let i = 0; i < 3; i++) {
    const start = t + i * 0.25;
    const osc = audioCtx.createOscillator();
    const gain = audioCtx.createGain();
    osc.type = "square";
    osc.frequency.value = 880;
    gain.gain.setValueAtTime(0.0001, start);
    gain.gain.exponentialRampToValueAtTime(0.25, start + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.0001, start + 0.18);
    osc.connect(gain).connect(audioCtx.destination);
    osc.start(start);
    osc.stop(start + 0.2);
  }
}

function startRinging() {
  // Android'de sesi ve titreşimi yerel servis çalıyor
  if (ringTimer || Alarm) return;
  const pulse = () => {
    beep();
    // Tarayıcı, kullanıcı ekrana dokunmadan titreşime izin vermiyor
    if (navigator.userActivation?.hasBeenActive ?? true) navigator.vibrate?.([500, 300, 500]);
  };
  pulse();
  ringTimer = setInterval(pulse, 1500);
}

function stopRinging() {
  if (!ringTimer) return;
  clearInterval(ringTimer);
  ringTimer = null;
  navigator.vibrate?.(0);
}

/* ---------- Bildirim ---------- */

const notified = new Set();

function requestNotifyPermission() {
  if (Alarm) {
    Alarm.checkPermissions()
      .then((p) => p.notifications !== "granted" && Alarm.requestPermissions())
      .catch(() => {});
  } else if ("Notification" in window && Notification.permission === "default") {
    Notification.requestPermission();
  }
}

function notify(alarm) {
  if (notified.has(alarm.id)) return;
  notified.add(alarm.id);
  // Android'de bildirimi yerel servis gösteriyor
  if (Alarm || !("Notification" in window) || Notification.permission !== "granted") return;
  try {
    const n = new Notification("⏰ " + alarm.label, {
      body: `${fmtClock(new Date(alarm.created))}'de kurulan alarm çalıyor`,
      tag: alarm.id,
      requireInteraction: true,
    });
    n.onclick = () => window.focus();
  } catch {
    // Bazı mobil tarayıcılar yalnızca service worker üzerinden bildirime izin verir
  }
}

/* ---------- Android yerel alarm ---------- */

// Uygulama Android'de (Capacitor içinde) çalışıyorsa alarmlar yerel Alarm
// eklentisiyle AlarmManager'a kurulur. Süre dolunca yerel servis alarm sesini
// döngüde çalar; uygulama kapalı ya da telefon kilitliyken de.
const Alarm = window.Capacitor?.isNativePlatform?.() ? window.Capacitor.Plugins.Alarm : null;

function newNid() {
  return Math.floor(Math.random() * 2 ** 31);
}

async function initNative() {
  if (!Alarm) return;
  try {
    await syncNative();
    await Alarm.addListener("alarmAction", syncNative);
    document.addEventListener("visibilitychange", () => !document.hidden && syncNative());
    // Gelecekteki tüm alarmların sistemde kurulu olduğundan emin ol
    alarms.filter((a) => a.end > Date.now()).forEach(scheduleNative);
  } catch (e) {
    console.warn("Yerel alarm hazırlanamadı", e);
  }
}

// Uygulama kapalıyken bildirimden yapılan Ertele/Kapat işlemlerini listeye uygula
async function syncNative() {
  try {
    const { actions } = await Alarm.takePendingActions();
    let changed = false;
    for (const act of actions) {
      const i = alarms.findIndex((a) => a.id === act.alarmId);
      if (i === -1) continue;
      if (act.type === "dismiss") {
        alarms.splice(i, 1);
      } else if (act.type === "snooze") {
        const a = alarms[i];
        a.start = act.time;
        a.end = act.at;
        a.snoozes = (a.snoozes || 0) + 1;
        notified.delete(a.id);
      }
      changed = true;
    }
    if (changed) commit();
  } catch (e) {
    console.warn("Alarm işlemleri alınamadı", e);
  }
}

function scheduleNative(a) {
  if (!Alarm) return Promise.resolve();
  return Alarm.schedule({
    nid: a.nid,
    alarmId: a.id,
    label: a.label,
    at: a.end,
    created: a.created,
  }).catch((e) => console.warn("Alarm sisteme kurulamadı", e));
}

// Bekleyen alarmı iptal eder; çalıyorsa susturur
function cancelNative(a) {
  if (!Alarm || a?.nid == null) return Promise.resolve();
  return Alarm.cancel({ nid: a.nid }).catch(() => {});
}

/* ---------- Kurulum paneli ---------- */

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
  ensureAudio();
  requestNotifyPermission();
  const now = Date.now();
  const alarm = {
    id: crypto.randomUUID(),
    nid: newNid(),
    label: labelEl.value.trim() || "Alarm",
    duration: total,
    created: now,
    start: now,
    end: now + total * 60000,
    snoozes: 0,
  };
  alarms.push(alarm);
  scheduleNative(alarm);
  labelEl.value = "";
  setTotal(0);
  commit();
});

/* ---------- Alarm işlemleri ---------- */

function removeAlarm(id) {
  const i = alarms.findIndex((a) => a.id === id);
  if (i !== -1) cancelNative(alarms.splice(i, 1)[0]);
  notified.delete(id);
  commit();
}

function snoozeAlarm(id, minutes) {
  const a = alarms.find((x) => x.id === id);
  if (!a) return;
  const now = Date.now();
  a.start = now;
  a.end = now + minutes * 60000;
  a.snoozes = (a.snoozes || 0) + 1;
  notified.delete(id);
  // Çalan bildirimi kaldır, yeni süreyle tekrar kur
  cancelNative(a).then(() => scheduleNative(a));
  commit();
}

function commit() {
  alarms.sort((a, b) => a.end - b.end);
  saveAlarms();
  renderList();
}

/* ---------- Liste ---------- */

function renderList() {
  const ul = $("alarms");
  const tpl = $("alarmTpl");
  ul.innerHTML = "";
  for (const a of alarms) {
    const li = tpl.content.firstElementChild.cloneNode(true);
    li.dataset.id = a.id;
    li.querySelector(".alarm-label").textContent =
      a.label + (a.snoozes ? ` (${a.snoozes}× ertelendi)` : "");
    li.querySelector(".alarm-at").textContent = fmtClock(new Date(a.end));
    li.querySelector(".delete").addEventListener("click", () => removeAlarm(a.id));
    ul.appendChild(li);
  }
  $("count").textContent = alarms.length;
  $("empty").hidden = alarms.length > 0;
  tick();
}

/* ---------- Çalma ekranı ---------- */

const ringEl = $("ring");
let ringingId = null;

$("dismissBtn").addEventListener("click", () => ringingId && removeAlarm(ringingId));
document.querySelectorAll(".snooze").forEach((btn) =>
  btn.addEventListener("click", () => ringingId && snoozeAlarm(ringingId, Number(btn.dataset.min)))
);

function updateRinging(now) {
  const due = alarms.filter((a) => a.end <= now);
  if (!due.length) {
    ringingId = null;
    ringEl.hidden = true;
    document.title = "Süre Alarmı";
    stopRinging();
    return;
  }
  const a = due[0];
  if (ringingId !== a.id) {
    ringingId = a.id;
    // Açık klavye çalma ekranını kapatmasın
    document.activeElement?.blur();
    $("ringLabel").textContent = a.label;
    $("ringSub").textContent =
      `${fmtClock(new Date(a.created))}'de ${fmtDuration(a.duration ?? Math.round((a.end - a.start) / 60000))} için kuruldu` +
      (a.snoozes ? ` · ${a.snoozes}× ertelendi` : "");
  }
  $("ringMore").textContent = due.length > 1 ? `+${due.length - 1} alarm daha çalıyor` : "";
  $("ringSound").hidden = !!Alarm || audioCtx?.state === "running";
  ringEl.hidden = false;
  document.title = "⏰ " + a.label;
  due.forEach(notify);
  startRinging();
}

/* ---------- Saat döngüsü ---------- */

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
  updateRinging(now);
}

// Başka sekmede değişiklik olursa (örn. bilgisayarda iki pencere) senkron kal
window.addEventListener("storage", (e) => {
  if (e.key !== STORAGE_KEY) return;
  alarms.splice(0, alarms.length, ...loadAlarms());
  renderList();
});

// Arka plandan dönünce hemen güncelle (tarayıcılar arka planda zamanlayıcıyı yavaşlatır)
document.addEventListener("visibilitychange", () => !document.hidden && tick());

$("dialFill").style.strokeDasharray = DIAL_LEN;
updateSetup();
renderList();
initNative();
setInterval(tick, 1000);
