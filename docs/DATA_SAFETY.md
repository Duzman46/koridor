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
| Haftalık liderlik satırı (kullanıcı adı, avatar, puan, galibiyet, maç sayısı kopyası) | Haftalık tablonun tek sorguda okunabilmesi | `leaderboards/weekly/{hafta}/{uid}` | **Evet** — giriş yapmış her oyuncu okuyabilir; yalnızca sunucu yazar |
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
| İçerik bildirimi (bildiren UID, kategori, oda kodu) | Kullanıcı adı veya oda adı hakkındaki şikâyetin geliştiriciye ulaşması | `contentReports/{bildirilen}/{bildiren}` | **Hayır** — hiçbir istemci okuyamaz, bildirilen oyuncu da göremez ve silemez |

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

- Konum bilgisi — **uygulamanın kendisi tarafından**. Konum izni istenmez, konum API'si hiç
  çağrılmaz, hiçbir konum değeri veritabanına yazılmaz. Buna karşılık AdMob, reklam sunarken
  isteğin IP adresinden şehir düzeyinde bir konum türetebilir; §5 tablosu bunu "Yaklaşık konum"
  olarak beyan eder. Beyan, Play formunun üçüncü taraf SDK'ları da kapsaması nedeniyle
  gereklidir ve yayımlanan gizlilik sayfasıyla aynı şeyi söyler
- Rehber, kişiler, telefon numarası
- Fotoğraf, video, ses kaydı, dosya
- Sağlık, finans veya biyometrik veri
- SMS, arama kaydı
- Kullanıcı tarafından yüklenen görsel (avatarlar kod içinde çizilir)
- Maç içinde serbest metin. Çevrim içi maçta gönderilebilen tek şey kapalı bir listeden
  seçilen hazır ifadeler ve emojilerdir; oyuncunun maç sırasında yazdığı hiçbir metin
  cihazdan çıkmaz. Veritabanı kuralları listedeki anahtarlar dışında hiçbir değeri kabul
  etmez ve bu liste serbest metne genişletilmemelidir. Oyuncunun yazıp başkalarının
  görebildiği yalnızca iki alan vardır: **kullanıcı adı** ve **oda adı**. İkisi de §2.1 ve
  §2.2'de listelenir, §5'te beyan edilir, bu madde onları kapsamaz — ve Play'in kullanıcı
  içeriği yükümlülüğünü doğuran da onlardır (bkz. §5, "Kullanıcı içeriği ve şikâyet yolu").

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
2. Bekleyen davetler silinir — hem kullanıcının kendi kutusundakiler hem de arkadaş
   listesindeki oyunculara gönderdikleri
3. Presence kaydı silinir
4. Kullanıcı adı rezervasyonu (`usernames/{normalized}`) serbest bırakılır
5. Özel veriler (`usersPrivate/{uid}` — e-posta) silinir
6. Genel profil (`users/{uid}`) silinir
7. Firebase Authentication hesabı silinir
8. Cihazdaki öğretici ve misafir durumu sıfırlanır
9. Haftalık liderlik tablosundaki satırlar sunucu tarafından silinir (§4.3)

Bu sıralama zorunludur: kimlik silindikten sonra veritabanı kuralları o yazma işlemlerine
artık izin vermez.

2. adımın tek istisnası rövanş istekleridir. Rövanş, arkadaşlık değil biten maç karşılığında
gönderilir; yani arkadaş olmayan bir rakibin kutusunda da bulunabilir. Bir davet kutusunu
kurallar gereği yalnızca sahibi okuyabilir — hiçbir derinlikte değil — dolayısıyla giden hesabın
o kaydı bulmasının bir yolu yoktur. Kaydı, silme işleminden sonraki taramada sunucu siler
(§4.3); gönderenin profili artık bulunmadığı için süresi dolmasa bile toplanır.

9. adım da aynı nedenle sunucunun işidir. `leaderboards` düğümüne hiçbir istemcinin yazma
kuralı yoktur — tablo, oyuncuların düzenleyemeyeceği bir kayıt olduğu için böyledir — dolayısıyla
giden hesap kendi satırını kaldıramaz. Satır, kullanıcı adının herkese açık bir kopyasıdır ve
"kalıcı olarak sil" onu da kapsamak zorundadır; silen el, admin kimliğini taşıyan taramadır.

### 4.2 Silme sonrası kalan veriler

