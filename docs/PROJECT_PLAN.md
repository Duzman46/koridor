# Gridbound — Android Oyun Proje Planı

Sürüm: 1.0  
Tarih: 1 Ağustos 2026  
Durum: Uygulama öncesi onaylanmış teknik plan

## 1. Ürün tanımı

**Gridbound**, 9×9 kareli bir tahtada iki piyonun karşı kenara ulaşmak için yarıştığı; oyuncuların hareket etmek veya rakibin yolunu uzatan duvarlar yerleştirmek arasında seçim yaptığı, özgün markalı bir Android strateji oyunudur.

Ürün, Quoridor kurallarından ilham alan mekanikleri eksiksiz uygular; ancak ad, ikonografi, görseller, sesler ve mağaza metinleri özgün olur. Böylece üçüncü taraf marka ve görsel varlıklarına bağımlılık yaratılmaz.

İlk sürüm kapsamı:

- Aynı cihazda iki oyuncu modu
- Yapay zekâya karşı oyun
- Easy, Medium ve Hard zorlukları
- Resmî hareket, atlama, çapraz atlama ve duvar kuralları
- Açık, koyu ve sistem/dinamik renk temaları
- Ses, titreşim, animasyon, hamle geçmişi ve yeniden başlatma
- DataStore tabanlı ayarlar ve istatistikler
- Telefon, katlanabilir cihaz ve tablet uyumu
- Kritik oyun motoru ve AI birim testleri

## 2. Teknik taban

İlk Gradle tesliminde kullanılacak taban:

| Alan | Karar |
|---|---|
| Dil | Yalnızca Kotlin |
| UI | Jetpack Compose + Material 3; XML layout yok |
| Minimum Android | API 26 |
| Compile / Target SDK | API 37 (Android 17) |
| Java toolchain | JDK 17 |
| Build sistemi | Kotlin DSL, AGP 9.3.x, Gradle 9.5.x |
| Kotlin | 2.3.21 uyum çizgisi |
| Compose | Kararlı BOM 2026.06.00 |
| DI | Hilt |
| Eşzamanlılık | Coroutines + StateFlow |
| Kalıcılık | Preferences DataStore |
| Test | JUnit, kotlinx-coroutines-test, Compose UI testleri |

Not: Android paketleme sistemi için zorunlu olan minimal `AndroidManifest.xml`, “XML kullanılmayacak” kuralının teknik istisnasıdır. Hiçbir ekran, tema veya görünüm XML ile tanımlanmayacak; UI bütünüyle Compose olacaktır.

## 3. Mimari yaklaşım

Clean Architecture bağımlılık yönü korunur:

`presentation/ui → domain ← data`

`presentation/ui → game engine`

`data → domain sözleşmeleri`

Oyun motoru Android sınıflarından bağımsız, saf Kotlin olarak tasarlanır. Böylece kural motoru JVM birim testlerinde hızlı ve deterministik biçimde çalışır. Compose yalnızca `GameUiState` çizer ve kullanıcı niyetlerini `GameAction` olarak ViewModel'e iletir.

### Modüller

- `:app`: Application, Activity, Hilt başlangıcı, ana navigasyon
- `:core:common`: Constants, dispatcher niteleyicileri, genel yardımcılar
- `:core:model`: Uygulama genelinde paylaşılan immutable modeller
- `:domain`: Repository sözleşmeleri, ayar/istatistik use-case'leri
- `:data`: DataStore, repository uygulamaları, ayar ve istatistik yöneticileri
- `:game:engine`: Tahta, kurallar, doğrulayıcılar, BFS, A*, tur ve oyun motoru
- `:game:ai`: Easy, Medium, Hard AI ve değerlendirme sistemi
- `:feature:home`: Splash, ana menü, mod ve zorluk seçimi
- `:feature:game`: GameViewModel, Canvas tahta, kontroller, animasyon ve ses
- `:feature:result`: Kazanan ekranı ve tekrar oynama akışı
- `:feature:settings`: Tema, ses ve istatistik ekranları
- `:core:testing`: Test fixture'ları ve deterministik test yardımcıları

Namespace kökü `com.projectname.gridbound` olur. Talep edilen `core`, `data`, `domain`, `presentation`, `ui`, `navigation`, `game`, `util`, `di` ve `theme` ayrımları ilgili modüllerin paketlerinde korunur.

