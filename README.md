# Revora

OBD-II araçlar için Android gösterge uygulaması. ELM327 uyumlu adaptörlerle Bluetooth üzerinden bağlanır (ilk test aracı: Golf Mk6 1.4 TSI + Vgate iCar Pro BT 3.0).

## APK'yı telefona kurmak

1. GitHub'da **Releases → latest** sayfasından `revora.apk` dosyasını indir.
2. Telefonda dosyayı aç. "Bilinmeyen kaynaklardan yükleme" izni isterse ver.
3. Her `main` güncellemesinde yeni APK otomatik derlenir ve eskisinin üstüne kurulur.

## İlk kullanım

1. Adaptörü direksiyonun altındaki OBD soketine tak, kontağı aç.
2. Telefonun Bluetooth ayarlarından adaptörü eşleştir (PIN genelde `1234` ya da `0000`).
3. Uygulamada **Bağlantı** sekmesinden adaptörü seç. Sonraki açılışlarda kendisi bağlanır.
4. Araba yokken denemek için **Demo modu**nu aç.

**Bluetooth LE adaptör** (Vgate iCar Pro BLE, vLinker BLE, OBDLink CX, Veepeak BLE ve klonlar): telefonda eşleştirilmez. **Bağlantı** sekmesinde "Bluetooth LE adaptör" başlığındaki **Ara**'ya bas, listeden adaptörü seç. iPhone'da çalışan Bluetooth adaptörler sadece bunlardır.

**Wi-Fi adaptör:** Adaptörü tak, telefonun Wi-Fi ayarlarından adaptörün ağına bağlan (genelde `WiFi_OBDII`), sonra **Bağlantı** sekmesinde adresi kontrol edip (çoğunda `192.168.0.10:35000`) **Wi-Fi ile bağlan**'a bas. Adaptörün ağında internet olmadığı için uygulama bağlantıyı özellikle bu Wi-Fi üzerinden açar.

**Geçmiş** sekmesi her sürüşü kendiliğinden kaydeder: süre, mesafe, ortalama ve en yüksek hız, en yüksek devir, rölantide geçen süre, en yüksek su sıcaklığı, en düşük akü voltajı ve hız, devir ve sıcaklık grafikleri. Motor 30 saniye kapalı kalınca sürüş biter, 30 saniyeden kısa kayıtlar tutulmaz. Kayıtlar sadece telefonda durur.

**Bağlantı** sekmesinin üstünde adaptör türü seçilir (Bluetooth / BLE / Wi-Fi). Bir adaptör ya da araç çalışmazsa en alttaki **Günlüğü paylaş** düğmesi, adaptöre giden komutları ve cevapları metin olarak paylaşır.

Telefon yatay çevrilince ana gösterge araç ekranı düzenine geçer.

## Okunan veriler

Standart OBD (mod 01): devir, hız, su sıcaklığı, emme sıcaklığı, dış sıcaklık, motor yükü, gaz pedalı ve akü voltajı (`ATRV`).

Bağlanınca uygulama arabanın desteklediği PID listesini (0100, 0120, 0140…) okur ve sadece onları sorar. Yakıt seviyesi, yakıt düzeltmeleri, MAF, manifold basıncı, ateşleme avansı, katalizör ve yağ sıcaklığı, yakıt tüketimi gibi 30'a yakın ek standart veri, araba destekliyorsa **Veriler** sekmesinde sırayla gösterilir. VIN (mod 09) okunur ve marka ile model yılı çıkarılır. Paylaşılan günlükte VIN gizlenir.

Birimler **Bağlantı → Birimler** bölümünden seçilir: km/h·km ya da mph·mil, °C ya da °F, kPa, bar ya da psi. İlk açılışta telefonun ülkesine göre seçilir (ABD: mph, °F, psi; İngiltere: mph, °C, psi). Uygulama içinde her şey metrik tutulur, sadece gösterim çevrilir.

## Diller

Desteklenen diller: İngilizce, Türkçe, Almanca, Fransızca, İspanyolca, İtalyanca, Portekizce, Felemenkçe, Lehçe, Çekçe, Rusça, Ukraynaca. Uygulama ilk açılışta telefonun dilini kullanır (elimizde yoksa İngilizce); **Bağlantı → Dil** bölümünden değiştirilebilir. Tüm yazılar `core/.../I18n.kt` içindeki `Str` listesindedir (İngilizce metniyle birlikte); her dil ayrı bir dosyada (`I18nTr.kt` gibi) çevirileri tutar. Arıza kodu açıklamaları `DtcInfo.kt` (İngilizce, Türkçe) ve `DtcXx.kt` (diğer diller) içindedir; test hepsinin aynı kodları kapsadığını kontrol eder. `I18nTest` her dilin her yazıyı ve aynı `{0}` yer tutucularını içerdiğini kontrol eder; yeni dil eklemek için `Lang`'a bir satır ve bir çeviri dosyası yeter.

## Google Play paketi

Play paketi (`revora.aab`) sadece yükleme anahtarı GitHub Secrets'a eklendiğinde derlenir: `UPLOAD_KEYSTORE_BASE64`, `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD`.

## Proje yapısı

- `core/`: Android'den bağımsız OBD mantığı (ELM327 komutları, PID çözümleme, demo adaptör). Testleri `CORE_ONLY=1 ./gradlew :core:test` ile Android SDK olmadan çalışır.
- `app/`: Jetpack Compose arayüzü ve Bluetooth bağlantısı.
- `design/`: ekran tasarımlarının kaynağı.
- `.github/workflows/build.yml`: testler, APK derleme ve "latest" sürümü.

## Plan

Ayrıntılı plan: proje belgesi "Mağaza sürümü planı".

1. Mağaza sürümü (Revora adı, imzalı Play paketi) ✓
2. Adaptör katmanı: Klasik BT, BLE ve Wi-Fi ELM327 ← şu an burada
3. Çok araç desteği (desteklenen PID'ler, VIN, marka profilleri, birimler, diller)
4. Cihaz uyumu, değiştirilebilir arayüz, araç içi Android ekranlar
5. Reklam (AdMob) ve gizlilik onayı
6. Google Play, sonra App Store

## Lisanslar

Sora yazı tipi SIL Open Font License 1.1 ile dağıtılır (Google Fonts).