| Veri | Durum | Gerekçe |
|---|---|---|
| `matchResults/{matchId}` | Kalır, ancak yalnızca UID içerir | Rakibin maç geçmişinin bütünlüğü; UID artık hiçbir profile bağlı değildir (anonimleşir) |
| `recentMatches/{uid}` | Kalır (en fazla 10 satır) | Bu düğüme hiçbir istemcinin yazma kuralı yoktur — silmek de bir yazmadır — ve silme hakkı vermek, kaybı sildirmenin yolu olurdu. Kalan satırlar zaten herkese açıktı: rakibin kullanıcı adı, sonuç, tarih. Profil gittiği için uygulamadan erişilemez |
| Silinen oyuncunun adı, rakiplerinin `recentMatches` satırlarında | Kalır | Ad, maç kaydedilirken kopyalanır; rakibin kendi geçmişi silinen hesabın malı değildir |
| `leaderboards/weekly/{hafta}/{uid}` | **Kalmaz** | Kullanıcı adının herkese açık bir kopyasıdır; silinen hesabın satırını sunucu taraması kaldırır (§4.3) |
| `contentReports/{bildirilen}/{bildiren}` | Kalır | Bir şikâyet, hakkında olduğu hesabın malı değildir; hiçbir istemci okuyamaz |
| `purchaseTokens/{hash}` | Kalır (hash) | Aynı satın almanın başka hesaba aktarılmasını engeller; kişisel veri içermez |
| Google Play satın alma kaydı | Google'da kalır | Google Play'in kendi politikası; uygulama kontrolünde değildir |
| AdMob reklam verileri | Google'da kalır | Google reklam ayarlarından yönetilir |

### 4.3 Otomatik temizlik

- Rakip beklenen odalar **30 dakika** sonra silinir
- Oynanan odalar **24 saat** sonra silinir
- Maç içi hazır mesajlar odanın içinde durur; odayla birlikte silinir, ayrı bir saklama
  süreleri yoktur
- Oyun davetleri ve rövanş istekleri **10 dakika** sonra geçersiz olur; süresi dolan kayıtlar
  sunucu tarafından ayrıca silinir
- Silinmiş bir hesabın başka bir oyuncunun kutusunda bıraktığı davet, süresi dolmasa bile
  aynı taramada silinir: gönderenin artık profili yoktur
- Haftalık liderlik tablosu yalnızca **içinde bulunulan haftayı ve bir öncekini** tutar; daha
  eskisi bütünüyle silinir. Uygulama zaten yalnızca içinde bulunulan haftayı okur, bir önceki
  ise saati geri kalmış bir cihaz için bırakılır
- Yaşayan haftada, profili artık bulunmayan bir hesabın satırı da silinir; bu, silinen bir
  hesabın adının herkese açık bir tabloda kalmamasını sağlayan tek yoldur

Bu temizliği dakikada bir çalışan `worker/src/sweep.ts` yapar. `recentMatches` bunun dışındadır
ve süreyle değil sayıyla sınırlanır: her oyuncu için yalnızca son 10 maç tutulur, on birinci maç
en eskisini düşürür.

---

## 5. Play Console Veri Güvenliği formu — beyan edilmesi gerekenler

Aşağıdaki tablo forma doğrudan aktarılabilir.

### Toplanan veri türleri