## 4. Temel oyun modeli

Tüm domain modelleri immutable `data class`, `enum class` veya sealed hierarchy olarak yazılır.

- `Position(row, column)`: Her eksende 0..8 aralığı doğrulanan kare
- `PlayerId`: `PLAYER_ONE`, `PLAYER_TWO`
- `Player`: kimlik, piyon konumu, kalan duvar sayısı, hedef satır
- `Pawn`: oyuncu kimliği ve konum
- `Wall`: 0..7 aralığındaki anchor ve `HORIZONTAL` / `VERTICAL` yönü
- `Board`: boyut ve sabit topoloji bilgisi
- `BoardState`: iki oyuncu, yerleştirilmiş duvarlar ve aktif oyuncu
- `GameAction`: `MovePawn`, `PlaceWall`, `Restart`, `ReturnHome`, `ToggleWallMode`, `SelectPawn`
- `GameEvent`: ses, haptik, hata, navigasyon ve zafer gibi tek seferlik olaylar
- `GameStatus`: `IN_PROGRESS`, `PLAYER_ONE_WON`, `PLAYER_TWO_WON`
- `GameMode`: `LOCAL_TWO_PLAYER`, `VS_AI`
- `Difficulty`: `EASY`, `MEDIUM`, `HARD`
- `TurnRecord`: sıra numarası, oyuncu, aksiyon, önceki ve sonraki durum

`BoardState` tek doğruluk kaynağıdır. UI tarafından değiştirilemez. Her geçerli aksiyon yeni bir durum üretir; geçersiz aksiyon mevcut durumu değiştirmez.

## 5. Kural motoru

### Piyon hareketi

`MoveValidator`, aktif oyuncu için geçerli hedefleri aşağıdaki sırayla üretir:

1. Dört ortogonal komşuyu tahta sınırı ve duvar engeli bakımından kontrol eder.
2. Komşu kare boşsa normal hamle olarak ekler.
3. Komşuda rakip varsa, rakibin arkasındaki kare aynı yönde erişilebilirse düz atlamayı ekler.
4. Rakibin arkası tahta kenarı veya duvarla kapalıysa, rakibin iki yanındaki erişilebilir kareleri resmî çapraz atlama hedefleri olarak ekler.
5. Rakibin arkası açıksa çapraz hedef üretmez.
6. Çapraz hedefe rakibin karesinden geçişi engelleyen yan duvar varsa o hedefi reddeder.

UI, geçerli kareleri doğrudan bu kümeden alır; böylece çizim ile motor kuralları birbirinden sapmaz.

### Duvar yerleştirme

Bir duvar anchor'ı iki kare uzunluğunda engel oluşturur:

- Yatay duvar iki dikey geçiş kenarını kapatır.
- Dikey duvar iki yatay geçiş kenarını kapatır.

`WallValidator` şu kontrolleri sırasıyla yapar:

1. Aktif oyuncunun en az bir duvarı var mı?
2. Anchor 0..7 aralığında mı?
3. Aynı duvar daha önce yerleştirilmiş mi?
4. Aynı yöndeki başka duvarla tek segment dahi üst üste geliyor mu?
5. Aynı anchor'daki ters yönlü duvarla kesişiyor mu?
6. Duvar geçici olarak eklendiğinde Player 1 hedef satırına ulaşabiliyor mu?
7. Aynı geçici durumda Player 2 hedef satırına ulaşabiliyor mu?

Son iki kontrol `BFSValidator` ile yapılır. İki yol da mevcut değilse işlem atomik olarak reddedilir ve duvar sayısı azalmaz.

### Zafer ve tur

- Player 1, satır 0'a ulaştığında kazanır.
- Player 2, satır 8'e ulaştığında kazanır.
- `VictoryChecker` her başarılı piyon hamlesinden hemen sonra çalışır.
- Zafer durumunda tur değiştirilmez ve yeni hamle kabul edilmez.
- Başarılı, zaferle bitmeyen aksiyonda `TurnManager` aktif oyuncuyu değiştirir ve tur sayısını artırır.

## 6. Pathfinding

### BFSValidator

