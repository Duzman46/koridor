# Koridor — Veri Toplama ve Play Console Veri Güvenliği Beyanı

Bu belge, Koridor'un topladığı verileri, toplama amacını ve silme yollarını açıklar. Play
Console'daki **Veri güvenliği (Data safety)** formunu doldururken doğrudan bu belge esas
alınmalıdır.

Son güncelleme: uygulama sürümü 1.0.0.

---

## 1. Kullanılan servisler

| Servis | Ne için | Veri işlenen yer |
|---|---|---|
| Firebase Authentication | Hesap oluşturma ve giriş | Google Cloud |
| Firebase Realtime Database | Profil, oda, maç sonucu, maç geçmişi, arkadaşlık, presence | Google Cloud |
| Cloudflare Worker (`worker/`) | Puan hesaplama, maç geçmişi yazımı, oda temizliği, eşleştirme | Cloudflare + Google Cloud |
| Firebase Crashlytics | Kilitlenme ve işlenmiş hata raporları | Google Cloud |
| Google Analytics for Firebase | Kullanım ölçümü | Google Cloud |
| Google Play Billing | Uygulama içi satın alma | Google |
| Google Play Developer API | Satın alma token doğrulaması (sunucu tarafı) | Google |
| Google AdMob | Reklam gösterimi | Google |
| Google UMP (User Messaging Platform) | Reklam onayı / GDPR rızası | Google |
| Android Credential Manager | Google ile giriş | Cihaz + Google |

> **Not:** Firebase Cloud Messaging (FCM) **kullanılmamaktadır**. Bildirim gönderilmediği için
> bu bağımlılık projeye hiç eklenmedi.
>
> **Not:** Sunucu tarafındaki iş, Cloud Functions yerine zamanlanmış bir Cloudflare Worker'da
> çalışır; `functions/` dizini aynı işin yayına alınmamış Cloud Functions karşılığıdır ve
> ikisinin birden dağıtılması yanlıştır (bkz. `worker/README.md`). Play satın alma
> doğrulaması (`functions/src/purchases.ts`) bu nedenle **şu an dağıtılmış değildir**;
> "reklamsız" hakkı cihazdaki Play Billing yanıtından gelir.

---

## 2. Toplanan veriler

### 2.1 Hesap bilgileri

| Veri | Zorunlu mu | Amaç | Nerede saklanır | Başkaları görür mü |
|---|---|---|---|---|
| E-posta adresi | Hayır (yalnızca e-posta ile kayıtta) | Hesap kimliği, şifre sıfırlama | Firebase Auth + `usersPrivate/{uid}/email` | **Hayır** |
| Kullanıcı kimliği (UID) | Evet | Tüm verilerin anahtarı | Firebase | Dolaylı (profil erişimi için) |
| Kullanıcı adı | Evet | Oyuncuların birbirini bulması ve profilde gösterim | `users/{uid}/username` | **Evet** |
| Avatar kimliği | Evet | Profil görseli (yerel olarak çizilir, dosya yüklenmez) | `users/{uid}/avatarId` | **Evet** |
| Hesap türü (misafir/e-posta/Google) | Evet | Özellik erişimi | `users/{uid}/accountType` | Evet |
| Dil tercihi | Hayır | Cihazlar arası dil senkronizasyonu | `users/{uid}/preferredLanguage` | Evet |

**Şifreler uygulama tarafından hiçbir zaman saklanmaz veya loglanmaz.** Kimlik doğrulama
tamamen Firebase Authentication'a devredilmiştir.

### 2.2 Oyun verileri

