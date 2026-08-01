# Gridbound

Gridbound, yol kurma ve duvar yerleştirme mekaniğine sahip özgün bir Android strateji oyunudur. İki oyuncu, 9×9 tahta üzerinde rakibin başladığı kenara ilk ulaşan taraf olmak için yarışır.

## Özellikler

- Aynı cihazda iki oyuncu
- Oda koduyla internet üzerinden iki oyuncu
- Firebase anonim oturum, Realtime Database ve sıra/revizyon korumalı atomik hamleler
- Easy, Medium ve Hard yapay zekâ
- Resmî düz atlama ve engelli durumda çapraz atlama kuralları
- Her duvar öncesi iki oyuncu için BFS yol doğrulaması
- AI için A*, minimax, alpha-beta pruning ve evaluation function
- Canvas tabanlı responsive tahta, telefon ve tablet düzenleri
- Piyon/duvar animasyonları, konfeti, ses ve haptik geri bildirim
- Açık, koyu ve Android 12+ dinamik renk temaları
- DataStore tabanlı ayarlar ve istatistikler
- Hamle geçmişi, restart ve undo
- Büyük duvar dokunma alanı, belirgin önizleme ve onay/iptal akışı
- Tamamen Kotlin + Jetpack Compose + Material 3

## Teknik temel

- Android 17 / API 37, minimum API 26
- Kotlin 2.4.10
- Android Gradle Plugin 9.3.1, Gradle 9.6.1
- Compose BOM 2026.06.01
- MVVM, Clean Architecture sınırları, immutable state
- Hilt, Coroutines, StateFlow ve Preferences DataStore
- Firebase Android BoM 34.17.0, Authentication ve Realtime Database

## Mimari

```text
com.duzman46.gridbound
├── core
├── data
├── domain
├── presentation
├── navigation
├── online
│   ├── data
│   ├── domain
│   └── model
├── ui
├── game
│   ├── ai
│   ├── animation
│   ├── audio
│   ├── board
│   ├── engine
│   ├── models
│   ├── pathfinding
│   └── rules
├── di
├── theme
└── util
```

Oyun motoru UI kararlarından bağımsız saf Kotlin modelleri üzerinde çalışır. Compose yalnızca `GameUiState` çizer ve kullanıcı niyetlerini `GameViewModel` üzerinden motora iletir.

Çevrimiçi oyunlarda aynı saf kural motoru Firebase transaction içinde uygulanır. Oda güvenliği anonim kimlik, iki kişilik üyelik, aktif oyuncu kimliği ve artan revizyon numarasıyla korunur.

## Derleme

Android Studio'nun gömülü JDK'sını kullanın:

```powershell
.\gradlew.bat :app:assembleDebug
```

Tam kalite kapısı:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:bundleRelease
```

## Çevrimiçi servis

Uygulama `gridbound-duzman46` Firebase projesine bağlıdır. Bağlantı değerleri `firebase.properties`, Authentication/Realtime Database dağıtım tanımı `firebase.json`, erişim politikası ise `database.rules.json` içindedir.

Kuralları yeniden yayınlamak için Firebase CLI oturumuyla:

```powershell
firebase deploy --only auth,database
```

## Test kapsamı

JVM testleri BFS, A*, normal hareket, düz atlama, çapraz atlama, duvar overlap/kesişim/yol kapatma, geniş duvar dokunma hedefi, çevrimiçi durum serileştirme, tur/zafer/undo ve üç AI seviyesini kapsar.

Detaylı teknik plan: [`docs/PROJECT_PLAN.md`](docs/PROJECT_PLAN.md)
