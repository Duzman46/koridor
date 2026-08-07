# Koridor

Koridor, yol kurma ve duvar yerleştirme mekaniğine sahip özgün bir Android strateji oyunudur.
İki oyuncu, 9×9 tahta üzerinde rakibin başladığı kenara ilk ulaşan taraf olmak için yarışır.

## Özellikler

**Oyun**
- Aynı cihazda iki oyuncu, üç seviyeli yapay zekâ (Easy / Medium / Hard)
- Resmî düz atlama ve engelli durumda çapraz atlama kuralları
- Her duvar öncesi iki oyuncu için BFS yol doğrulaması
- AI için A*, minimax, alpha-beta pruning ve evaluation function
- Etkileşimli 7 adımlı öğretici — gerçek oyun tahtası ve gerçek kural motoru üzerinde çalışır
- Canvas tabanlı responsive tahta, telefon ve tablet düzenleri

**Hesap**
- Google ile giriş (Credential Manager), e-posta ile kayıt/giriş, şifre sıfırlama
- Misafir olarak oynama; internet olmadan bile açılır
- Misafir hesabını Google veya e-postaya bağlama — ilerleme korunur
- Kalıcı hesap silme

**Çevrim içi**
- Oda oluşturma: ad, görünürlük, dereceli/derecesiz, hamle ve oyuncu süresi, isteğe bağlı şifre
- Kodla katılma, açık oda listesi (filtrelerle), hızlı eşleşme, arkadaş daveti
- Bağlantı kesilince maça geri dönme; kısa kopmada yenilgi yok
- Süre aşımı, pes etme ve normal bitiş ayrı ayrı ele alınır
- Maç içi hazır mesajlar: sekiz ifade ve altı emoji, serbest metin yok, ayarlardan kapatılabilir

**Rekabet**
- ELO tabanlı puan sistemi (başlangıç 1000), sunucu tarafında uygulanır
- Genel, haftalık ve arkadaş liderlik tabloları, sayfalı yükleme
- Profilde son oyunlar: kendinin ve başkasının profilinde son 3 maç, dokununca 10'a çıkar

**Sosyal**
- Kullanıcı adıyla arama, arkadaşlık istekleri, engelleme, oyun davetleri
- `onDisconnect` tabanlı çevrim içi durumu

**Diğer**
- 10 dil, tam RTL desteği, TalkBack açıklamaları
- Google Play Billing ile reklamsız kullanım
- Açık, koyu ve sistemi izleyen tema seçenekleri

## Teknik temel

- Android 17 / API 37, minimum API 26
- Kotlin 2.4.10, Compose BOM 2026.06.01, AGP 9.3.1, Gradle 9.6.1
- MVVM, Clean Architecture sınırları, immutable state
- Hilt, Coroutines, StateFlow, Preferences DataStore
- Firebase BoM 34.17.0 — Authentication + **Realtime Database**
- Credential Manager 1.6.0, Play Billing 9.1.0, Mobile Ads 25.4.0, UMP 4.0.0
- Sunucu tarafı: zamanlanmış Cloudflare Worker (TypeScript); aynı işin Cloud Functions
  (Node 22) karşılığı da depoda duruyor

### Neden Firestore değil, Realtime Database?

Proje baştan RTDB üzerine kuruluydu ve kuralları yazılmıştı. Firestore'a geçmek çalışan
çevrim içi maçı bozardı; ikisini birlikte tutmak ise aynı işi yapan iki bağımlılık ve
~1,2 MB fazladan indirme demekti. RTDB ayrıca sıra tabanlı gerçek zamanlı senkronizasyon
ve `onDisconnect` tabanlı presence için daha uygundur — Firestore'da `onDisconnect` yoktur.
İstenen her özelliğin RTDB karşılığı kullanıldı: transaction, security rules, `.indexOn`,
`orderByChild` + `limitToLast` + `endBefore` ile sayfalama.

## Mimari

```text
com.duzman46.gridbound
├── core            UiText, AppError, Outcome, UsernameRules, AppLog
├── auth            domain/ + data/ (Firebase Auth, Credential Manager)
├── profile         domain/ + data/ (kullanıcı profili, kullanıcı adı benzersizliği)
├── session         SessionManager — kimlik, profil ve misafir kuralları
├── rating          EloCalculator (saf)
├── match           domain/ + data/ (maç raporu, idempotency)
├── leaderboard     domain/ + data/ (sayfalı liderlik tablosu)
├── social          domain/ + data/ (arkadaşlık, engelleme, davet, presence)
├── monetization    BillingManager, PurchaseVerifier, entitlement modeli
├── online          oda modeli, codec, Firebase repository
├── tutorial        adım tanımları ve saf öğretici motoru
├── game            ai, animation, audio, board, engine, models, pathfinding, rules
├── presentation    ViewModel'ler
├── ui              screens/, components/, game/, localization/
├── data            DataStore repository'leri, Firebase yardımcıları
├── navigation      AppNavigation
├── di              Hilt modülleri
└── theme
```

