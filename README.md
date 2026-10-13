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

Herhangi bir statik sunucu yeterli. Örneğin Python ile:

```bash
python -m http.server 5173
```

Ardından tarayıcıda `http://localhost:5173` adresini açın. Aynı Wi-Fi ağındaki telefondan
`http://<bilgisayar-IP>:5173` ile erişilebilir.

## Dosyalar

| Dosya | Açıklama |
|-------|----------|
| `index.html` | Sayfa yapısı: kurulum paneli, alarm listesi, çalma ekranı |
| `style.css` | Koyu tema; telefonda tek sütun, geniş ekranda iki sütun |
| `app.js` | Alarm mantığı, kayıt, ses/titreşim/bildirim |

## Yol haritası

- [x] Aşama 1 – Arayüz
- [x] Aşama 2 – Kalıcı alarmlar ve çalma ekranı
- [ ] Aşama 3 – Capacitor ile Android uygulaması, uygulama kapalıyken de çalan yerel alarm