- Amaç: Bir oyuncunun herhangi bir hedef karesine ulaşabildiğini kanıtlamak.
- Veri yapıları: sabit boyutlu ziyaret dizisi ve kuyruk.
- Karmaşıklık: 81 düğüm ve sınırlı kenar sayısı nedeniyle `O(V + E)`.
- Piyonlar BFS için engel sayılmaz; yol varlığı duvar topolojisine göre değerlendirilir.
- Duvar doğrulamasında her aday için iki kez çalışır.

### AStarPathFinder

- Amaç: Oyuncunun hedef satırına en kısa yolunu ve mesafesini bulmak.
- Heuristic: hedef satıra Manhattan satır uzaklığı; admissible ve consistent.
- Sonuç: `PathResult(path, distance)` veya `NoPath`.
- AI değerlendirmesi ve Medium AI hareket kararı tarafından kullanılır.
- Eşit maliyetli yollar deterministik tie-break kuralıyla sıralanır.

## 7. Yapay zekâ planı

AI çalışması ana thread dışında `Dispatchers.Default` üzerinde yürür. AI düşünürken kullanıcı girdisi kilitlenir; oyun yeniden başlatılırsa aktif hesaplama iptal edilir.

### EasyAI

- Geçerli piyon hareketleri ve makul geçerli duvar adaylarından seçim yapar.
- Enjekte edilen `Random` kullanır; testlerde seed sabitlenebilir.
- Duvar kullanma olasılığı sabit ve `Constants` içinde tanımlıdır.
- Kazandıran doğrudan hamle varsa rastgelelikten önce seçilir.

### MediumAI

- Kendi A* yolundaki ilk hamleyi temel aday yapar.
- Rakibin kısa yoluna temas eden sınırlı duvar adaylarını üretir.
- Her aday için kendi ve rakip mesafe farkını ölçer.
- Kendisini orantısız yavaşlatmayan, rakibin yolunu en çok uzatan aksiyonu seçer.
- Kazandıran hamle ve acil rakip tehdidi önceliklidir.

### HardAI

- Minimax + alpha-beta pruning kullanır.
- Transposition table için kanonik `BoardState` hash'i kullanır.
- Önce kazandıran hamleler, sonra yüksek etkili duvarlar, sonra A* yönündeki hamleler sıralanır.
- Branching factor'ı kontrol etmek için duvar adayları iki oyuncunun en kısa yolları çevresiyle sınırlandırılır; her aday yine tam `WallValidator` kontrolünden geçer.
- Derinlik, süre bütçesi ve değerlendirilen düğüm sınırı `Constants` içinde tutulur.
- Süre dolarsa son tamamlanan derinliğin sonucu döndürülür; sonuç her zaman geçerli aksiyondur.

Değerlendirme bileşenleri:

- Kendi hedef mesafesi
- Rakibin hedef mesafesi
- Kalan duvar farkı
- Önceki pozisyona göre rakibi yavaşlatma
- Kendi yolunu hızlandırma
- Bir hamlede kazanma / kaybetme tehdidi
- Terminal kazanma ve kaybetme skorları

## 8. Durum yönetimi ve veri akışı

`GameViewModel` aşağıdakileri birleştirir:

- `StateFlow<GameUiState>`: ekranın kalıcı ve yeniden çizilebilir tüm durumu
- `SharedFlow<GameEvent>`: ses, titreşim, snackbar ve navigasyon gibi tek seferlik etkiler
- `SavedStateHandle`: seçilen mod ve zorluk gibi süreç ölümü sonrası gerekli küçük bilgiler

Akış:

`TouchController → GameAction → GameViewModel → GameManager/GameEngine → yeni BoardState → GameUiState → Compose`

GameViewModel kural hesaplaması yapmaz; use-case ve engine çağrılarını koordine eder. `GameRepository`, ayarları ve istatistikleri domain'e Flow olarak sunar.

## 9. Ekran ve navigasyon planı

Navigasyon rotaları tip güvenli olur:

1. `Splash`
2. `MainMenu`
3. `ModeSelection`
4. `DifficultySelection` — yalnızca AI modu
5. `Game`
6. `Winner`
7. `Settings`
8. `Statistics`

Geri davranışı:

- Oyun ekranından çıkış onay ister.
- Winner ekranındaki tekrar oyna aynı ayarlarla yeni oyun başlatır.
- Ana menü seçeneği oyun state'ini temizler.
- Splash yalnızca ayarları yükler ve kısa logo animasyonu gösterir; yapay gecikme eklemez.