Oyun motoru UI'dan bağımsız saf Kotlin modelleri üzerinde çalışır. Çevrim içi maçlarda aynı
kural motoru Firebase transaction içinde uygulanır.

## Derleme

Gradle **JDK 17+** ister. Sistemde yalnızca Java 8 varsa `gradlew` "Gradle requires JVM 17
or later" hatası verir; Android Studio'nun gömülü JDK'sını gösterin:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

Kalıcı çözüm için `gradle.properties` içine `org.gradle.java.home` yazılabilir; bu dosya
depoda olduğu için makineye özgü yol eklenmedi.

```powershell
.\gradlew.bat :app:assembleDebug
```

Tam kalite kapısı:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:bundleRelease
```

---

## Kurulum

Aşağıdaki dört yapılandırma dosyası **git'e girmez** ve elle doldurulmalıdır. Her birinin
`.example` şablonu depoda vardır.

### 1. Firebase

`firebase.properties.example` → `firebase.properties`

```properties
GRIDBOUND_FIREBASE_API_KEY=...
GRIDBOUND_FIREBASE_APP_ID=...
GRIDBOUND_FIREBASE_PROJECT_ID=...
GRIDBOUND_FIREBASE_DATABASE_URL=https://PROJE-default-rtdb.BOLGE.firebasedatabase.app
GRIDBOUND_GOOGLE_WEB_CLIENT_ID=....apps.googleusercontent.com
```

**Adımlar:**

1. [Firebase Console](https://console.firebase.google.com/) → proje oluştur
2. **Authentication → Sign-in method** → şunları etkinleştir:
   - Anonymous (misafir oyuncular için — zorunlu)
   - Email/Password
   - Google
3. **Realtime Database** oluştur (bölge seç, kilitli modda başlat)
4. **Project settings → General → Your apps → Android** ile uygulamayı ekle
   (paket adı: `com.duzman46.gridbound`)
5. API key, App ID ve Project ID değerlerini oradan kopyala

**Google ile giriş için ek olarak (yapılmazsa Google butonu otomatik gizlenir):**

6. Uygulamanın **SHA-1** parmak izini Firebase'e ekle:
   ```powershell
   .\gradlew.bat signingReport
   ```
   Debug için `debug` variant'ının SHA-1'ini, yayın için **Play App Signing** sayfasındaki
   SHA-1'i ekleyin. Play imzalama kullanıyorsanız Play Console'un ürettiği sertifikanın
   SHA-1'i de eklenmelidir, aksi hâlde Google girişi yalnızca yerel derlemede çalışır.
7. **Authentication → Sign-in method → Google → Web SDK configuration** altındaki
   **Web client ID**'yi `GRIDBOUND_GOOGLE_WEB_CLIENT_ID` olarak yaz.

**Kuralları yayınla:**

```powershell
firebase login
firebase use PROJE_KIMLIGI
firebase deploy --only database
```

> **Önemli:** `firebase deploy --only database` **zorunludur**. Kurallar yayınlanmadan profil
> oluşturma, oda açma ve arkadaşlık çalışmaz.

**Sunucuyu yayınla.** Puanı, haftalık tabloyu, oda temizliğini, eşleştirme yedeğini ve tüm
zamanlar tablosunun dizinini hiçbir oyuncunun elinde olmayan bir taraf yazar. Depoda bunun iki
uygulaması var ve **yalnızca biri yayınlanır**:

- [`worker/`](worker/README.md) — dakikada bir çalışan, zamanlanmış bir Cloudflare Worker.
  Yayınlanan sürüm budur: ücretsiz planda çalışır, kart istemez ve dışarıya açık bir adresi
  yoktur. Kurulum adımları kendi README'sinde.
- `functions/` — aynı işin Cloud Functions karşılığı, her şeyi Firebase içinde tutmak
  isteyenler için. **Blaze (kullandıkça öde) planı** gerektirir.

İkisi de aynı maç raporlarını okur; ikisini birden yayınlama.

> Hiçbiri yayınlanmazsa uygulama çalışır ancak **puanlar hiç değişmez** — bu güvenli varsayılan
> davranıştır, çünkü istemci puan yazamaz. Worker bekleyen raporları yoklayarak bulduğu için
> ilk çalıştığında biriken raporları da işler; Cloud Functions sürümü yazma tetikleyicisiyle
> çalıştığından kendinden önce birikenleri geriye dönük işlemez.

### 2. Uygulama içi ürünler ve reklamlar

`monetization.properties.example` → `monetization.properties`

```properties
KORIDOR_ADMOB_APP_ID=ca-app-pub-.....~.....
KORIDOR_ADMOB_BANNER_ID=ca-app-pub-...../.....
KORIDOR_ADMOB_INTERSTITIAL_ID=ca-app-pub-...../.....
KORIDOR_PREMIUM_PRODUCT_ID=remove_ads
KORIDOR_THEME_MIDNIGHT_PRODUCT_ID=
KORIDOR_THEME_SUNSET_PRODUCT_ID=
```

**Play Console adımları:**

1. **Monetize → Products → In-app products** → yönetilen ürün oluştur
2. Ürün kimliğini (`remove_ads` gibi) ilgili satıra yaz
3. Tema ürünlerini **oluşturana kadar boş bırakın** — boş bırakılan ürün mağazada hiç
   görünmez. Uydurma bir kimlik yazmayın; Play satın almayı reddeder.
4. Test için **License testing** listesine hesabınızı ekleyin.

**Sunucu tarafı satın alma doğrulaması** (`functions/src/purchases.ts`) için:

5. Google Cloud Console → **Google Play Android Developer API**'yi etkinleştir
6. Play Console → **Users and permissions** → fonksiyonun servis hesabına
   *"View financial data"* yetkisi ver

Bu yapılmadan da satın almalar cihazda çalışır (Play'in imzalı yanıtıyla), ancak cihazlar
arası kalıcı entitlement yazılmaz.

### 3. Yasal bağlantılar

`app.properties.example` → `app.properties`

```properties
KORIDOR_PRIVACY_POLICY_URL=
KORIDOR_TERMS_URL=
```

Boş bırakılırsa uygulama ilgili bağlantıyı **gizler** — hatalı bir adrese yönlendirmez.
Play Store yayını için gizlilik politikası URL'si **zorunludur**. Metin taslakları
`docs/PRIVACY_POLICY_TR.md` ve `docs/PRIVACY_POLICY_EN.md` dosyalarındadır.

### 4. İmzalama

`keystore.properties.example` → `keystore.properties`

```properties
storeFile=../koridor-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Dört değer de doluysa release derlemesi otomatik imzalanır; boşsa imzasız üretilir.

