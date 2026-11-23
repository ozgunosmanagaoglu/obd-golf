# Golf OBD

VW Golf Mk6 (1.4 TSI, DSG) için Android OBD göstergesi. Vgate iCar Pro BT 3.0 (ELM327) adaptörüyle Bluetooth üzerinden bağlanır.

## APK'yı telefona kurmak

1. GitHub'da **Releases → latest** sayfasından `golf-obd.apk` dosyasını indir.
2. Telefonda dosyayı aç. "Bilinmeyen kaynaklardan yükleme" izni isterse ver.
3. Her `main` güncellemesinde yeni APK otomatik derlenir ve eskisinin üstüne kurulur.

## İlk kullanım

1. Adaptörü direksiyonun altındaki OBD soketine tak, kontağı aç.
2. Telefonun Bluetooth ayarlarından adaptörü eşleştir (PIN genelde `1234` ya da `0000`).
3. Uygulamada **Bağlantı** sekmesinden adaptörü seç. Sonraki açılışlarda kendisi bağlanır.
4. Araba yokken denemek için **Demo modu**nu aç.

**Garaj** sekmesi 3D bir Golf gösterir: parmakla döndürülür, kaput, kapılar ve bagaj dokununca açılır. Motora dokununca kaput açılır ve motor ile turbo görünür. Arabaya bağlıyken motor gerçek devirle çalışır, bağlı değilken "Basılı tut · Gaz" ile simüle edilir. Sayfa `design/garage3d.html` içinde, uygulamadaki internetsiz kopyası `python3 tools/build_garage3d.py` ile üretilir.

**Geçmiş** sekmesi her sürüşü kendiliğinden kaydeder: süre, mesafe, ortalama ve en yüksek hız, en yüksek devir, rölantide geçen süre, en yüksek su sıcaklığı, en düşük akü voltajı ve hız, devir ve sıcaklık grafikleri. Motor 30 saniye kapalı kalınca sürüş biter, 30 saniyeden kısa kayıtlar tutulmaz. Kayıtlar sadece telefonda durur.

Telefon yatay çevrilince ana gösterge araç ekranı düzenine geçer.

## Okunan veriler

Standart OBD (mod 01): devir, hız, su sıcaklığı, emme sıcaklığı, dış sıcaklık, motor yükü, gaz pedalı ve akü voltajı (`ATRV`).

## Proje yapısı

- `core/`: Android'den bağımsız OBD mantığı (ELM327 komutları, PID çözümleme, demo adaptör). Testleri `CORE_ONLY=1 ./gradlew :core:test` ile Android SDK olmadan çalışır.
- `app/`: Jetpack Compose arayüzü ve Bluetooth bağlantısı.
- `design/`: ekran tasarımlarının kaynağı.
- `.github/workflows/build.yml`: testler, APK derleme ve "latest" sürümü.

## Plan

1. Adaptör ve bağlantı testi
2. Android uygulaması, standart veriler ← şu an burada
3. VW'ye özel veriler (yağ ve DSG sıcaklığı, turbo, klima)
4. Araca kalıcı ekran
5. Ayarlar / kodlama (örneğin konfor sinyali yanıp sönme sayısı), önce yedekleme ile

## Lisanslar

Sora yazı tipi SIL Open Font License 1.1 ile dağıtılır (Google Fonts). three.js MIT lisanslıdır (`app/src/main/assets/garage3d/THREE_LICENSE.txt`).

Garajdaki 3D araba modeli: "Volkswagen Golf 6" (https://sketchfab.com/3d-models/volkswagen-golf-6-46d9c43124b94f6293920944bef5b563), Nieve5677 (https://sketchfab.com/niev), CC BY 4.0 lisanslı. Uygulamada kapı, kaput ve bagaj açılsın diye parçalara ayrılıyor, motor bölmesi bizim çizimimiz. Ayrıntı: `app/src/main/assets/garage3d/golf6/license.txt`.