## 10. UI/UX planı

### Oyun ekranı

- Üst bölüm: aktif oyuncu, tur sayısı, home, restart ve settings
- Orta bölüm: kare aspect-ratio'lu Canvas tahta
- Alt bölüm: oyuncuların kalan duvarları, hareket/duvar modu seçimi, yatay/dikey yön seçimi ve hamle geçmişi
- Dar telefonlarda alt kontroller kompakt; geniş ekranda tahta ve bilgi paneli yan yana
- Minimum dokunma hedefi 48 dp; renk dışında şekil/ikon ile de durum anlatımı

### CanvasRenderer

- Tahta ölçüsü mevcut constraint'ten hesaplanır.
- Kare, koridor ve duvar kalınlıkları tek geometri nesnesinde önceden hesaplanır.
- Çizim sırası: zemin, kareler, hedef vurguları, yerleşik duvarlar, duvar önizlemesi, piyonlar, seçim/parlama efektleri.
- Touch koordinatları aynı geometri nesnesiyle `Position` veya `Wall` anchor'ına dönüştürülür.
- Görsel katman hiçbir kural kararı vermez.

### Kontrol davranışı

- Piyona dokununca geçerli hedefler yeşil vurgu ile gösterilir.
- Sadece bu hedeflere dokunma hamle üretir.
- Duvar modunda yakın anchor ve yön hesaplanır.
- Geçerli önizleme yeşil, geçersiz önizleme kırmızı olur.
- Tek dokunuş geçerli duvarı yerleştirir; geçersiz dokunuş hata sesi/haptik üretir.

### Tema ve erişilebilirlik

- Açık, koyu ve sistem teması
- Android 12+ dinamik renk; eski sürümlerde özgün fallback paleti
- Yüksek kontrastlı piyonlar ve duvarlar
- Renk körlüğüne karşı farklı piyon şekilleri ve sembolleri
- Ekran okuyucu için semantik kare açıklamaları ve kontrol etiketleri
- Sistem “animasyonları azalt” ayarına uyum

## 11. Animasyon, haptik ve ses

- Piyon: önceki ve yeni kare arasında `Animatable` ile kayma
- Duvar: alpha + scale ile yerleşme
- Geçersiz işlem: kısa shake ve hata haptik geri bildirimi
- Zafer: Canvas parçacıklarıyla özgün konfeti, parlama ve başarı haptik deseni
- Sesler: telifsiz üçüncü taraf dosya yerine uygulama için özgün üretilmiş kısa ses varlıkları
- `SoundManager`: lifecycle uyumlu yükleme, oynatma, sessize alma ve kaynak temizleme
- Ses ve haptik efektler state içinde tutulmaz; `GameEvent` olarak tüketilir

## 12. DataStore ve istatistik

Kaydedilecek ayarlar:

- Tema modu
- Dinamik renk tercihi
- Ses açık/kapalı
- Haptik açık/kapalı
- Son seçilen AI zorluğu

Kaydedilecek istatistikler:

- Toplam tamamlanan oyun
- AI karşısında galibiyet ve mağlubiyet
- Zorluk bazında galibiyet/mağlubiyet
- Yerel iki oyunculu tamamlanan oyun
- Toplam oynanan tur

Yazmalar IO dispatcher'da ve atomik DataStore güncellemesiyle yapılır. Bozuk veya eksik değerler güvenli varsayılanlara düşer.

## 13. Performans planı

- GameUiState ve domain modelleri immutable tutulur.
- Büyük BoardState yalnızca geçerli aksiyonda yenilenir.
- Compose'a ViewModel değil, state ve callback'ler geçirilir.
- `collectAsStateWithLifecycle`, `remember`, `derivedStateOf` ve kararlı parametreler kullanılır.
- Canvas geometri ve Path nesneleri ölçü değişmedikçe yeniden oluşturulmaz.
- AI ve pathfinding ana thread dışında çalışır.
- UI için frame başına pathfinding veya allocation yapılmaz.
- Release build'de R8 optimizasyonu ve resource shrinking etkinleştirilir.
- Macrobenchmark zorunlu ilk kapsam dışında olsa da gerçek cihazda frame timeline ve recomposition kontrolü teslim kapısına eklenir.

## 14. Hata güvenliği