| Veri | Amaç | Nerede | Başkaları görür mü |
|---|---|---|---|
| Puan (rating), en yüksek puan | Eşleştirme ve liderlik tablosu | `users/{uid}` | Evet |
| Galibiyet / mağlubiyet / beraberlik / maç sayısı | İstatistik, liderlik tablosu | `users/{uid}` | Evet |
| Galibiyet serisi | Profil | `users/{uid}` | Evet |
| Öğretici tamamlandı bilgisi | Öğreticinin tekrar gösterilmemesi | `users/{uid}` + cihaz | Hayır |
| Maç sonucu kaydı | Puanlamanın sunucuda doğrulanması | `matchResults/{matchId}` | Yalnızca maçın iki oyuncusu |
| Son maçlar (rakibin kullanıcı adı, sonuç, tarih, puan değişimi) | Profildeki "son oyunlar" bölümü | `recentMatches/{uid}` (son 10 maç) | **Evet** — giriş yapmış her oyuncu okuyabilir; yalnızca sunucu yazar |
| Oda verileri (kod, ad, ayarlar, tahta durumu) | Çevrim içi maçın yürütülmesi | `rooms/{code}` | Açık odalarda liste görünür |
| Maç içi hazır mesaj (sabit bir anahtar + sunucu zaman damgası) | Rakibe hazır ifade veya emoji gönderme | `rooms/{code}/chat/{uid}` | Odayı okuyabilen herkes; pratikte maçın iki oyuncusu |
| Yerel istatistikler | Cihazdaki istatistik ekranı | Cihaz (DataStore) | Hayır |

### 2.3 Sosyal veriler

| Veri | Amaç | Nerede | Başkaları görür mü |
|---|---|---|---|
| Arkadaşlık ilişkileri | Arkadaş listesi | `friendships/{uid}/{other}` | Yalnızca ilgili iki kişi |
| Engelleme kayıtları | Engellemenin uygulanması | `friendships/{uid}/{other}` | **Hayır** (engellenen kişi bunu göremez) |
| Oyun davetleri | Davet gönderme | `invites/{alıcı}/{gönderen}` | Yalnızca alıcı |
| Çevrim içi durumu | Arkadaşın müsait olup olmadığı | `presence/{uid}` | Yalnızca giriş yapmış kullanıcılar |

**Presence yalnızca `online: true/false` içerir.** Konum, IP, cihaz kimliği veya "en son
görülme" zamanı başka oyunculara gösterilmez.

### 2.4 Satın alma verileri

| Veri | Amaç | Nerede | Başkaları görür mü |
|---|---|---|---|
| Satın alma token'ı (SHA-256 hash'lenmiş) | Sunucu tarafı doğrulama, mükerrer kullanımın engellenmesi | `purchaseReceipts/{uid}/{hash}` | Hayır |
| Sipariş kimliği, satın alma zamanı | Doğrulama ve destek | `purchaseReceipts/{uid}/{hash}` | Hayır |
| Sahip olunan ürünler (entitlement) | Reklamsız kullanım, tahta temaları | `users/{uid}/purchasedEntitlements` + cihaz | Hayır |

**Ödeme bilgisi (kart, adres, isim) uygulamaya hiç ulaşmaz.** Tüm ödeme akışı Google Play
tarafından yürütülür.

### 2.5 Reklam verileri

AdMob, reklam gösterimi ve ölçümü için reklam kimliği (Advertising ID) ve cihaz bilgileri
toplayabilir. Bu toplama, UMP üzerinden alınan kullanıcı rızasına bağlıdır ve **Premium
(reklamsız) satın alan kullanıcılarda reklamlar hiç yüklenmez**.

Reklam gizlilik tercihleri uygulama içinden değiştirilebilir:
**Ayarlar → Mağaza → Reklam Gizlilik Seçenekleri**

### 2.6 Toplanmayan veriler

Aşağıdakiler **kesinlikle toplanmaz**:

- Konum bilgisi (hiçbir çeşidi)
- Rehber, kişiler, telefon numarası
- Fotoğraf, video, ses kaydı, dosya
- Sağlık, finans veya biyometrik veri
- SMS, arama kaydı
- Kullanıcı tarafından yüklenen görsel (avatarlar kod içinde çizilir)
- Maç içinde serbest metin. Çevrim içi maçta gönderilebilen tek şey kapalı bir listeden
  seçilen hazır ifadeler ve emojilerdir; oyuncunun maç sırasında yazdığı hiçbir metin
  cihazdan çıkmaz. Veritabanı kuralları listedeki anahtarlar dışında hiçbir değeri kabul
  etmez. Bu liste, uygulamayı Play'in kullanıcı içeriği (UGC) yükümlülükleri — moderasyon,
  şikâyet ve engelleme altyapısı — kapsamına sokmayan tasarımdır ve serbest metne
  genişletilmemelidir. Oyuncunun yazıp başkalarının görebildiği yalnızca iki alan vardır:
  **kullanıcı adı** ve **oda adı**. İkisi de §2.1 ve §2.2'de listelenir, §5'te beyan edilir ve
  bu madde onları kapsamaz.

