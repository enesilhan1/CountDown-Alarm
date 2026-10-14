# CountDown Alarm (Süre Alarmı)

Android için tasarlanan, **saat yerine süre ile** kurulan bir alarm uygulaması.
Klasik alarmlarda "07:30'da çal" dersiniz; burada "**2 saat 15 dakika sonra** çal" dersiniz.

Arayüz bir web uygulaması olarak yazıldı; bilgisayardan tarayıcıda test edilebiliyor.
Capacitor ile Android uygulamasına (APK) dönüştürülüyor ve Android'de alarmlar sistemin
alarm servisine kuruluyor, yani uygulama kapalıyken veya telefon kilitliyken de çalıyor.

## Özellikler

- Saat / dakika ayarı ve hazır süreler (15 dk – 8 sa)
- Kurulmadan önce alarmın saat kaçta çalacağının önizlemesi
- Etiketli alarmlar, canlı geri sayım ve ilerleme çubuğu
- Alarmlar tarayıcıda saklanır; sayfa yenilense de kaybolmaz
- Süre dolunca tam ekran çalma ekranı, ses, titreşim ve bildirim
- Ertele (5 / 10 / 15 dk) ve Kapat
- **Android:** tam zamanında çalan sistem alarmı (AlarmManager), bildirimden Ertele / Kapat,
  telefon yeniden başlasa da alarmlar korunur

## Çalıştırma

**Tarayıcıda (geliştirme / test):**

```bash
npm run serve
```

Ardından tarayıcıda `http://localhost:5173` adresini açın. Aynı Wi-Fi ağındaki telefondan
`http://<bilgisayar-IP>:5173` ile erişilebilir.

**Android:** Node.js, Android Studio ve **Java 21** gerekir. (Android Studio'nun getirdiği
Java 25, projenin Gradle 8.14 sürümüyle çalışmaz.)

```bash
npm install
npm run sync   # www/ içeriğini Android projesine kopyalar
npm run open   # projeyi Android Studio'da açar
```

Android Studio'da *Settings → Build Tools → Gradle → Gradle JDK* olarak Java 21 seçilmeli.
Komut satırından test APK'sı üretmek için (`JAVA_HOME` Java 21'i göstermeli):

```bash
cd android
./gradlew assembleDebug   # çıktı: android/app/build/outputs/apk/debug/app-debug.apk
```

## Dosyalar

| Dosya | Açıklama |
|-------|----------|
| `www/index.html` | Sayfa yapısı: kurulum paneli, alarm listesi, çalma ekranı |
| `www/style.css` | Koyu tema; telefonda tek sütun, geniş ekranda iki sütun |
| `www/app.js` | Alarm mantığı, kayıt, ses/titreşim/bildirim |
| `capacitor.config.json` | Capacitor ayarları (uygulama kimliği, adı, web klasörü) |
| `android/` | Capacitor'ın oluşturduğu Android Studio projesi |

## Yol haritası

- [x] Aşama 1 – Arayüz
- [x] Aşama 2 – Kalıcı alarmlar ve çalma ekranı
- [x] Aşama 3 – Capacitor ile Android uygulaması, uygulama kapalıyken de çalan sistem alarmı
- [ ] Aşama 4 – Kilit ekranında tam ekran çalma, sürekli çalan alarm sesi, uygulama simgesi