- Dışarıdan alınan position ve wall anchor'ları oluşturulurken doğrulanır.
- Koleksiyon erişimlerinde ham index yerine doğrulanmış `Position` kullanılır.
- Engine aksiyon sonucu `Success` veya açıklanabilir `InvalidAction` döndürür; beklenen kullanıcı hataları exception değildir.
- AI sonucu motor tarafından tekrar doğrulanır; geçersiz sonuçta güvenli ilk geçerli hamleye düşülür.
- Aynı anda çift dokunma mutex/işlem durumu ile engellenir.
- Navigasyon ve tek seferlik event'ler tekrarlı tüketilmeye karşı korunur.
- Null ile durum ifade etmek yerine sealed durumlar kullanılır.

## 15. Test stratejisi

### Kural testleri

- Başlangıç konumları ve hedef satırlar
- Dört normal hareket ve tüm sınır durumları
- Duvar arkasından geçememe
- Düz atlama
- Arka duvar nedeniyle sol/sağ çapraz atlama
- Tahta kenarı nedeniyle çapraz atlama
- Arka açıkken çaprazın yasak olması
- Yan duvar nedeniyle tek çaprazın kapanması
- Tur ve zafer geçişleri

### Duvar testleri

- Sınırdaki tüm geçerli anchor'lar
- Tahta dışı anchor reddi
- Aynı duvar, segment overlap ve merkez kesişim reddi
- Uç uca temasın kabulü
- Duvar kalmadığında ret
- Tek oyuncuyu veya iki oyuncuyu tamamen kapatan duvarın reddi
- Reddedilen duvarda state ve sayaçların değişmemesi

### Pathfinding testleri

- Açık tahtada doğru en kısa mesafe
- Zikzak duvarlarda beklenen yol
- Birden fazla eşit yolda deterministik sonuç
- BFS erişilebilir/erişilemez fixture'ları
- A* sonucu ile referans BFS mesafesinin property-style örneklerde eşleşmesi

### AI testleri

- Easy yalnızca geçerli aksiyon üretir
- Medium doğrudan kazandıran hamleyi seçer
- Medium kritik rakip yolunu uzatan duvarı seçer
- Hard bir hamlede kazanır ve bir hamlede kaybı savunur
- Alpha-beta sonucu küçük ağaçta tam minimax ile eşleşir
- Süre bütçesi dolduğunda geçerli fallback döner
- Sabit seed ile deterministik test

### UI ve entegrasyon testleri

- Ana menüden iki oyun moduna navigasyon
- Piyon seçimi ve hedef vurguları
- Geçerli/geçersiz duvar önizlemesi
- Restart, home ve winner akışları
- Tema ve ses ayarlarının yeniden açılışta korunması
- Telefon ve tablet boyutlarında temel screenshot/layout kontrolleri

## 16. Uygulama ve teslim sırası

Her faz tamamlandığında derleme ve ilgili testler çalıştırılır; başarısız kalite kapısıyla sonraki faza geçilmez.

### Faz 1 — Gradle ve proje iskeleti

- Settings, version catalog, root ve modül Gradle Kotlin DSL dosyaları
- API 37 / minSdk 26, Kotlin, Compose, Hilt, DataStore ve test yapılandırması
- Minimal manifest, Application ve MainActivity
- Boş Compose ekranıyla debug build doğrulaması

### Faz 2 — Paket ve modül yapısı

- Tüm modül kaynak/test dizinleri
- Namespace'ler ve bağımlılık sınırları
- `Constants.kt` içinde sorumluluğa göre iç içe sabit grupları
- Hilt dispatcher ve repository modülleri

### Faz 3 — Saf oyun modelleri

- Board, Tile, Position, Pawn, Player, Wall, BoardState
- GameAction, sonuçlar ve immutable kopyalama kuralları
- Model invariant testleri

### Faz 4 — Kurallar ve pathfinding

- BoardGraph / geçiş engeli hesabı
- MoveValidator, BFSValidator, WallValidator
- AStarPathFinder, VictoryChecker, TurnManager, RuleEngine
- Tüm kritik kural ve algoritma testleri

### Faz 5 — Oyun motoru

- GameEngine ve GameManager
- Aksiyonların atomik uygulanması
- Hamle geçmişi, restart ve opsiyonel undo
- Engine entegrasyon testleri

### Faz 6 — AI