### Google Play Games Services

Kurulmadı. Kendi liderlik tablomuz RTDB üzerinde çalışır ve arkadaşlık/profil sistemi
Play Games profiline bağımlı değildir. Play Games leaderboard'u ileride **isteğe bağlı ek**
olarak eklenebilir; bunun için `play-services-games-v2` bağımlılığı ve maç sonucunda
`submitScore` çağrısı yeterlidir. Şu an eklenmemesinin nedeni ~1 MB indirme maliyeti ve
ikinci bir kimlik sistemine bağımlılık yaratmamasıdır.

---

## Test

```powershell
.\gradlew.bat :app:testDebugUnitTest      # JVM birim testleri
npm --prefix rules-tests test             # Realtime Database kural testleri (emülatör)
cd worker; npm test; npm run test:e2e     # sunucu: birim + emülatöre karşı uçtan uca
cd functions; npm test                    # Cloud Functions karşılığının testleri
```

Kural testleri ve worker'ın uçtan uca testi Firebase CLI ile bir JDK gerektirir; emülatörü
kendileri başlatıp durdurur.
Ayrıntı: [`docs/FIREBASE_SETUP.md`](docs/FIREBASE_SETUP.md).

JVM testleri; kullanıcı adı kuralları ve Türkçe locale normalizasyonu, ELO hesaplama,
maç idempotency, oda durum geçişleri ve saatler, arkadaşlık durum makinesi ve engelleme,
satın alma durum eşlemesi, misafir hesap bağlama, hesap silme, öğretici adımları (gerçek
kural motoruna karşı), BFS/A*, hareket ve duvar doğrulama, çevrim içi serileştirme ve üç AI
seviyesini kapsar.