---

## 3. Loglama ve hata kayıtları

- Release derlemesinde `AppLog` cihaz loguna yalnızca işlem adını ve exception **tipini**
  yazar; mesaj, stack trace, token, e-posta veya oda kodu yazılmaz.
- R8 kuralları (`proguard-rules.pro`) `Log.d/v/i` çağrılarını release binary'sinden tamamen
  kaldırır, böylece debug metinleri APK içinde bile bulunmaz.
- Firebase veya sunucu hata kodları kullanıcıya hiçbir zaman gösterilmez; hepsi `AppError`
  enum'una eşlenip yerelleştirilmiş genel mesajlara dönüştürülür.
- **Crashlytics kullanılmaktadır** (`app/build.gradle.kts` → `firebase-crashlytics`,
  `AppLog.report`). Release derlemesinde iki tür kayıt gider: kilitlenmeler ve `AppLog.warn`
  ile işlenen hataların "non-fatal" olarak bildirimi. Yanına iliştirilen tek özel alan
  `operation`'dır ve çağrı yerinde sabit yazılmış bir etikettir (örneğin `send-message`) —
  oyuncu içeriği, kullanıcı adı, e-posta veya oda kodu taşımaz. Crashlytics'in kendisi ayrıca
  cihaz modeli, işletim sistemi sürümü ve bir kurulum kimliği toplar; bu, §5'te "Kilitlenme
  günlükleri" ve "Tanılama" olarak beyan edilir.
- Google Analytics for Firebase de bağımlılıklardadır ve varsayılan otomatik olayları
  toplar. Uygulama kendi olayını hiç göndermez; beyan için §6'daki karara bakınız.

---

## 4. Verilerin silinmesi

### 4.1 Kullanıcının kendi silmesi

Uygulama içi yol: **Ayarlar → Hesap → Hesabı sil**

Kullanıcı onay olarak "SİL" (dilin karşılığı) yazmak zorundadır. Onaylandığında sırayla:

1. Arkadaşlık kayıtları hem kullanıcıdan hem **karşı tarafların listelerinden** silinir
2. Bekleyen davetler silinir
3. Presence kaydı silinir
4. Kullanıcı adı rezervasyonu (`usernames/{normalized}`) serbest bırakılır
5. Özel veriler (`usersPrivate/{uid}` — e-posta) silinir
6. Genel profil (`users/{uid}`) silinir
7. Firebase Authentication hesabı silinir
8. Cihazdaki öğretici ve misafir durumu sıfırlanır

Bu sıralama zorunludur: kimlik silindikten sonra veritabanı kuralları o yazma işlemlerine
artık izin vermez.

### 4.2 Silme sonrası kalan veriler

| Veri | Durum | Gerekçe |
|---|---|---|
| `matchResults/{matchId}` | Kalır, ancak yalnızca UID içerir | Rakibin maç geçmişinin bütünlüğü; UID artık hiçbir profile bağlı değildir (anonimleşir) |
| `recentMatches/{uid}` | Kalır (en fazla 10 satır) | Bu düğüme hiçbir istemcinin yazma kuralı yoktur — silmek de bir yazmadır — ve silme hakkı vermek, kaybı sildirmenin yolu olurdu. Kalan satırlar zaten herkese açıktı: rakibin kullanıcı adı, sonuç, tarih. Profil gittiği için uygulamadan erişilemez |
| Silinen oyuncunun adı, rakiplerinin `recentMatches` satırlarında | Kalır | Ad, maç kaydedilirken kopyalanır; rakibin kendi geçmişi silinen hesabın malı değildir |
| `purchaseTokens/{hash}` | Kalır (hash) | Aynı satın almanın başka hesaba aktarılmasını engeller; kişisel veri içermez |
| Google Play satın alma kaydı | Google'da kalır | Google Play'in kendi politikası; uygulama kontrolünde değildir |
| AdMob reklam verileri | Google'da kalır | Google reklam ayarlarından yönetilir |

