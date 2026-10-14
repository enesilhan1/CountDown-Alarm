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

// Tarayıcıda seçilen ses dosyası (yoksa bip)
const ringAudio = new Audio();
ringAudio.loop = true;

function startRinging(alarm) {
  // Android'de sesi ve titreşimi yerel servis çalıyor
  if (ringTimer || Alarm) return;
  if (alarm?.sound) {
    ringAudio.src = alarm.sound;
    ringAudio.play().catch(() => {}); // çalınamazsa bip devreye girer
  }
  const pulse = () => {
    if (ringAudio.paused) beep();
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
  ringAudio.pause();
  ringAudio.removeAttribute("src");
  navigator.vibrate?.(0);
}

/* ---------- Bildirim ---------- */

const notified = new Set();

function requestNotifyPermission() {
  if (Alarm) {
    Alarm.checkPermissions()
      .then((p) => p.notifications !== "granted" && Alarm.requestPermissions())
      .then(askFullScreenOnce)
      .catch(() => {});
  } else if ("Notification" in window && Notification.permission === "default") {
    Notification.requestPermission();
  }
}

// Android 14+: kilit ekranında çalma ekranının açılması için ayrı izin gerekebilir
async function askFullScreenOnce() {
  const KEY = "sureAlarm.fullScreenAsked";
  try {
    if (localStorage.getItem(KEY)) return;
    const { granted } = await Alarm.canUseFullScreen();
    if (granted) return;
    localStorage.setItem(KEY, "1");
    if (confirm("Alarm çalınca kilit ekranında tam ekran açılabilmesi için izin gerekiyor. Ayarları açayım mı?")) {
      await Alarm.openFullScreenSettings();
    }
  } catch {
    // İzin sorulamazsa alarm yine bildirimle çalar
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
    syncPresetsNative();
    await Alarm.addListener("alarmAction", syncNative);
    await Alarm.addListener("previewEnded", () => setPreviewing(false));
    document.addEventListener("visibilitychange", () => !document.hidden && syncNative());
    // Gelecekteki tüm alarmların sistemde kurulu olduğundan emin ol
    alarms.filter((a) => a.end > Date.now()).forEach(scheduleNative);
  } catch (e) {
    console.warn("Yerel alarm hazırlanamadı", e);
  }
}

// Uygulama dışında (bildirim, widget) yapılan işlemleri listeye uygula
async function syncNative() {
  try {
    const { actions } = await Alarm.takePendingActions();
    let changed = false;
    for (const act of actions) {
      const i = alarms.findIndex((a) => a.id === act.alarmId);
      if (act.type === "create") {
        // Widget'tan kurulan alarm
        const it = act.item;
        if (i === -1 && it) {
          alarms.push({
            id: it.alarmId,
            nid: it.nid,
            label: it.label,
            duration: Math.round((it.at - it.created) / 60000),
            created: it.created,
            start: it.created,
            end: it.at,
            snoozes: 0,
            sound: it.sound || "",
            soundName: it.soundName || "",
            presetId: it.presetId || "",
          });
          changed = true;
        }
        continue;
      }
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
    sound: a.sound || "",
    soundName: a.soundName || "",
    presetId: a.presetId || "",
  }).catch((e) => console.warn("Alarm sisteme kurulamadı", e));
}

// Bekleyen alarmı iptal eder; çalıyorsa susturur
function cancelNative(a) {
  if (!Alarm || a?.nid == null) return Promise.resolve();
  return Alarm.cancel({ nid: a.nid }).catch(() => {});
}

/* ---------- Alarm sesi ---------- */

// Seçilen ses yeni alarmlar için varsayılan olur; her alarm kendi sesini saklar.
// Android'de content:// adresi kalıcıdır. Tarayıcıda dosya yalnızca bu oturumda
// çalınabildiği için kaydedilmez.
const SOUND_KEY = "sureAlarm.sound";
let sound = loadSound(); // { uri, name } | null (null: varsayılan alarm sesi)

function loadSound() {
  if (!Alarm) return null;
  try {
    const s = JSON.parse(localStorage.getItem(SOUND_KEY));
    return s?.uri ? s : null;
  } catch {
    return null;
  }
}

function saveSound() {
  if (!Alarm) return;
  try {
    if (sound) localStorage.setItem(SOUND_KEY, JSON.stringify(sound));
    else localStorage.removeItem(SOUND_KEY);
  } catch {
    // Kaydedilemezse seçim yalnızca bu oturumda geçerli
  }
}

function renderSound() {
  $("soundName").textContent = sound?.name || "Varsayılan alarm sesi";
  $("soundName").title = sound?.name || "";
  $("soundReset").hidden = !sound;
}

$("soundPick").addEventListener("click", async () => {
  stopPreview();
  if (!Alarm) {
    $("soundFile").click();
    return;
  }
  try {
    const r = await Alarm.pickSound();
    if (r.cancelled) return;
    sound = { uri: r.uri, name: r.name };
    saveSound();
    renderSound();
  } catch (e) {
    alert(e.message || "Ses seçilemedi");
  }
});

$("soundFile").addEventListener("change", (e) => {
  const file = e.target.files?.[0];
  e.target.value = "";
  if (!file) return;
  sound = { uri: URL.createObjectURL(file), name: file.name.replace(/\.[^.]+$/, "") };
  renderSound();
});

$("soundReset").addEventListener("click", () => {
  stopPreview();
  sound = null;
  saveSound();
  renderSound();
});

/* Dinle (en fazla 15 sn) */
const previewAudio = new Audio();
let previewing = false;
let previewTimer = null;

function setPreviewing(on) {
  previewing = on;
  const btn = $("soundPreview");
  btn.textContent = on ? "■" : "▶";
  btn.classList.toggle("playing", on);
  btn.setAttribute("aria-label", on ? "Durdur" : "Sesi dinle");
  if (!on) clearTimeout(previewTimer);
}

function stopPreview() {
  if (!previewing) return;
  if (Alarm) Alarm.stopPreview().catch(() => {});
  else previewAudio.pause();
  setPreviewing(false);
}

$("soundPreview").addEventListener("click", async () => {
  if (previewing) {
    stopPreview();
    return;
  }
  try {
    if (Alarm) {
      await Alarm.previewSound({ uri: sound?.uri || "" });
    } else if (sound) {
      previewAudio.src = sound.uri;
      await previewAudio.play();
      previewTimer = setTimeout(stopPreview, 15000);
    } else {
      ensureAudio(); // tarayıcıda varsayılan ses: bip
      beep();
      return;
    }
    setPreviewing(true);
  } catch {
    alert("Ses çalınamadı");
  }
});
previewAudio.addEventListener("ended", () => setPreviewing(false));

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
  $("presetSave").disabled = total === 0;
  // Kayıtlı süre: süre ve etiket aynıysa seçili; yerleşikler yalnızca o zaman değilse
  const label = labelEl.value.trim();
  let customActive = false;
  document.querySelectorAll(".chip-custom").forEach((c) => {
    const p = presets.find((x) => x.id === c.dataset.id);
    const on = !!p && p.minutes === total && (p.label || "") === label;
    c.classList.toggle("active", on);
    customActive ||= on;
  });
  document.querySelectorAll(".chip[data-min]").forEach((c) =>
    c.classList.toggle("active", !customActive && Number(c.dataset.min) === total)
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

document.querySelectorAll(".chip[data-min]").forEach((chip) =>
  chip.addEventListener("click", () => setTotal(Number(chip.dataset.min)))
);

[hoursEl, minutesEl, labelEl].forEach((el) => el.addEventListener("input", updateSetup));

/* ---------- Kayıtlı hazır süreler ---------- */

// Kullanıcının kaydettiği süre + etiket (+ Android'de ses) kombinasyonları
const PRESETS_KEY = "sureAlarm.presets";
const MAX_PRESETS = 12;
const presets = loadPresets();
let editingPresets = false;

function loadPresets() {
  try {
    const data = JSON.parse(localStorage.getItem(PRESETS_KEY));
    return Array.isArray(data) ? data : [];
  } catch {
    return [];
  }
}

function savePresets() {
  try {
    localStorage.setItem(PRESETS_KEY, JSON.stringify(presets));
  } catch {
    // Kaydedilemezse yalnızca bu oturumda kalır
  }
  syncPresetsNative();
}

// Android'de ana ekran widget'ları hazır süreleri buradan alır
function syncPresetsNative() {
  if (Alarm) Alarm.syncPresets({ presets }).catch(() => {});
}

function renderPresets() {
  const box = $("presets");
  box.querySelectorAll(".chip-custom").forEach((c) => c.remove());
  const firstBuiltin = box.querySelector(".chip[data-min]");
  for (const p of presets) {
    const chip = document.createElement("button");
    chip.type = "button";
    chip.className = "chip chip-custom";
    chip.dataset.id = p.id;
    if (p.soundName) chip.title = `🎵 ${p.soundName}`;

    const name = document.createElement("span");
    name.className = "chip-label";
    name.textContent = p.label || fmtDuration(p.minutes);
    chip.append(name);
    if (p.label) {
      const dur = document.createElement("span");
      dur.className = "chip-dur";
      dur.textContent = fmtDuration(p.minutes);
      chip.append(dur);
    }
    if (p.soundName) chip.append("🎵");
    const x = document.createElement("span");
    x.className = "chip-x";
    x.textContent = "×";
    chip.append(x);
    chip.setAttribute("aria-label",
      [p.label, fmtDuration(p.minutes), p.soundName && `ses: ${p.soundName}`].filter(Boolean).join(", "));

    chip.addEventListener("click", () => (editingPresets ? deletePreset(p.id) : applyPreset(p)));
    box.insertBefore(chip, firstBuiltin);
  }
  $("presetEdit").hidden = presets.length === 0;
  if (!presets.length) setEditing(false);
  updateSetup();
}

function applyPreset(p) {
  labelEl.value = p.label || "";
  if (p.sound) {
    sound = { uri: p.sound, name: p.soundName };
    saveSound();
    renderSound();
  }
  setTotal(p.minutes);
}

function deletePreset(id) {
  const i = presets.findIndex((p) => p.id === id);
  if (i !== -1) presets.splice(i, 1);
  savePresets();
  renderPresets();
}

function setEditing(on) {
  editingPresets = on;
  $("presets").classList.toggle("editing", on);
  $("presetEdit").textContent = on ? "Bitti" : "Düzenle";
}

$("presetEdit").addEventListener("click", () => setEditing(!editingPresets));

$("presetSave").addEventListener("click", () => {
  const minutes = totalMinutes();
  if (!minutes) return;
  const label = labelEl.value.trim();
  // Tarayıcıda seçilen dosya sayfa yenilenince kaybolur; sesi yalnızca Android'de kaydet
  const keep = Alarm && sound ? sound : null;
  // Aynı süre + etiket zaten varsa yenisini ekleme, sesini güncelle
  let p = presets.find((x) => x.minutes === minutes && (x.label || "") === label);
  if (p) {
    p.sound = keep?.uri || "";
    p.soundName = keep?.name || "";
  } else {
    if (presets.length >= MAX_PRESETS) {
      alert(`En fazla ${MAX_PRESETS} hazır süre kaydedebilirsin. Önce Düzenle ile birini sil.`);
      return;
    }
    p = { id: crypto.randomUUID(), minutes, label, sound: keep?.uri || "", soundName: keep?.name || "" };
    presets.push(p);
  }
  savePresets();
  setEditing(false);
  renderPresets();

  $("presets").querySelector(`.chip-custom[data-id="${p.id}"]`)?.classList.add("flash");
  const btn = $("presetSave");
  btn.textContent = "✓ Kaydedildi";
  btn.classList.add("saved");
  clearTimeout(btn.resetTimer);
  btn.resetTimer = setTimeout(() => {
    btn.textContent = "＋ Kaydet";
    btn.classList.remove("saved");
  }, 1500);
});

createBtn.addEventListener("click", () => {
  const total = totalMinutes();
  if (!total) return;
  ensureAudio();
  stopPreview();
  setEditing(false);
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
    sound: sound?.uri || "",
    soundName: sound?.name || "",
    // Seçili kayıtlı hazır süreden kurulduysa widget'ı da bu alarmı gösterir
    presetId: document.querySelector(".chip-custom.active")?.dataset.id || "",
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
    li.querySelector(".alarm-sound").textContent = a.soundName ? ` · 🎵 ${a.soundName}` : "";
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
  $("ringSound").hidden = !!Alarm || audioCtx?.state === "running" || !ringAudio.paused;
  ringEl.hidden = false;
  document.title = "⏰ " + a.label;
  due.forEach(notify);
  startRinging(a);
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
renderPresets(); // updateSetup'ı da çağırır
renderSound();
renderList();
initNative();
setInterval(tick, 1000);
