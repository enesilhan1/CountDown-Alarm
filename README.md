# CountDown Alarm (Süre Alarmı)

Android için, **saat yerine süre ile** kurulan sade bir alarm uygulaması.
Klasik alarmlarda "07:30'da çal" dersiniz; burada "**2 saat 15 dakika sonra** çal" dersiniz.

Günlük kullanım için yazılmış küçük bir uygulama: ilaç saatleri, çay/yemek, mola, çamaşır
gibi "şu kadar süre sonra haber ver" ihtiyaçları için.

## Özellikler

- Süreyi saat/dakika olarak seç ya da hazır sürelere dokun (15 dk – 8 sa); kurmadan önce
  alarmın saat kaçta çalacağını görürsün
- Gerçek bir alarm saati gibi çalar: telefonun alarm sesi kapatılana kadar çalar, titreşir,
  ekran kilitliyken tam ekran açılır; uygulama kapalı ya da telefon yeniden başlatılmış olsa da çalar
- **Ertele** (5 / 10 / 15 dk) ve **Kapat** — çalma ekranından ya da bildirimden
- **Etiket** ve **alarm sesi**: telefondaki herhangi bir ses dosyasını alarm sesi yapabilirsin
- **Kendi hazır sürelerin**: "İlaç · 8 sa" gibi süre + etiket + ses kombinasyonlarını kaydet
- **Tekrarlayan alarm** (🔁, isteğe bağlı): ör. 8 saatlik alarm, sen "Tekrarı bitir" diyene kadar
  8 saatte bir çalar; erteleme ya da geç kapatma saatleri kaydırmaz
- **Ana ekran widget'ları**: her widget bir hazır süreye bağlanır, tek dokunuşla uygulamayı
  açmadan alarm kurar. Geniş widget çalma saatini ve geri sayımı, 1x1 widget yalnızca süreyi
  ("8s", "42dk") gösterir. Boşta koyu, geri sayarken mor, çalarken kırmızı olur

## Telefona kurulum

Uygulama Google Play'de yok; APK dosyası olarak elle kurulur. **Android 7.0 ve üzeri** gerekir.

### 1. APK'yı indir

[Releases](https://github.com/enesilhan1/CountDown-Alarm/releases) sayfasındaki en son sürümden
`.apk` dosyasını telefonuna indir. (Bilgisayara indirdiysen USB kablosu, Google Drive ya da
mesajlaşma uygulamasıyla telefona aktarabilirsin.)

### 2. Kur

1. Telefonda indirdiğin `.apk` dosyasına dokun (Dosyalar uygulaması, indirilenler ya da bildirim).
2. Android **"Bu kaynaktan uygulama yüklenmesine izin verilmiyor"** derse **Ayarlar**'a dokun,
   **"Bu kaynaktan izin ver"** seçeneğini aç ve geri dön.
3. **Yükle**'ye bas.
4. Google Play Protect **"tanınmayan uygulama"** uyarısı gösterirse **"Yine de yükle"**yi seç.
   Uygulama Play Store'dan gelmediği için bu uyarı normaldir.

### 3. İlk açılışta izinler

- **Bildirim izni**: ilk alarmı kurarken sorulur, **İzin ver** de. Olmadan alarm bildirimi görünmez.
- **Tam ekran izni** (Android 14 ve üzeri): sorulursa **Evet** de ve açılan ayarda izni aç.
  Bu izin olmadan alarm kilit ekranında tam ekran açılmaz, yalnızca bildirim olarak gelir.

### 4. Pil ayarı (önemli)

Xiaomi, Samsung, Oppo, Huawei gibi bazı markalar arka plandaki uygulamaları kısıtlar ve alarm
gecikebilir. Bunu önlemek için:
**Ayarlar → Uygulamalar → Süre Alarmı → Pil** → **"Kısıtlama yok"** (ya da "Optimize etme").

Sonra 1 dakikalık bir alarm kurup ekranı kilitleyerek dene: alarm sesi duyulmalı ve ekran açılıp
çalma ekranı gelmeli.

### Widget ekleme

1. Uygulamada bir süre seç ve **＋ Kaydet** ile hazır süre olarak kaydet.
2. Ana ekranda boş bir yere uzun bas → **Widget'lar** → **Süre Alarmı**.
3. **Hazır süre** (geniş) ya da **Hazır süre · küçük** (1x1) widget'ını ekle.
4. Açılan ekrandan widget'ın hangi hazır süreyi başlatacağını seç.

Widget'a dokununca alarm kurulur; kuruluyken dokununca iptal edilir, çalarken dokununca kapanır.
Widget'ı uzun basıp **Ayarlar**'dan başka bir hazır süreye bağlayabilirsin.

### Güncelleme ve kaldırma

- **Güncelleme**: yeni sürümün APK'sını aynı şekilde indirip kur; eskisinin üzerine kurulur,
  alarmların ve hazır sürelerin korunur. Ana ekrandaki widget'lar kurulumdan sonra bir dakika
  kadar "Yükleniyor…" gösterebilir, sonra kendiliğinden düzelir.
- **Kaldırma**: uygulama simgesine uzun bas → **Kaldır**. Kurulu alarmlar ve ayarlar da silinir.

## Kaynaktan derleme

Geliştirmek ya da APK'yı kendin üretmek istersen: Node.js, Android Studio ve **Java 21** gerekir.
(Android Studio'nun getirdiği Java 25, projenin Gradle 8.14 sürümüyle çalışmaz.)

```bash
npm install
npm run sync   # www/ içeriğini Android projesine kopyalar
npm run open   # projeyi Android Studio'da açar
```

Android Studio'da *Settings → Build Tools → Gradle → Gradle JDK* olarak Java 21 seçilmeli.
Komut satırından APK üretmek için (`JAVA_HOME` Java 21'i göstermeli):

```bash
cd android
./gradlew assembleDebug   # çıktı: android/app/build/outputs/apk/debug/app-debug.apk
```

Arayüzü bilgisayarda tarayıcıda denemek için `npm run serve` çalıştırıp `http://localhost:5173`
adresini aç. (Tarayıcıda alarm yalnızca sayfa açıkken çalar; gerçek alarm davranışı Android'dedir.)

### Proje yapısı

| Dosya | Açıklama |
|-------|----------|
| `www/` | Arayüz (HTML, CSS, JavaScript): kurulum paneli, alarm listesi, çalma ekranı |
| `android/` | Capacitor ile oluşturulan Android projesi |
| `android/app/src/main/java/.../alarm/` | Yerel alarm: zamanlayıcı, çalma servisi, JS köprüsü (`Alarm` eklentisi) |
| `android/app/src/main/java/.../widget/` | Ana ekran widget'ları ve widget eklerken açılan seçim ekranı |
| `capacitor.config.json` | Capacitor ayarları (uygulama kimliği, adı, web klasörü) |

## Lisans

[MIT](LICENSE) © 2026 Enes İlhan