Cihazda doğrulanması gereken senaryolar: [`docs/MANUAL_TESTS.md`](docs/MANUAL_TESTS.md)

## Güvenlik modeli

- `rating`, `wins`, `losses`, `draws`, `totalGames`, `highestRating`, seriler ve
  `purchasedEntitlements` alanlarının **istemci yazma kuralı yoktur**. Yalnızca Admin SDK
  kullanan Cloud Functions yazabilir.
- Liderlik tablosu `rating` ile değil, `leaderboardRating` ile sıralanır: aynı sayının yalnızca
  hesabını bağlamış oyuncuda bulunan bir kopyası. Kural bu kopyayı `rating` ile birebir eşit
  olmaya zorlar ve `GUEST` profiline yazdırmaz; `/users` üzerinde izin verilen tek sorgu da bu
  dizin üzerindendir. Haftalık tabloya ise sunucu misafir için hiç satır yazmaz. Misafir
  tablodan **süzülmez**, dizinde hiç bulunmaz; hesabını bağladığı gün kazandığı puanla girer.
  Kopyayı üç el yazar: puanlı maçtan sonra sunucu, hesabını bağlayan istemcinin kendisi ve
  profil ağacını sayfa sayfa gezen sunucu taraması. Sonuncusu şarttır: dizin eklenmeden önce
  yazılmış hesaplara uzanabilen tek el odur — bir telefon yalnızca kendi profiline yazabilir —
  ve dizinde değeri olmayan bir profil tablonun sonunda değil, tablonun tamamen dışındadır.
- Maç raporu, odanın kendi `winnerUserId` ve `endReason` alanlarıyla **birebir eşleşmek
  zorundadır**; oda kuralları bu değerleri tahtaya (normal bitiş) veya sunucu saatine
  (süre aşımı) karşı doğrular.
- `matchId = roomCode_createdAt` ve write-once kural ile aynı maç iki kez puanlanamaz.
- Profildeki "son oyunlar" listesi `recentMatches/{uid}` altındadır ve **hiçbir istemcinin
  yazma kuralı yoktur**; yalnızca sunucu yazar, giriş yapmış her oyuncu okuyabilir. Herkese
  açık bir profilde duran bir geçmişi telefon yazsaydı, telefon düzenleyebilirdi: yenilgiyi
  hiç bildirmemekle gerçekten bildirmemek arasındaki farkı veritabanı göremez. Liste on maçta
  tutulur, en eskisi düşer.
- Oda şifresi hash'i, hiçbir istemcinin okuyamadığı `roomSecrets/{code}` altındadır;
  karşılaştırmayı kural yapar.
- Maç içi mesajlarda **serbest metin yoktur ve olmamalıdır**. Kural, `rooms/{code}/chat/{uid}`
  altına yalnızca uygulamadaki sabit listenin anahtarlarından birini kabul eder; başka bir alan
  eklenemez, bir oyuncu diğerinin adına yazamaz ve ardışık iki mesaj arasında en az üç saniye
  olmak zorundadır — zaman damgasını sunucu koyar, telefon değil. Kapalı liste yalnızca
  sadelik değil: serbest metin uygulamayı Play'in kullanıcı içeriği yükümlülükleri kapsamına
  sokar. Mesajlar odanın içinde durur, odayla birlikte silinir.
- Engelleme sunucuda uygulanır: engellenen kullanıcı karşı tarafın düğümüne yazamaz.
- E-posta adresleri `usersPrivate/{uid}` altındadır ve yalnızca sahibi okuyabilir.
- Release derlemesinde debug logları R8 tarafından tamamen kaldırılır.

Ayrıntılı veri beyanı: [`docs/DATA_SAFETY.md`](docs/DATA_SAFETY.md)

## Belgeler

- [`docs/FIREBASE_SETUP.md`](docs/FIREBASE_SETUP.md) — Firebase Console adımları, App Check, emülatör ve kural testleri
- [`docs/ADS_SETUP.md`](docs/ADS_SETUP.md) — AdMob Console adımları, gerekli kimlikler, reklam davranışı
- [`docs/DATA_SAFETY.md`](docs/DATA_SAFETY.md) — veri toplama ve Play Data Safety formu
- [`docs/MANUAL_TESTS.md`](docs/MANUAL_TESTS.md) — manuel test senaryoları
- [`docs/RELEASE.md`](docs/RELEASE.md) — yayın adımları
- [`docs/PROJECT_PLAN.md`](docs/PROJECT_PLAN.md) — teknik plan
