# CountDown Alarm (Süre Alarmı)

Android için tasarlanan, **saat yerine süre ile** kurulan bir alarm uygulaması.
Klasik alarmlarda "07:30'da çal" dersiniz; burada "**2 saat 15 dakika sonra** çal" dersiniz.

Uygulama şu an mobil odaklı bir web uygulaması olarak geliştiriliyor; bilgisayardan da
kontrol ve test edilebiliyor. İlerleyen aşamada Capacitor ile Android uygulamasına (APK) dönüştürülecek.

## Özellikler

- Saat / dakika ayarı ve hazır süreler (15 dk – 8 sa)
- Kurulmadan önce alarmın saat kaçta çalacağının önizlemesi
- Etiketli alarmlar, canlı geri sayım ve ilerleme çubuğu
- Alarmlar tarayıcıda saklanır; sayfa yenilense de kaybolmaz
- Süre dolunca tam ekran çalma ekranı, ses, titreşim ve bildirim
- Ertele (5 / 10 / 15 dk) ve Kapat

## Çalıştırma

**Tarayıcıda (geliştirme / test):**

```bash
npm run serve
```

Ardından tarayıcıda `http://localhost:5173` adresini açın. Aynı Wi-Fi ağındaki telefondan
`http://<bilgisayar-IP>:5173` ile erişilebilir.

**Android:** Node.js ve Android Studio gerekir.

```bash
npm install
npm run sync   # www/ içeriğini Android projesine kopyalar
npm run open   # projeyi Android Studio'da açar
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
- [ ] Aşama 3 – Capacitor ile Android uygulaması, uygulama kapalıyken de çalan yerel alarm