| Form kategorisi | Veri türü | Toplanır | Paylaşılır | Zorunlu | Amaç |
|---|---|---|---|---|---|
| Konum | Yaklaşık konum (AdMob'un IP'den türettiği şehir düzeyi konum) | ✅ | ✅ | Hayır | Reklamcılık |
| Kişisel bilgiler | E-posta adresi | ✅ | ❌ | Hayır | Hesap yönetimi |
| Kişisel bilgiler | Kullanıcı kimlikleri | ✅ | ❌ | Evet | Hesap yönetimi, Uygulama işlevselliği |
| Kişisel bilgiler | Ad (kullanıcı adı) | ✅ | ❌ | Evet | Uygulama işlevselliği |
| Uygulama etkinliği | Uygulama içi eylemler (maç sonuçları, istatistikler, son maçlar; ayrıca Analytics'in varsayılan oturum ve ekran olayları) | ✅ | ❌ | Evet | Uygulama işlevselliği, Analitik |
| Uygulama etkinliği | Diğer kullanıcı tarafından oluşturulan içerik (oda adı) | ✅ | ❌ | Hayır | Uygulama işlevselliği |
| Mesajlar | Diğer uygulama içi mesajlar (maç içi hazır ifadeler) | ✅ | ❌ | Hayır | Uygulama işlevselliği |
| Finansal bilgiler | Satın alma geçmişi | ✅ | ❌ | Hayır | Uygulama işlevselliği |
| Uygulama bilgileri ve performansı | Kilitlenme günlükleri | ✅ | ❌ | Hayır | Analitik, Uygulama işlevselliği |
| Uygulama bilgileri ve performansı | Tanılama (non-fatal hatalar, cihaz/OS bilgisi) | ✅ | ❌ | Hayır | Analitik, Uygulama işlevselliği |
| Cihaz veya diğer kimlikler | Cihaz veya diğer kimlikler (Reklam Kimliği; Analytics ve Crashlytics kurulum kimlikleri) | ✅ | ✅ | Hayır | Reklamcılık, Analitik |

### Güvenlik uygulamaları — form yanıtları

| Soru | Yanıt | Gerekçe |
|---|---|---|
| Veriler aktarımda şifreleniyor mu? | **Evet** | Tüm Firebase ve Play trafiği TLS üzerinden |
| Kullanıcı verilerinin silinmesini isteyebiliyor mu? | **Evet** | Uygulama içi "Hesabı sil" akışı |
| Veriler bağımsız bir güvenlik incelemesinden geçti mi? | Hayır | Böyle bir inceleme yapılmadı |
| Aile Politikası kapsamında mı? | Hedef kitleye göre belirlenmeli | 13 yaş altına özel hedefleme yapılmıyor |

### Kullanıcı içeriği ve şikâyet yolu

Uygulamanın bir oyuncunun yazdığını başka oyunculara gösterdiği **iki** alan vardır ve Play'in
kullanıcı içeriği yükümlülüğünü doğuran da bu ikisidir:

- **kullanıcı adı** — liderlik tablosunda, profilde, maç sırasında tahtada ve rakiplerin son
  oyunlar satırlarında görünür; 3-16 karakter, yalnızca `[A-Za-z0-9_]`
- **oda adı** — herkese açık oda listesinde görünür; en fazla 32 karakter, serbest metin

Karşılığında uygulamada üç şey vardır:

1. **Bildirme.** Her oyuncu profilinde bir **Bildir** düğmesi vardır — liderlik tablosundan,
   son oyunlar satırından ve maç sırasında rakibe dokunarak ulaşılır. Oda listesinde her satırın
   yanında aynı işi yapan bir bayrak simgesi bulunur. Gerekçe kapalı bir listeden seçilir;
   serbest metin alanı yoktur. Kayıt `contentReports/{bildirilen}/{bildiren}` altına yazılır;
   hiçbir istemci onu okuyamaz, bildirilen oyuncu göremez ve silemez. Misafirler de bildirebilir:
   bir şeyi görmek için hesap gerekmez.
2. **Engelleme.** Aynı profil sayfasından ve arkadaşlar ekranından. Engellenen oyuncu davet
   gönderemez, arkadaşlık isteği yazamaz ve engellendiğini öğrenemez.
3. **Moderasyon.** Bildirimler veritabanı konsolundan okunur; gereken hesabın kullanıcı adı
   `users/{uid}/username` üzerinden değiştirilebilir veya `accountStatus` ile askıya alınabilir.

**Maç içi mesajlar bu kapsamın dışındadır.** Gönderilebilecek her şey sabit bir listedir: sekiz
hazır ifade ve altı emoji. Veritabanına yazılan değer bu listenin anahtarlarından biridir ve
kurallar başka hiçbir değeri kabul etmez, dolayısıyla oyuncunun **yazdığı** bir içerik oradan
hiçbir zaman çıkmaz. Form yine de yukarıdaki satırla beyan eder: eksik beyan bir politika
ihlali, fazladan beyan değildir. Oyuncunun kendi savunması da uygulamanın içindedir:
**Ayarlar → Oyun deneyimi → Maç mesajları** kapatıldığında ne mesaj gelir ne de gönderilebilir.
Aynı anahtar, maç sırasında mesaj seçicisinin altındaki "Mesajları kapat" bağlantısıyla da
kapatılabilir.

### "Paylaşılır" işaretlenen kalemler

İkisi de AdMob'dan gelir ve ikisi de Google ile paylaşılır: **Reklam Kimliği** ve **yaklaşık
konum**. Paylaşım **kullanıcı rızasına bağlıdır** (UMP) ve Premium satın alındığında hiç
gerçekleşmez, çünkü o durumda reklam SDK'sı yüklenmez.

Uygulamanın kendi yazdığı hiçbir veri paylaşılmaz.

---

## 6. Yapılandırılması gereken alanlar

Aşağıdakiler **uydurulmamıştır** ve yayın öncesi doldurulmak zorundadır. Uygulama bu
değerler boşken ilgili bağlantıyı **gizler**, hatalı bir adrese yönlendirmez.

| Alan | Dosya | Şu anki durum |
|---|---|---|
| Gizlilik Politikası URL'si | `app.properties` → `KORIDOR_PRIVACY_POLICY_URL` | Dolu: `https://gridbound-duzman46.web.app/privacy` |
| Kullanım Koşulları URL'si | `app.properties` → `KORIDOR_TERMS_URL` | **Boş — doldurulmalı** |

Gizlilik politikası dört dosyada durur ve dördü birlikte değiştirilir: yayımlanan sayfalar
`public/privacy.html` ve `public/gizlilik.html` (Firebase Hosting, `cleanUrls` sayesinde
`/privacy` ve `/gizlilik`), aynı metnin markdown karşılıkları `docs/PRIVACY_POLICY_EN.md` ve
`docs/PRIVACY_POLICY_TR.md`. Dördü de bu belgenin §1, §2, §4 ve §5 tablolarından türetilmiştir:
hesap açma, kullanıcı adı, profil, arkadaşlık ve engelleme, liderlik tabloları, son maçlar,
maç içi hazır mesajlar, bildirme yolu, Crashlytics ve Analytics, hepsi geçer. Geliştirici
iletişim adresi (`furkanduzman46@gmail.com`, mağaza sayfasındakiyle aynı) sayfanın sonundadır.

**Bu belge değiştiğinde o dört dosya da değişmelidir.** Play incelemesinin fiilen okuduğu tek
şey yayımlanan sayfadır ve formla çelişmesi tek başına ret sebebidir.

### Google Analytics for Firebase — kalıyor, beyan ediliyor

`app/build.gradle.kts` `firebase-analytics` bağımlılığını taşır ve `google-services.json`
varken otomatik olay toplama açıktır. Uygulama kendi olayını hiç göndermez — kodda tek bir
`logEvent` çağrısı yoktur — ancak SDK varsayılan olarak oturum, ilk açılış ve ekran görüntüleme
olaylarını bir kurulum kimliğiyle birlikte toplar.

Sahibi 2026-08-08'de bunun kalmasına karar verdi. Gerekçe: yeni yayımlanan bir oyunda kaç kişi
oynadığını, nereden geldiğini ve ertesi gün geri dönüp dönmediğini bilmemek, düzeltilecek şeyi
seçememek demektir. §5 tablosu bu karara göre yazılmıştır ve iki satırı Analytics'i de
kapsayacak biçimde okur:

- **Uygulama etkinliği → Uygulama içi eylemler** — amaçlarına *Analitik* eklendi.
- **Cihaz veya diğer kimlikler** — Reklam Kimliğinin yanına Analytics ve Crashlytics kurulum
  kimlikleri de girer, amaçlarına *Analitik* eklendi. "Paylaşılır" işareti yalnızca Reklam
  Kimliğinden gelir ve olduğu gibi kalır.

Beyan edilmesi gereken başka bir satır yoktur: uygulama Analytics'e özel olay göndermediği için
oraya oyuncunun yazdığı hiçbir şey ulaşmaz, ve konum API'si hiç çağrılmaz.

**Açık kalan, engelleyici olmayan bir konu.** Reklam rızası UMP ile alınır; Analytics'in kendi
rıza modu (Consent Mode) bağlanmamıştır, dolayısıyla AEA'daki bir oyuncu reklam kişiselleştirmesini
reddettiğinde Analytics'in varsayılan toplaması devam eder. İlk yayın için Play'in istediği şey
doğru beyandır ve o yapılmıştır; AEA'da ciddi bir kullanıcı kitlesi oluşursa
`setConsent(ANALYTICS_STORAGE, ...)` çağrısını UMP sonucuna bağlamak doğru adımdır.

Crashlytics'in beyanı bu karardan bağımsızdır: kilitlenme raporlaması kullanıldığı için
§5'teki iki satır her hâlükârda beyan edilir.