- AIEngine sözleşmesi ve ortak aday üretimi
- EasyAI, MediumAI, HardAI
- Minimax, alpha-beta, evaluation ve transposition table
- Geçerlilik, taktik ve süre testleri

### Faz 7 — Data katmanı

- GameRepository sözleşmesi ve uygulaması
- SettingsManager, StatisticsManager, DataStore serializer/key yapısı
- Repository ve migration/default testleri

### Faz 8 — Tema, navigasyon ve menüler

- Material 3 tema, açık/koyu/dinamik palet
- Splash, ana menü, mod, zorluk, ayarlar ve istatistik ekranları
- Tip güvenli navigasyon

### Faz 9 — Oyun UI

- GameUiState, GameEvent, GameViewModel
- CanvasRenderer ve TouchController
- Responsive oyun ekranı, duvar seçimi ve hamle geçmişi
- UI state ve Compose testleri

### Faz 10 — Efektler ve sonuç ekranı

- AnimationManager, SoundManager, haptik yönetimi
- Piyon/duvar animasyonları, konfeti ve winner ekranı
- Tekrar oyna ve ana menü akışları

### Faz 11 — Üretim sertleştirme

- Tam test paketi, lint ve release build
- R8 ve resource shrinking doğrulaması
- Erişilebilirlik, farklı ekran boyutları ve dark/light kontrolleri
- 60 FPS ölçümü ve gereksiz recomposition analizi
- Signed bundle için yapılandırma talimatı; anahtar dosyası repoya eklenmez

## 17. Dosya üretim kuralı

Sonraki teslimlerde dosyalar dependency sırasına göre oluşturulur. Her dosya için:

1. Tam yol belirtilir.
2. Eksiksiz, derlenebilir içerik yazılır.
3. Bağlı olduğu daha önce oluşturulmuş tipler doğrulanır.
4. İlgili test aynı fazda eklenir.
5. Dosya sonunda kodun içine gereksiz yorum eklemek yerine, teslim mesajında kısa teknik tasarım gerekçesi verilir.

Hiçbir fazda pseudo-code, TODO, boş gövde veya geçici `NotImplementedError` bırakılmaz.

## 18. Tamamlanma ölçütleri

Proje tamamlanmış sayılmak için:

- Temiz checkout sonrası Gradle sync başarılı olmalı.
- Debug APK ve release AAB derlenmeli.
- Tüm JVM ve Compose testleri geçmeli.
- Lint'te blocker/critical hata olmamalı.
- İki oyun modu baştan sona oynanabilmeli.
- Tüm resmî hareket ve duvar kuralları testlerle kanıtlanmalı.
- AI hiçbir durumda geçersiz aksiyon üretmemeli.
- Ayarlar ve istatistikler uygulama yeniden açıldığında korunmalı.
- Telefon ve tablet yerleşimleri taşma olmadan çalışmalı.
- UI ana thread'i AI sırasında bloklanmamalı.
- Uygulama içinde üçüncü taraf telifli görsel veya ses bulunmamalı.

## 19. Karar kaydı

- **Özgün ad ve görsel dil:** Oyun mekaniği korunurken ürün kimliği bağımsızlaştırılır.
- **Saf Kotlin motor:** Kural doğruluğu, performans ve hızlı test için Android bağımlılıklarından ayrılır.
- **Multi-module yapı:** Clean Architecture sınırları build sistemi tarafından da korunur.
- **Graph edge modeli:** Duvar ve hareket kuralları kare işaretlemek yerine geçiş kenarlarını kapatarak doğru modellenir.
- **AI aday daraltma:** Hard AI'ın mobil cihazda doğal ve zamanında hamle yapabilmesi için yalnızca taktik açıdan ilgili duvarlar derin aramaya alınır.
- **State ve event ayrımı:** Yeniden çizilebilir UI durumu ile tek seferlik ses/navigasyon etkilerinin karışması önlenir.

## 20. Resmî teknik referanslar

- Android 17 SDK kurulumu: https://developer.android.com/about/versions/17/setup-sdk
- AGP 9.3 uyumluluk bilgileri: https://developer.android.com/build/releases/agp-9-3-0-release-notes
- Compose Compiler ve BOM kurulumu: https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler
- Hilt dependency injection: https://developer.android.com/training/dependency-injection/hilt-android
- Google Play target API gereksinimi: https://developer.android.com/google/play/requirements/target-sdk
