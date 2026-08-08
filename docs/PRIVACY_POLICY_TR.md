# Koridor Gizlilik Politikası

Son güncelleme: 8 Ağustos 2026

Bu metin `public/gizlilik.html` adresinde yayımlanan metindir; uygulama ona **Daha fazla →
Gizlilik Politikası** ve **Ayarlar → Hesap** üzerinden bağlanır. İkisi birlikte değiştirilir;
ikisinin de kaynağı `docs/DATA_SAFETY.md`'dir.

Koridor bir strateji oyunudur. Tek başına, bota karşı veya tek cihazda iki kişiyle, hesapsız ve
tamamen çevrimdışı oynanabilir. Çevrim içi oynamak bir hesap gerektirir; aşağıdakiler orada
geçerlidir.

## Toplanan veriler

**Hesabınız.** E-posta ile hesap açtığınızda bu adres Firebase Authentication'da ve yalnızca
sizin okuyabildiğiniz özel bir kayıtta saklanır. Google ile girdiğinizde bunun yerine Google'ın
hesabınız için döndürdüğü bilgi saklanır. Her iki durumda da bir **kullanıcı adı** seçmeniz
istenir ve bu ad herkese açıktır: diğer oyuncular onu liderlik tablosunda, profilinizde, maç
sırasında tahtada ve kendi son oyunlar listelerinde görür. Bir de **avatar** seçersiniz; avatar
sabit bir kümeden uygulamanın içinde çizilir — hiçbir görsel yüklenmez.

Misafir olarak oynamak hesap yerine cihaza ait anonim bir kimlik oluşturur. Misafirin adı
üretilmiştir, hiçbir liderlik tablosunda listelenmez ve arkadaşlık, davet ve puanlı maç
kullanamaz.

**Nasıl oynadığınız.** Puanınız, galibiyet, mağlubiyet ve beraberlik sayınız, galibiyet seriniz
ve maç sayınız hesabınızla birlikte saklanır ve herkese açıktır. Her çevrim içi maç iki oyuncuyu
adlandıran bir sonuç kaydı ve her iki oyuncunun kısa geçmişine birer satır yazar — rakibin
kullanıcı adı, sonuç, tarih ve puan değişimi — ve bu satırları giriş yapmış her oyuncu okuyabilir.

**Odalar ve maçlar.** Bir oda; kodunu, yazdığınız isteğe bağlı oda adını, ayarları, tahtadaki
durumu ve hamleleri maç sürdüğü sürece tutar. Maç sırasında sabit bir listeden seçilen hazır
ifadeler ve emojiler gönderebilirsiniz; yazdığınız hiçbir metin karşı tarafa ulaşmaz.

**Arkadaşlar.** Arkadaşlıklar, arkadaşlık istekleri, engellemeler, oyun davetleri ve kaba bir
çevrim içi/çevrim dışı bilgisi arkadaş listesinin çalışması için saklanır. Engelleme, engellenen
kişiye hiçbir zaman gösterilmez. Durum yalnızca "çevrim içi" veya "çevrim dışı"dır — konum,
adres veya son görülme zamanı değil.

**Satın almalar.** "Reklamları kaldır", Google Play tarafından yürütülen tek seferlik bir
uygulama içi üründür. Koridor kart, adres veya fatura bilgisini hiç görmez. Yalnızca satın alma
token'ının bir özetini, sipariş kimliğini ve satın alma zamanını saklar; bu, sahipliği
doğrulamak ve aynı satın almanın başka bir hesapta kullanılmasını engellemek içindir.

**Reklamlar.** Ücretsiz sürüm Google AdMob reklamları gösterir. Google; reklam kimliğinizi, IP
adresinizi, yaklaşık konumunuzu, reklam etkileşimlerinizi ve cihaz tanılama bilgilerini kendi
politikaları kapsamında işleyebilir. Gerekli olduğu yerlerde, herhangi bir reklam yüklenmeden
önce Google User Messaging Platform üzerinden rızanız istenir; reklam gizlilik tercihlerinizi
**Ayarlar → Reklam gizlilik seçenekleri**'nden yeniden açabilirsiniz. "Reklamları kaldır" satın
alındığında reklamlar hiç yüklenmez.

**Tanılama.** Firebase Crashlytics, yayın derlemelerinden kilitlenmeleri ve işlenmiş hataları
alır. Yanlarında giden tek şey exception tipi ve işlemi adlandıran sabit bir etikettir — asla
kullanıcı adınız, e-postanız, bir oda kodu veya yazdığınız bir şey değil. Crashlytics ayrıca
cihaz modelini, işletim sistemi sürümünü ve bir kurulum kimliğini toplar. Google Analytics for
Firebase de bulunur ve varsayılan olaylarını toplar; uygulama kendi olayını hiç göndermez.

## Hiçbir zaman toplanmayan veriler

Hiçbir çeşit konum bilgisi, rehber, telefon numarası, fotoğraf, video, ses, dosya, sağlık,
finans veya biyometrik veri, SMS ve arama kaydı. Sizden gelen hiçbir görsel yüklenmez —
avatarlar uygulamanın içinde sabit bir kümeden çizilir.

