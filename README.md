# Gridbound

Gridbound, yol kurma ve duvar yerleştirme mekaniğine sahip özgün bir Android strateji oyunudur. İki oyuncu, 9×9 tahta üzerinde rakibin başladığı kenara ilk ulaşan taraf olmak için yarışır.

## Özellikler

- Aynı cihazda iki oyuncu
- Easy, Medium ve Hard yapay zekâ
- Resmî düz atlama ve engelli durumda çapraz atlama kuralları
- Her duvar öncesi iki oyuncu için BFS yol doğrulaması
- AI için A*, minimax, alpha-beta pruning ve evaluation function
- Canvas tabanlı responsive tahta, telefon ve tablet düzenleri
- Piyon/duvar animasyonları, konfeti, ses ve haptik geri bildirim
- Açık, koyu ve Android 12+ dinamik renk temaları
- DataStore tabanlı ayarlar ve istatistikler
- Hamle geçmişi, restart ve undo
- Tamamen Kotlin + Jetpack Compose + Material 3

## Teknik temel

- Android 17 / API 37, minimum API 26
- Kotlin 2.4.10
- Android Gradle Plugin 9.3.1, Gradle 9.6.1
- Compose BOM 2026.06.01
- MVVM, Clean Architecture sınırları, immutable state
- Hilt, Coroutines, StateFlow ve Preferences DataStore

## Mimari

```text
com.duzman46.gridbound
├── core
├── data
├── domain
├── presentation
├── navigation
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

## Derleme

Android Studio'nun gömülü JDK'sını kullanın:

```powershell
.\gradlew.bat :app:assembleDebug
```

Tam kalite kapısı:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:bundleRelease
```

## Test kapsamı

JVM testleri BFS, A*, normal hareket, düz atlama, çapraz atlama, duvar overlap/kesişim/yol kapatma, tur/zafer/undo ve üç AI seviyesini kapsar.

Detaylı teknik plan: [`docs/PROJECT_PLAN.md`](docs/PROJECT_PLAN.md)