### 4.3 Otomatik temizlik

- Rakip beklenen odalar **30 dakika** sonra silinir
- Oynanan odalar **24 saat** sonra silinir
- Maç içi hazır mesajlar odanın içinde durur; odayla birlikte silinir, ayrı bir saklama
  süreleri yoktur
- Oyun davetleri **10 dakika** sonra geçersiz olur

Bu temizliği dakikada bir çalışan `worker/src/sweep.ts` yapar. `recentMatches` bunun dışındadır
ve süreyle değil sayıyla sınırlanır: her oyuncu için yalnızca son 10 maç tutulur, on birinci maç
en eskisini düşürür.

---

## 5. Play Console Veri Güvenliği formu — beyan edilmesi gerekenler

Aşağıdaki tablo forma doğrudan aktarılabilir.

### Toplanan veri türleri

| Form kategorisi | Veri türü | Toplanır | Paylaşılır | Zorunlu | Amaç |
|---|---|---|---|---|---|
| Kişisel bilgiler | E-posta adresi | ✅ | ❌ | Hayır | Hesap yönetimi |
| Kişisel bilgiler | Kullanıcı kimlikleri | ✅ | ❌ | Evet | Hesap yönetimi, Uygulama işlevselliği |
| Kişisel bilgiler | Ad (kullanıcı adı) | ✅ | ❌ | Evet | Uygulama işlevselliği |
| Uygulama etkinliği | Uygulama içi eylemler (maç sonuçları, istatistikler, son maçlar) | ✅ | ❌ | Evet | Uygulama işlevselliği |
| Uygulama etkinliği | Diğer kullanıcı tarafından oluşturulan içerik (oda adı) | ✅ | ❌ | Hayır | Uygulama işlevselliği |
| Mesajlar | Diğer uygulama içi mesajlar (maç içi hazır ifadeler) | ✅ | ❌ | Hayır | Uygulama işlevselliği |
| Finansal bilgiler | Satın alma geçmişi | ✅ | ❌ | Hayır | Uygulama işlevselliği |
| Uygulama bilgileri ve performansı | Kilitlenme günlükleri | ✅ | ❌ | Hayır | Analitik, Uygulama işlevselliği |
| Uygulama bilgileri ve performansı | Tanılama (non-fatal hatalar, cihaz/OS bilgisi) | ✅ | ❌ | Hayır | Analitik, Uygulama işlevselliği |
| Cihaz veya diğer kimlikler | Cihaz veya diğer kimlikler (Reklam Kimliği) | ✅ | ✅ | Hayır | Reklamcılık |

### Güvenlik uygulamaları — form yanıtları

| Soru | Yanıt | Gerekçe |
|---|---|---|
| Veriler aktarımda şifreleniyor mu? | **Evet** | Tüm Firebase ve Play trafiği TLS üzerinden |
| Kullanıcı verilerinin silinmesini isteyebiliyor mu? | **Evet** | Uygulama içi "Hesabı sil" akışı |
| Veriler bağımsız bir güvenlik incelemesinden geçti mi? | Hayır | Böyle bir inceleme yapılmadı |
| Aile Politikası kapsamında mı? | Hedef kitleye göre belirlenmeli | 13 yaş altına özel hedefleme yapılmıyor |

### Maç içi mesajlar neden "kullanıcı içeriği" değildir