## Başkalarının görebildiği içerik ve şikâyet yolu

Yazdığınız iki şey sizi tanımayan oyunculara gösterilir: **kullanıcı adınız** ve bir **oda adı**.
İkisi de uygulama içinden şikâyet edilebilir.

- Bir oyuncunun profilini açın — liderlik tablosundan, son oyunlar satırından ya da maç
  sırasında rakibinize dokunarak — ve **Bildir**'i kullanın. Giriş yapmış oyuncular aynı
  sayfadan **Engelle** de diyebilir; bu, o oyuncunun sizi davet etmesini ve size istek
  göndermesini durdurur.
- Oda listesinde, bir odanın yanındaki bayrak simgesi odaya o adı veren oyuncuyu bildirir.

Bir bildirim hesabı ve kategoriyi taşır ve yalnızca geliştirici tarafından okunur. Bildirilen
oyuncuya asla gösterilmez ve onu silemez. Uygulamada başka bir oyuncuya ulaşan hiçbir serbest
metin alanı yoktur.

## İzinler

Çevrim içi maçlar, reklamlar ve satın alma denetimi için internet ve ağ durumu. Listenin
tamamı bu. Uygulama kamera, mikrofon, rehber, konum veya depolama izni istemez.

## Servis sağlayıcılar

Firebase Authentication, Firebase Realtime Database, Firebase Crashlytics, Google Analytics for
Firebase, Google Play Billing, Google AdMob, Google User Messaging Platform, Android Credential
Manager ve puanları hesaplayıp biten odaları temizleyen zamanlanmış bir Cloudflare Worker.
Veriler; oyunun yürütülmesi, güvenlik, sahtekârlığın önlenmesi, ödeme ve reklamcılık için bu
sağlayıcıların koşulları kapsamında işlenir. Koridor kullanıcı verisi satmaz.

## Verilerin saklanması ve silinmesi

Hesabınızı uygulama içinden silebilirsiniz: **Ayarlar → Hesap → Hesabı sil**. Onay olarak bir
kelime yazmanız istenir. Bu işlem kullanıcı adı rezervasyonunuzu serbest bırakır; özel
kaydınızı, herkese açık profilinizi, iki taraftaki arkadaşlık ve engelleme kayıtlarınızı,
davetlerinizi, çevrim içi durumunuzu, haftalık liderlik tablosundaki satırlarınızı ve Firebase
Authentication hesabınızı siler.

Bazı şeyler bundan sonra da kalır; nedenleri şunlardır:

- Maç sonuç kayıtları iki kullanıcı kimliğini tutar ki rakibin geçmişi eksilmesin. Artık hiçbir
  profile işaret etmezler.
- Rakiplerinizin kendi geçmiş satırları, oynadığınız kullanıcı adını tutar; oynadıkları bir
  maçın kaydı onlarındır.
- Kendi geçmiş satırlarınız veritabanında kalır ve profil gittikten sonra uygulamadan
  erişilemez. Bu düğüme hiçbir istemcinin yazma hakkı yoktur — bir yenilgiyi sildirebilmek, tam
  olarak sunucuya ait bir geçmişin engellemek için var olduğu şeydir.
- Bir satın alma token'ının özeti kalır; böylece aynı satın alma başka bir hesaba taşınamaz.
  Kişisel veri içermez.
- Google Play satın almanın kendi kaydını, Google da kendi reklam verisini tutar. İkisi de
  Google'ın politikalarına ve Google hesabı ayarlarınıza tabidir.

Otomatik temizlik: rakip bekleyen bir oda 30 dakika, oynanmış bir oda 24 saat sonra silinir;
davetler 10 dakikada geçersizleşir. Bir profilin geçmişi son on maçı tutar. Haftalık liderlik
tablosu içinde bulunulan haftayı ve bir öncekini tutar.

Uygulamayı kaldırmak cihazda saklanan her şeyi siler. Android ayarlarından uygulama verisini
temizlemek de aynı şeyi yapar; hesabınızı silmez.

**Uygulamayı zaten sildiyseniz.** Yeniden kurmanız gerekmez. furkanduzman46@gmail.com adresine,
hesabı açtığınız e-posta adresinden yazın ya da oynadığınız kullanıcı adını bildirin ve hesabınızın
silinmesini isteyin. Yukarıda anlatılan silme işlemi elle yapılır ve tamamlandığında size bildirilir.
Talepler en geç 30 gün içinde yanıtlanır.

## Çocukların gizliliği

Koridor, e-posta ile hesap açtığınızda bir e-posta adresi, çevrim içi oynayabilmeniz için de bir
kullanıcı adı ister. Gerçek ad, telefon numarası veya adres istemez. Çevrim içi oyun sizi başka
oyuncularla karşılaştırır; oda kodlarını yalnızca tanıdığınız kişilerle paylaşın.

## Değişiklikler ve iletişim

Değişiklikler bu sayfada yayımlanır ve mağaza sayfasında belirtilir. Sorular, veri talepleri ve
başka bir oyuncuyla ilgili bildirimler **furkanduzman46@gmail.com** adresine gönderilebilir.
