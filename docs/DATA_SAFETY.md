# Koridor — Veri Toplama ve Play Console Veri Güvenliği Beyanı

Bu belge, Koridor'un topladığı verileri, toplama amacını ve silme yollarını açıklar. Play
Console'daki **Veri güvenliği (Data safety)** formunu doldururken doğrudan bu belge esas
alınmalıdır.

Son güncelleme: uygulama sürümü 0.4.0.

---

## 1. Kullanılan servisler

| Servis | Ne için | Veri işlenen yer |
|---|---|---|
| Firebase Authentication | Hesap oluşturma ve giriş | Google Cloud |
| Firebase Realtime Database | Profil, oda, maç sonucu, arkadaşlık, presence | Google Cloud |
| Firebase Cloud Functions | Puan hesaplama, satın alma doğrulama, oda temizliği | Google Cloud |
| Google Play Billing | Uygulama içi satın alma | Google |
| Google Play Developer API | Satın alma token doğrulaması (sunucu tarafı) | Google |
| Google AdMob | Reklam gösterimi | Google |
| Google UMP (User Messaging Platform) | Reklam onayı / GDPR rızası | Google |
| Android Credential Manager | Google ile giriş | Cihaz + Google |

> **Not:** Firebase Cloud Messaging (FCM) **kullanılmamaktadır**. Bildirim gönderilmediği için
> bu bağımlılık projeye hiç eklenmedi.

---

## 2. Toplanan veriler

### 2.1 Hesap bilgileri

| Veri | Zorunlu mu | Amaç | Nerede saklanır | Başkaları görür mü |
|---|---|---|---|---|
| E-posta adresi | Hayır (yalnızca e-posta ile kayıtta) | Hesap kimliği, şifre sıfırlama | Firebase Auth + `usersPrivate/{uid}/email` | **Hayır** |
| Kullanıcı kimliği (UID) | Evet | Tüm verilerin anahtarı | Firebase | Dolaylı (profil erişimi için) |
| Kullanıcı adı | Evet | Oyuncuların birbirini bulması | `users/{uid}/username` | **Evet** |
| Görünen ad | Evet | Profilde gösterim | `users/{uid}/displayName` | **Evet** |
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
| Oda verileri (kod, ad, ayarlar, tahta durumu) | Çevrim içi maçın yürütülmesi | `rooms/{code}` | Açık odalarda liste görünür |
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
- Uygulama içi mesajlaşma (böyle bir özellik yoktur)

---

## 3. Loglama ve hata kayıtları

- Release derlemesinde `AppLog` yalnızca işlem adını ve exception **tipini** yazar; mesaj,
  stack trace, token, e-posta veya oda kodu yazılmaz.
- R8 kuralları (`proguard-rules.pro`) `Log.d/v/i` çağrılarını release binary'sinden tamamen
  kaldırır, böylece debug metinleri APK içinde bile bulunmaz.
- Firebase veya sunucu hata kodları kullanıcıya hiçbir zaman gösterilmez; hepsi `AppError`
  enum'una eşlenip yerelleştirilmiş genel mesajlara dönüştürülür.
- Crash reporting servisi (Crashlytics vb.) **kullanılmamaktadır**.

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
| `purchaseTokens/{hash}` | Kalır (hash) | Aynı satın almanın başka hesaba aktarılmasını engeller; kişisel veri içermez |
| Google Play satın alma kaydı | Google'da kalır | Google Play'in kendi politikası; uygulama kontrolünde değildir |
| AdMob reklam verileri | Google'da kalır | Google reklam ayarlarından yönetilir |

### 4.3 Otomatik temizlik

- Rakip beklenen odalar **30 dakika** sonra silinir
- Oynanan odalar **24 saat** sonra silinir
- Oyun davetleri **10 dakika** sonra geçersiz olur

Bu temizliği `functions/src/rooms.ts` içindeki `sweepExpiredRooms` zamanlanmış fonksiyonu yapar.

---

## 5. Play Console Veri Güvenliği formu — beyan edilmesi gerekenler

Aşağıdaki tablo forma doğrudan aktarılabilir.

### Toplanan veri türleri

| Form kategorisi | Veri türü | Toplanır | Paylaşılır | Zorunlu | Amaç |
|---|---|---|---|---|---|
| Kişisel bilgiler | E-posta adresi | ✅ | ❌ | Hayır | Hesap yönetimi |
| Kişisel bilgiler | Kullanıcı kimlikleri | ✅ | ❌ | Evet | Hesap yönetimi, Uygulama işlevselliği |
| Kişisel bilgiler | Ad (görünen ad / kullanıcı adı) | ✅ | ❌ | Evet | Uygulama işlevselliği |
| Uygulama etkinliği | Uygulama içi eylemler (maç sonuçları, istatistikler) | ✅ | ❌ | Evet | Uygulama işlevselliği |
| Uygulama etkinliği | Diğer kullanıcı tarafından oluşturulan içerik (oda adı) | ✅ | ❌ | Hayır | Uygulama işlevselliği |
| Finansal bilgiler | Satın alma geçmişi | ✅ | ❌ | Hayır | Uygulama işlevselliği |
| Cihaz veya diğer kimlikler | Cihaz veya diğer kimlikler (Reklam Kimliği) | ✅ | ✅ | Hayır | Reklamcılık |

### Güvenlik uygulamaları — form yanıtları

| Soru | Yanıt | Gerekçe |
|---|---|---|
| Veriler aktarımda şifreleniyor mu? | **Evet** | Tüm Firebase ve Play trafiği TLS üzerinden |
| Kullanıcı verilerinin silinmesini isteyebiliyor mu? | **Evet** | Uygulama içi "Hesabı sil" akışı |
| Veriler bağımsız bir güvenlik incelemesinden geçti mi? | Hayır | Böyle bir inceleme yapılmadı |
| Aile Politikası kapsamında mı? | Hedef kitleye göre belirlenmeli | 13 yaş altına özel hedefleme yapılmıyor |

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