Gönderilebilecek her şey uygulamada sabit bir listedir: sekiz hazır ifade ve altı emoji.
Veritabanına yazılan değer bu listenin anahtarlarından biridir ve kurallar başka hiçbir değeri
kabul etmez, dolayısıyla oyuncunun **yazdığı** bir içerik hiçbir zaman ortaya çıkmaz. Bu yüzden
Play'in kullanıcı içeriği barındıran uygulamalardan istediği moderasyon, şikâyet ve engelleme
altyapısı gerekmez. Buna karşılık form yine de yukarıdaki satırla beyan edilir: eksik beyan bir
politika ihlali, fazladan beyan değildir.

Oyuncunun kendi savunması da uygulamanın içindedir: **Ayarlar → Oyun deneyimi → Maç mesajları**
kapatıldığında ne mesaj gelir ne de gönderilebilir. Aynı anahtar, maç sırasında mesaj
seçicisinin altındaki "Mesajları kapat" bağlantısıyla da kapatılabilir.

### "Paylaşılır" işaretlenen tek kalem

Reklam Kimliği, AdMob aracılığıyla Google ile paylaşılır. Bu paylaşım **kullanıcı rızasına
bağlıdır** (UMP) ve Premium satın alındığında hiç gerçekleşmez.

---

## 6. Yapılandırılması gereken alanlar

Aşağıdakiler **uydurulmamıştır** ve yayın öncesi doldurulmak zorundadır. Uygulama bu
değerler boşken ilgili bağlantıyı **gizler**, hatalı bir adrese yönlendirmez.

| Alan | Dosya | Şu anki durum |
|---|---|---|
| Gizlilik Politikası URL'si | `app.properties` → `KORIDOR_PRIVACY_POLICY_URL` | **Boş — doldurulmalı** |
| Kullanım Koşulları URL'si | `app.properties` → `KORIDOR_TERMS_URL` | **Boş — doldurulmalı** |

Gizlilik politikası metninin Türkçe ve İngilizce taslakları `docs/PRIVACY_POLICY_TR.md` ve
`docs/PRIVACY_POLICY_EN.md` dosyalarındadır; bu belgedeki tablolarla uyumlu hâle getirilip
herkese açık bir adreste yayımlanmalıdır. Play Console, gizlilik politikası URL'si olmadan
yayına izin vermez.

**Bu iki taslak şu anda uygulamanın gerisindedir** ve olduğu gibi yayımlanamaz. Yalnızca
anonim çevrim içi oyunu anlatıyorlar; şunlardan hiçbiri geçmiyor: e-posta ve Google ile
hesap açma, kullanıcı adı, profil, arkadaşlık ve engelleme, liderlik tablosu, profildeki son
maçlar, maç içi hazır mesajlar, Crashlytics ve Analytics. "Uygulama kullanıcıdan ad, e-posta
veya telefon numarası istemez" cümlesi artık **doğru değildir**. Yayın öncesi ikisi de bu
belgenin §1, §2 ve §5 tablolarına göre yeniden yazılmalıdır.

### Karar verilmesi gereken: Google Analytics for Firebase

`app/build.gradle.kts` `firebase-analytics` bağımlılığını taşır ve `google-services.json`
varken otomatik olay toplama açıktır. Uygulama kendi olayını hiç göndermez, ancak SDK
varsayılan olarak oturum, ilk açılış ve ekran görüntüleme olaylarını bir kurulum kimliğiyle
birlikte toplar. İki seçenekten biri yayın öncesi seçilmelidir:

- **Kalsın.** §5 tablosuna "Uygulama etkinliği → Uygulama içi eylemler" satırının amacına
  *Analitik* eklenir ve "Cihaz veya diğer kimlikler" satırı Analytics'i de kapsayacak biçimde
  genişletilir.
- **Çıkarılsın.** Bağımlılık kaldırılır ve §5 tablosu olduğu gibi kalır. Crashlytics
  Analytics olmadan da çalışır; kaybedilen tek şey rapora iliştirilen "breadcrumb" olay
  dökümü ve kilitlenme hızı uyarılarıdır.

Crashlytics'in beyanı bu karardan bağımsızdır: kilitlenme raporlaması kullanıldığı için
§5'teki iki satır her hâlükârda beyan edilir.
