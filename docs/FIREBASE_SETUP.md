# Firebase kurulumu

Kod, kurallar, indeksler, Cloud Functions ve emülatör yapılandırması depoda hazır. Bu dosya
yalnızca **hesap erişimi gerektiren**, koddan yapılamayan işleri listeler.

Proje: **Realtime Database** kullanır, Firestore kullanmaz. Gerekçe README'de.

## 1. Yapılandırma dosyası

`firebase.properties` proje kökünde, `.gitignore` içinde. `firebase.properties.example`
şablondur.

```properties
GRIDBOUND_FIREBASE_API_KEY=
GRIDBOUND_FIREBASE_APP_ID=
GRIDBOUND_FIREBASE_PROJECT_ID=
GRIDBOUND_FIREBASE_DATABASE_URL=
GRIDBOUND_GOOGLE_WEB_CLIENT_ID=
GRIDBOUND_APP_CHECK_ENABLED=false
```

Değerler Firebase Console → Project settings → Your apps → Android → `google-services.json`
içindedir. **`google-services.json` dosyasının kendisi projeye eklenmez**; uygulama
`FirebaseOptions` ile elle kurulur, böylece anahtarlar tek bir git-ignored dosyada kalır.

`GRIDBOUND_GOOGLE_WEB_CLIENT_ID`, Google ile giriş için gereken **Web client (auto created by
Google Service)** OAuth istemci kimliğidir — Android istemcisi değil. Boş bırakılırsa Google
ile giriş düğmesi çalışmaz; e-posta ve misafir girişi çalışmaya devam eder.

## 2. Console'da yapılacaklar

| # | İş | Nerede |
|---|---|---|
| 1 | Android uygulamasını ekleyin, paket adı `com.duzman46.gridbound` | Project settings → Your apps |
| 2 | **SHA-1 ve SHA-256** parmak izlerini ekleyin (aşağıdaki komut) | aynı ekran |
| 3 | Authentication → Sign-in method: **Anonymous**, **Email/Password**, **Google** açın | Authentication |
| 4 | Realtime Database oluşturun, konum **europe-west1** | Realtime Database |
| 5 | Blaze planına geçin (Cloud Functions için zorunlu) | Usage & billing |
| 6 | App Check → Play Integrity kaydı (bkz. §4) | App Check |
| 7 | Play Console → API access: Cloud Functions'ın satın alma doğrulaması için hizmet hesabına **Financial data / View orders** yetkisi | Google Play Console |

### SHA parmak izleri

Upload keystore'unuz için:

```bash
keytool -list -v -alias koridor-upload -keystore "C:/Users/furka/Documents/Uygulamalarım/Koridor/private/koridor-upload.jks"
```

Play App Signing kullanıyorsanız **ayrıca** Play Console → Setup → App signing altındaki
"App signing key certificate" SHA-1 ve SHA-256 değerlerini de eklemeniz gerekir. Bu adım
atlanırsa Google ile giriş yalnızca yerel derlemede çalışır, Play'den inen sürümde çalışmaz.

## 3. Kuralları ve fonksiyonları dağıtma

```bash
firebase deploy --only database
```

```bash
firebase deploy --only functions
```

`database.rules.json` production kuralıdır; kökte `.read`/`.write` `false`'tur, test modu
açık bırakılmamıştır. Rating, galibiyet sayıları, satın alma hakları ve maç sonuçları
istemci tarafından yazılamaz — bunların tamamı Cloud Functions içinde Admin SDK ile yazılır.
Fonksiyonlar dağıtılmamışsa sistem **güvenli tarafa düşer**: puanlar hiç değişmez.

## 4. App Check

Uygulamada hazır ama **kapalı**. Açma sırası önemlidir; ters sırada yaparsanız tüm cihazlar
backend'den kilitlenir.

1. Firebase Console → App Check → Apps → Android uygulamasını seçin → **Play Integrity**'yi
   kaydedin.
2. `firebase.properties` içinde `GRIDBOUND_APP_CHECK_ENABLED=true` yapın ve bir derleme alın.
3. Debug derlemesi çalıştırıldığında logcat'te `DebugAppCheckProvider` etiketiyle bir token
   basılır. Bunu App Check → Apps → ⋮ → **Manage debug tokens** altına ekleyin.
4. App Check → APIs → Realtime Database → **Metrics**'te doğrulanmamış istek oranının sıfıra
   inmesini bekleyin (kullanıcıların güncellemeyi alması birkaç gün sürer).
5. Ancak o zaman **Enforce**'a basın.

Release derlemesinde debug sağlayıcısı yoktur — `app/src/debug` ve `app/src/release` altında
ayrı `AppCheckProviders` dosyaları vardır, debug kütüphanesi release classpath'ine hiç girmez.

## 5. Emülatör ve kural testleri

```bash
npm install -g firebase-tools
```

```bash
firebase emulators:start
```

Emülatör portları `firebase.json` içinde sabittir: auth 9099, database 9000, functions 5001,
UI 4000. Veritabanı emülatörü için makinede bir **JDK** bulunmalıdır.

Güvenlik kuralı testleri:

```bash
npm --prefix rules-tests install
```

```bash
npm --prefix rules-tests test
```

Bu komut emülatörü kendisi başlatıp durdurur. Testler `database.rules.json` dosyasını
doğrudan okur, yani kural dosyasında yapılan bir gevşetme testte yakalanır.

Cloud Functions birim testleri ayrıdır ve emülatör gerektirmez:

```bash
npm --prefix functions test
```

## 6. Eklenmeyenler ve nedeni

- **Firestore** — proje RTDB üzerine kurulu; ikisini birlikte tutmak aynı işi yapan iki
  bağımlılık ve ~1,2 MB fazladan indirme demek. `onDisconnect` tabanlı presence Firestore'da
  yok.
- **Crashlytics / Analytics** — eklenmedi. Her ikisi de `google-services` Gradle eklentisini
  ve `google-services.json` dosyasını zorunlu kılar; bu proje anahtarları bilerek tek bir
  git-ignored properties dosyasında tutuyor. İstenirse ayrı bir iş olarak eklenebilir,
  yaklaşık +600 KB.
- **FCM** — uygulamada bildirim yok. Arkadaş davetleri RTDB dinleyicisiyle, uygulama açıkken
  gösteriliyor. Kapalıyken bildirim istenirse FCM + bir Cloud Function gerekir.
