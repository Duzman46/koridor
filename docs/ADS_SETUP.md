# Reklam kurulumu (AdMob)

Bu dosya, uygulamanın reklam tarafında **kod ile yapılamayan** işleri listeler. Kod tarafı
hazır; eksik olan tek şey AdMob Console'dan alınacak kimlikler.

## Şu an ne var?

| | Debug | Release |
|---|---|---|
| SDK | Google Mobile Ads 25.4.0 | aynı |
| Onay (consent) | UMP 4.0.0 | aynı |
| App ID | Google test App ID | `monetization.properties` → yoksa test ID |
| Banner | Google test banner ID (**her zaman**) | `monetization.properties` → yoksa test ID |
| Geçiş (interstitial) | Google test ID (**her zaman**) | `monetization.properties` → yoksa test ID |
| Ödüllü (rewarded) | yok | yok |

Debug derlemesi **hiçbir koşulda** gerçek reklam yüklemez — `app/build.gradle.kts` içindeki
`debug { }` bloğu ID'leri sabit test değerlerine bağlar. Bu, geliştirme sırasında kendi
reklamlarınıza tıklayıp hesabınızın kapatılmasını engeller.

## Benden istenen değerler

`monetization.properties` dosyasını proje kökünde oluşturun (`.gitignore` içinde, depoya
gitmez). `monetization.properties.example` şablon olarak kullanılabilir.

```properties
KORIDOR_ADMOB_APP_ID=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
KORIDOR_ADMOB_BANNER_ID=ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY
KORIDOR_ADMOB_INTERSTITIAL_ID=ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY
KORIDOR_PREMIUM_PRODUCT_ID=remove_ads
```

Üçü de dolu olmadan `BuildConfig.MONETIZATION_CONFIGURED` `false` kalır ve release derlemesi
reklam **göstermez** — eksik yapılandırmayla yayına çıkıp boş reklam alanı göstermek yerine
hiç göstermemeyi tercih eder.

CI kullanıyorsanız aynı isimlerle ortam değişkeni de okunur; dosya gerekmez.

## AdMob Console'da yapılacaklar

1. **Uygulamayı ekleyin** — Apps → Add app → Android → paket adı `com.duzman46.gridbound`.
   Uygulama henüz Play'de değilse "Not listed" seçilir; yayınlandıktan sonra Play kaydına
   bağlanmalıdır, yoksa gelir raporları eksik kalır.
2. **App ID'yi kopyalayın** (`ca-app-pub-...~...` — tilde işaretli olan).
3. **İki reklam birimi oluşturun:**
   - Banner → "Koridor Menu Banner"
   - Interstitial → "Koridor Match End"
   (Ödüllü reklam şu an uygulamada kullanılmıyor; oluşturmaya gerek yok.)
4. **Ödeme bilgilerini tamamlayın** (Payments → address + tax). Bu yapılmadan reklamlar
   gösterilse bile gelir tahakkuk etmez.
5. **Onay mesajı (GDPR/EEA):** Privacy & messaging → GDPR → mesajı oluşturup **yayınlayın**.
   Uygulama UMP ile bu mesajı çağırır; console'da yayınlanmış mesaj yoksa `canRequestAds()`
   Avrupa'da `false` döner ve reklam hiç gösterilmez.
6. **US states mesajı** (Privacy & messaging → US states) — ABD trafiği hedefleniyorsa.
7. **Test cihazı ekleyin** — release APK'yı kendi telefonunuzda denerken Settings → Test
   devices altına cihazınızın reklam kimliğini ekleyin. Aksi hâlde kendi gerçek reklamınıza
   bakmak geçersiz trafik sayılır.
8. **App-ads.txt** — Play listesindeki web sitesi alanı doluysa, o sitenin köküne AdMob'un
   verdiği `app-ads.txt` yüklenmelidir. Yoksa talep hacmi ciddi biçimde düşer.

## Çocuklara yönelik içerik

Uygulama **çocuklara yönelik olarak işaretlenmemiştir.** `MonetizationManager`
`setTagForUnderAgeOfConsent(false)` gönderir. Play Console'daki hedef kitle beyanı
"13 yaş üstü" dışında bir şey seçilirse burası ve AdMob'daki COPPA ayarı birlikte
güncellenmelidir.

## Uygulamadaki reklam davranışı

- **Banner:** yalnızca ana menüde, alt çubukta. Oyun tahtasında, öğreticide veya form
  ekranlarında banner yok.
- **Geçiş reklamı:** yalnızca maç bittikten sonra, kazanan ekranından çıkışta
  ("Tekrar oyna" / "Ana menü"). Hamle sırasında veya oyun devam ederken asla.
  Sıklık: **3 maçta bir** ve **art arda iki reklam arasında en az 3 dakika**
  (`MonetizationManager.MATCHES_PER_INTERSTITIAL`, `MIN_INTERSTITIAL_GAP_MILLIS`).
- **Ödüllü reklam:** yok. Eklenirse, oyun içi avantaj (ekstra duvar, geri alma vb.) **satmamalı**
  — mevcut kozmetik/reklamsızlık modeli pay-to-win'e dönüşür.
- **Reklam yüklenmezse** oyun hiçbir noktada beklemez; `onFinished` her yolda çağrılır.
- **Premium satın alındığında** (`Entitlement.REMOVE_ADS`) banner ve geçiş reklamı tamamen kapanır.

## Yayın öncesi kontrol

```bash
./gradlew :app:assembleRelease
```

komutundan sonra:

```bash
unzip -p app/build/outputs/apk/release/app-release.apk AndroidManifest.xml | strings | grep -o 'ca-app-pub-[0-9]*'
```

Çıkan publisher kimliği **sizin** AdMob hesabınızınki olmalı. `3940256099942544` görüyorsanız
hâlâ test ID'leri gömülüdür ve gelir üretmez.
