# CountDown Alarm (Süre Alarmı)

Android için tasarlanan, **saat yerine süre ile** kurulan bir alarm uygulaması.
Klasik alarmlarda "07:30'da çal" dersiniz; burada "**2 saat 15 dakika sonra** çal" dersiniz.

Arayüz bir web uygulaması olarak yazıldı; bilgisayardan tarayıcıda test edilebiliyor.
Capacitor ile Android uygulamasına (APK) dönüştürülüyor. Android'de alarmlar yerel bir
alarm servisiyle çalıyor: uygulama kapalıyken veya telefon kilitliyken de, gerçek bir alarm
saati gibi kapatılana kadar çalar.

## Özellikler

- Saat / dakika ayarı ve hazır süreler (15 dk – 8 sa)
- Kurulmadan önce alarmın saat kaçta çalacağının önizlemesi
- Etiketli alarmlar, canlı geri sayım ve ilerleme çubuğu
- Alarmlar tarayıcıda saklanır; sayfa yenilense de kaybolmaz
- Süre dolunca tam ekran çalma ekranı, ses, titreşim ve bildirim
- Ertele (5 / 10 / 15 dk) ve Kapat
- **Android:**
  - Alarm saati yöntemiyle kurulum (`setAlarmClock`): güç tasarrufunda ertelenmez,
    durum çubuğunda alarm simgesi
  - Telefonun alarm sesi, alarm ses seviyesinde döngüde çalar ve titreşir; yanıt
    verilmezse 10 dk sonra susar
  - Ekran kapalı/kilitliyken çalma ekranı kilit ekranının üstünde tam ekran açılır
  - Bildirimden Ertele / Kapat (uygulama kapalıyken de)
  - Telefon yeniden başlasa da alarmlar korunur

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
| `android/` | Android Studio projesi |
| `android/app/src/main/java/.../alarm/` | Yerel alarm: zamanlayıcı, çalma servisi, JS köprüsü (`Alarm` eklentisi) |

## Yol haritası

- [x] Aşama 1 – Arayüz
- [x] Aşama 2 – Kalıcı alarmlar ve çalma ekranı
- [x] Aşama 3 – Capacitor ile Android uygulaması, uygulama kapalıyken de çalan sistem alarmı
- [x] Aşama 4 – Sürekli çalan alarm sesi, kilit ekranında tam ekran çalma, uygulama simgesi
