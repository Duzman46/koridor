# Koridor — Manuel Test Senaryoları

Otomatik testler saf mantığı kapsar (240 JVM testi, 192 güvenlik kuralı testi). Bu belge,
**yalnızca gerçek cihazda doğrulanabilecek** senaryoları listeler: ağ davranışı, iki cihaz
arası eşzamanlılık, sistem diyalogları ve görsel yerleşim.

Her senaryonun sonunda beklenen davranış yazılıdır. Bir madde geçmezse, hangi adımda
başarısız olduğu not edilmelidir.

**Ön koşullar:** `firebase.properties`, `monetization.properties`, `app.properties` ve
`keystore.properties` doldurulmuş; `firebase deploy --only database` yapılmış ve `worker/`
yayınlanmış olmalı. `functions/` **yayınlanmaz** — aynı işin ikinci uygulamasıdır ve worker'ın
son turlarda eklenen işlerini içermez; ayrıntı için `README.md`.

---

## A. Çevrim içi maç — iki gerçek cihaz

### A1. Oda oluşturma ve kodla katılma
1. Cihaz 1: Çevrim İçi Oyna → Oda oluştur → oda adı gir, dereceli açık.
   **Renk kontrolüne dokunmayın**, yalnızca hangi rengin seçili göründüğünü not edin: renk
   form açılırken çekilir, dolayısıyla her seferinde mavi olmaz. → Oda oluştur
2. Cihaz 1: 6 karakterli kod görünmeli, bekleme ekranı açılmalı
3. Cihaz 2: Çevrim İçi Oyna → Kodla katıl → kodu gir → Katıl
4. **Beklenen:** İki cihazda da tahta açılır. Cihaz 1'in piyonu 1. adımda seçili görünen
   renktedir; mavi olan başlar ve karşı taraf tahtayı ters çevrilmiş görür.
5. Formu tekrar açıp not ettiğinizin tersine dokunun → **Beklenen:** dokunulan renk seçili
   hâle gelir, diğeri bırakılır ve oluşturulan odada Cihaz 1 o renktedir.

### A2. Açık odalar listesinden katılma
1. Cihaz 1: Oda oluştur (her oda listelenir; artık seçilecek bir görünürlük yok)
2. Cihaz 2: Açık odalar listesi kendiliğinden tazelenir — **on beş saniye içinde**, hiçbir
   şeye dokunmadan
3. **Beklenen:** Oda listede görünür; oda adı, sahibinin kullanıcı adı ve puanı, süre, dereceli/derecesiz ve 1/2 oyuncu sayısı doğru.
4. Cihaz 2: Odaya dokun → maç başlar, oda listeden kaybolur.

### A3. Şifreli oda
1. Cihaz 1: Oda oluştur → şifre `1234` (en az 4 karakter)
2. Cihaz 2: Listeden odaya dokun → şifre sorulur → **yanlış** şifre gir
3. **Beklenen:** "Oda şifresi hatalı" mesajı, maç başlamaz.
4. Doğru şifreyi gir → maç başlar.

### A4. Aynı anda hamle gönderme (yarış koşulu)
1. Maç sürerken iki cihazda da aynı anda hamle yapmayı deneyin (sırası olmayan taraf da dokunsun)
2. **Beklenen:** Sırası olmayan oyuncunun dokunuşu hiçbir şey yapmaz. Tahta iki cihazda da aynı kalır, tur numarası tek artar. Hiçbir cihazda "hayalet" hamle görünmez.

### A5. Hızlı eşleşme — sıraya girme
Hızlı eşleşme artık oda açmaz; oyuncuyu bir bekleme listesine yazar. Asıl sınav 3. maddedir:
eskiden iki oyuncu aynı anda bastığında ikisi de kendi odasında kalıp birbirini hiç bulmuyordu.

1. Cihaz 1: Hızlı eşleşme → **Beklenen:** "Rakip aranıyor…" ekranı; **oda kodu gösterilmez**
   (girilecek bir oda yok, arkadaş davet listesi de çıkmaz).
2. Cihaz 2: Hızlı eşleşme → **Beklenen:** iki cihazda da maç birkaç saniye içinde başlar.
3. Aynı anda: iki cihazda da Hızlı eşleşme'ye **aynı anda** basın → **Beklenen:** yine tek bir
   maç açılır, iki cihaz da aynı tahtaya girer.
4. Renkler çekilişle belirlenir: 3. maddeyi birkaç kez tekrarlayın → **Beklenen:** hangi cihazın
   mavi olduğu değişir, hiçbir yerde renk seçtiren bir kontrol çıkmaz, mavi olan başlar.
5. Cihaz 1: Hızlı eşleşme → İptal → Cihaz 2: Hızlı eşleşme → **Beklenen:** Cihaz 2 eşleşmez,
   beklemede kalır (Cihaz 1 sıradan çıkmıştır).
6. Cihaz 1: Hızlı eşleşme → uygulamayı arka plana al → Cihaz 2: Hızlı eşleşme →
   **Beklenen:** Cihaz 2 eşleşmez. Cihaz 1 geri geldiğinde de maça düşmüş olmaz.
7. Cihaz 1: Hızlı eşleşme → uygulamayı görev listesinden **tamamen kapat** → Cihaz 2: Hızlı
   eşleşme → **Beklenen:** Cihaz 2 eşleşmez; kapanan cihazın adı listeden düşmüştür.

### A6. Maç içi hazır mesajlar
Yazılabilen hiçbir şey yok: gönderilebilecek her şey sabit bir listedir. Sınav, listenin kapalı
kalması ve tahtanın hiçbir zaman rahatsız edilmemesidir.

1. Maç sürerken iki cihazda da tahtanın üstündeki gülen yüz düğmesine dokun →
   **Beklenen:** alttan bir sayfa açılır; altı emoji ve sekiz hazır ifade vardır, **hiçbir yerde
   yazı yazılacak bir alan yoktur**.
2. Cihaz 1: "Bol şans" seç → **Beklenen:** sayfa kapanır, kendi tarafında yalnızca emoji görünür;
   Cihaz 2'de emoji ve yazı birlikte görünür, birkaç saniye sonra ikisi de kendiliğinden
   kaybolur — kapatmak için hiçbir şeye dokunmak gerekmez.
3. Cihaz 1: hemen ikinci bir mesaj göndermeyi dene → **Beklenen:** hata sesi gelir, mesaj gitmez.
   Üç saniye bekleyip tekrar dene → gider.
4. Mesaj satırı maç boyunca **aynı yüksekliktedir**: mesaj gelip gitse de tahta ne büyür ne
   küçülür, kareler yerinden oynamaz.
5. Duvar modundayken gülen yüz düğmesinin hemen altına, tahtanın üst kenarına dokun →
   **Beklenen:** dokunuş tahtaya gider, düğmeye değil; yanlışlıkla mesaj sayfası açılmaz.
6. Cihaz 2: Ayarlar → Oyun deneyimi → **Maç mesajları**'nı kapat → maça dön →
   **Beklenen:** mesaj satırı tamamen kaybolur; Cihaz 1 mesaj gönderse de hiçbir şey görünmez.
7. Cihaz 2: mesajları geri aç → mesaj seçicisini aç → **"Mesajları kapat"** bağlantısına dokun →
   **Beklenen:** sayfa kapanır, satır kaybolur, ayar da kapanmıştır (Ayarlar ekranında görülür).
8. Bot maçında ve aynı cihazda iki kişilik maçta → **Beklenen:** mesaj satırı hiç yoktur.

---

## B. Bağlantı ve dayanıklılık

### B1. Maç sırasında interneti kapatma
1. Maç sürerken Cihaz 2'de uçak modunu aç
2. Cihaz 2'de hamle yapmayı dene
3. **Beklenen:** Hamle gitmez, "Yeniden bağlanılıyor…" görünür, uygulama **çökmez**.
4. Uçak modunu kapat → **Beklenen:** Tahta otomatik olarak güncel duruma senkronize olur.

### B2. Maçı terk etme
Bir maçın açık kalıp kimseyi beklememesi gerekir; geri dönülecek bir maç artık yoktur.

1. Maç sürerken Cihaz 2'de geri tuşuna bas → **Beklenen:** "çıkarsan maçı kaybedersin" uyarısı
2. Onayla → **Beklenen:** Cihaz 2 doğrudan ana menüye çıkar — kapıyı isteyen oyuncuya giderken
   skor tabelası gösterilmez. Cihaz 1'de kazanan ekranı açılır ve "Rakibin maçtan ayrıldı." der.
3. Cihaz 2: Çevrim İçi Oyna → **Beklenen:** Lobide "maçına dön" gibi bir kart **yoktur**;
   biten maç açık odalar listesinde de görünmez.
4. Ayrı bir maçta Cihaz 2'de uygulamayı **görev listesinden tamamen kapat** ve on dakika bekle
   → **Beklenen:** Cihaz 1'de maç kendiliğinden biter, sırası gelen taraf kaybeder.

### B3. Kısa kopma yenilgi sayılmamalı
1. Maç sürerken Cihaz 2'yi 10 saniye uçak moduna al, sonra geri getir
2. **Beklenen:** Maç devam eder, kimse kaybetmez.

### B4. Süre aşımı
Süresi biten tur maçı kendisi bitirir; kimsenin bir şeye basması gerekmez.

1. Oda oluştururken **hamle süresi 30 sn** seç (toplam maç süresi diye bir ayar yoktur)
2. Sırası gelen oyuncu hiçbir şey yapmasın
3. **Beklenen:** Son beş saniyede sayaç kırmızıya döner ve uyarı sesi **yalnızca sırası olan
   oyuncuda** bir kez çalar.
4. **Beklenen:** 0:00'da maç iki cihazda da kendiliğinden biter, kazanan doğru, kazanan
   ekranında "süre doldu" yazar. Basılacak bir "galibiyeti al" butonu **yoktur**.

### B5. Pes etme
1. Maç sürerken üst çubuktaki bayrak simgesine bas → onayla
2. **Beklenen:** Rakip kazanır, kazanan ekranı iki cihazda da doğru sonucu gösterir.

### B6. Process death ve ekran döndürme
1. Geliştirici Seçenekleri → "Etkinlikleri koru" (Don't keep activities) **açık**
2. Maç sırasında uygulamayı arka plana al, geri dön
3. Ekranı yatay/dikey çevir
4. **Beklenen:** Tahta durumu, sıra ve sayaç kaybolmaz; çökme yok.

---

## C. Hesap akışları

### C1. Misafir → hesap taşıma (kritik)
1. Temiz kurulum → **Misafir olarak oyna**
2. Öğreticiyi tamamla, birkaç yerel maç oyna, profilden kullanıcı adını değiştir
3. Ayarlar → Hesap → **E-posta ile bağla** (veya Google ile bağla)
4. **Beklenen:** Aynı kullanıcı adı, aynı öğretici durumu, aynı istatistikler korunur. "Misafir" rozeti kaybolur. Arkadaşlar ve dereceli maç açılır.

### C1b. Misafir liderlik tablosunda görünmemeli (kritik)
1. Misafirken **Liderlik Tablosu** → Genel ve Haftalık sekmelerini aç
2. **Beklenen:** Kendi adın hiçbir sekmede yok; alttaki çubuk sıra yerine "Misafirler liderlik tablosunda yer almaz" der. Tablo yine de okunabilir.
3. Ayarlar → Hesap → Google ile bağla → kullanıcı adı ekranı gelir, bir ad seç
4. **Beklenen:** Artık tablodasın; **seçtiğin adla** ve misafirken taşıdığın puanla.

### C1c. Adı verilmemiş hesap tabloya çıkmamalı (kritik)
Bağlanmak hesabı gerçek yapar; ad vermek bir form sonra gelir. Tabloya yazma eskiden bağlanma
anındaydı, yani aradaki saniyelerde oyuncu herkese açık tabloda `guest_######` olarak
duruyordu — ve uygulama o anda kapatılırsa orada kalıyordu. Artık tablodaki yeri ad belirler.
1. Misafir olarak gir, birkaç maç oyna
2. Ayarlar → Hesap → **E-posta ile bağla** → kullanıcı adı ekranı gelir
3. **Ad yazmadan uygulamayı tamamen kapat** (son kullanılanlar listesinden kaydır)
4. **En az iki dakika bekle.** Bu bekleme testin parçasıdır: tabloya çıkarabilecek ikinci el
   dakikada bir çalışan sunucudur ve asıl sınav odur
5. İkinci cihazda (ya da misafir olarak) **Liderlik Tablosu → Genel**, listeyi sonuna kadar aç
6. **Beklenen:** `guest_` ile başlayan hiçbir ad yok
7. Birinci cihazda uygulamayı aç → **Beklenen:** doğrudan kullanıcı adı ekranı gelir; bir ad ver
8. Liderlik Tablosu → **Beklenen:** yeni ad tabloda, misafirken taşıdığın puanla

### C2. Google girişini iptal etme
1. Karşılama ekranı → Google ile devam et → hesap seçiciyi **geri tuşuyla kapat**
2. **Beklenen:** Hiçbir hata mesajı gösterilmez, ekran olduğu gibi kalır, buton tekrar basılabilir.

### C3. E-posta doğrulama ve hata mesajları
1. Kayıt: geçersiz e-posta (`abc`) → **Beklenen:** "Geçerli bir e-posta adresi gir."
2. Kayıt: 7 karakterli şifre → **Beklenen:** "Şifren en az 8 karakter olmalı."
3. Kayıt: eşleşmeyen şifreler → **Beklenen:** "Şifreler eşleşmiyor."
4. Var olan e-posta ile kayıt → **Beklenen:** "Bu e-posta adresiyle zaten bir hesap var."
5. **Hiçbir mesajda Firebase hata kodu görünmemeli.**

### C4. Şifremi unuttum
1. Kayıtlı olmayan bir adres gir → gönder
2. **Beklenen:** Yine de "bağlantı yola çıktı" mesajı (hesap varlığı sızdırılmaz).

### C5. Kullanıcı adı benzersizliği
1. Cihaz 1'de `koray` kullanıcı adını al
2. Cihaz 2'de `KORAY` almayı dene
3. **Beklenen:** "Bu kullanıcı adı alınmış." (büyük/küçük harf farkı korumaz)

### C6. Hesap silme
1. Ayarlar → Hesap → Hesabı sil → uyarıyı oku → onay kelimesini yaz → sil
2. **Beklenen:** Karşılama ekranına dönülür. Aynı e-posta ile tekrar giriş **yapılamaz** (hesap yok).
3. Silinen kullanıcının arkadaş listesindeki bir hesapla giriş yap → **Beklenen:** Silinen kişi listede yok.

### C7. Aynı cihazda ikinci hesap — kullanıcı adı kapısı (kritik)
Öğreticiyi bir kez bitirmiş bir cihazda, **uygulamayı hiç kapatmadan** ikinci bir hesaba
geçmek kapıyı atlatabiliyordu: giriş ekranı, o ekran çizildiği andaki oturumu okuyordu ve o
oturum hâlâ önceki oyuncuya aitti.
1. Misafir olarak gir, öğreticiyi bitir, ana menüye ulaş
2. Ayarlar → Hesap → **Çıkış yap** (uygulamayı kapatma)
3. Karşılama ekranı → daha önce hiç kullanılmamış bir e-posta ile **yeni hesap oluştur**
4. **Beklenen:** Öğretici tekrar sorulmaz ama **kullanıcı adı ekranı gelir** ve geri tuşu onu
   kapatmaz. Ad seçilmeden ana menüye ulaşılamaz.
5. Aynı adımları Google ile girişte tekrarla → **Beklenen:** aynı.

### C8. Misafirken zaten var olan bir hesaba geçme (kritik)
Bu akış "Giriş yapıldı" yazıp hiçbir şey yapmamış olabiliyordu: çağrılar dönüyordu ama oturum
önceki oyuncuda kalıyordu. Sınav, ekrandaki cümlenin ekrandaki verilerle aynı hesabı
anlatmasıdır.
1. Cihaz 1'de bir Google hesabıyla giriş yap, kullanıcı adı ver, **dereceli bir maç oynayıp
   kazan** (puanı 1000'den farklı olsun), çıkış yap
2. Aynı cihazda misafir olarak gir ve **en az beş dakika** oyna. Bu bekleme testin parçasıdır:
   Firebase, birkaç dakikadan eski bir misafir oturumunu silmeyi reddeder ve hata yalnızca o
   reddin ardından ortaya çıkıyordu
3. Ayarlar → Hesap → **Google ile bağla** → 1. adımdaki hesabı seç
4. **Beklenen:** "Bu hesap zaten kullanılıyor" uyarısı çıkar ve neyin kaybedileceğini söyler
5. **Misafir kal**'a dokun → **Beklenen:** hiçbir şey değişmez; misafirin puanı, adı ve
   istatistikleri yerinde. Ekrandan çıkıp geri gel → **Beklenen:** aynı teklif kendiliğinden
   tekrar açılmaz
6. 3. adımı tekrarla ve bu kez **devam et** → **Beklenen:** bekleme boyunca ekranın üstünde
   bir ilerleme çubuğu vardır; iş bitince "Giriş yapıldı" yazar **ve** aynı ekranda artık
   1. adımdaki hesabın puanı ile istatistikleri durur, "İlerlemeni koru" kartı **yoktur**
7. Profili ve liderlik tablosunu aç → **Beklenen:** ikisi de 1. adımdaki hesabı gösterir,
   misafirin puanını değil
8. Uçak modunu açıp 3–6. adımları tekrarla → **Beklenen:** "Bu hesaba geçilemedi. Misafir
   ilerlemen artık yok…" hatası ekranda **kalır**; başka bir ekrana atılmazsın

### C9. Arkadaş olmayan bir rakibe gönderilmiş rövanş isteğinin silinmesi (kritik)
Silme, karşı tarafların düğümlerini arkadaş listesini gezerek bulur. Rövanş isteği ise
arkadaşlık değil biten maç karşılığında gönderilir, yani listede olmayan birine de gidebilir —
ve bir davet kutusunu yalnızca sahibi okuyabildiği için giden hesap onu bulamaz. Bu senaryo,
kaydı sunucunun topladığını doğrular. Sonucu uygulamadan görülemez; **Firebase Console →
Realtime Database** gerekir.
1. Cihaz 1 ve 2'de birbiriyle **arkadaş olmayan** iki hesapla giriş yap
2. Aralarında çevrim içi bir maç oyna ve bitir
3. Cihaz 1: kazanan/kaybeden ekranında **Rövanş** iste. Cihaz 2'de üstte çubuk belirmeli —
   **cevaplama**
4. Console → `invites/<cihaz 2'nin uid'si>/<cihaz 1'in uid'si>` → **Beklenen:** kayıt duruyor
5. Cihaz 1: Ayarlar → Hesap → **Hesabı sil**
6. **En çok iki dakika bekle** (sunucu dakikada bir çalışır ve bu iş sıranın sonundadır)
7. Console → aynı yol → **Beklenen:** kayıt **yok**; `invites/<cihaz 2'nin uid'si>` düğümü
   başka kaydı kalmadıysa tamamen kaybolmuş olmalı
8. Cihaz 2'de uygulamayı aç → **Beklenen:** silinen oyuncudan gelen bir çubuk yok

---

## D. Arkadaşlık, davet ve rövanş

### D0. Arkadaşlar ekranına ulaşma
1. Ana menü → **sol üstteki arkadaş simgesi** → **Beklenen:** Arkadaşlar ekranı açılır.
2. Ana menü → "Diğer" → **Beklenen:** listede arkadaşlar girişi **yoktur** (tek yol simgedir).
3. Ana menü → sağ üstteki profil arması → **Beklenen:** profil sayfasından da Arkadaşlar'a
   gidilebilir.
4. Dili العربية yap ve 1. maddeyi tekrarla → **Beklenen:** simge sağ üste geçer, ayarlar/dil
   armaları sola; hiçbiri tahtanın duvar rafıyla çakışmaz.

### D1. Arkadaş ekleme
1. Cihaz 2'nin kullanıcı adını Cihaz 1'de ara → Arkadaş ekle
2. **Beklenen:** Cihaz 2'de "Gelen istekler" bölümünde görünür → Kabul et → iki tarafta da "Çevrim içi/dışı" listesine geçer.

### D2. Çevrim içi durumu
1. İki cihazda da giriş yap, arkadaş olun
2. Cihaz 2'de uygulamayı tamamen kapat
3. **Beklenen:** Cihaz 1'de arkadaş kısa süre içinde "Çevrim dışı" bölümüne geçer (yeşil nokta kaybolur).

### D3. Arkadaş engelleme (kritik)
1. Cihaz 1: arkadaşı **Engelle**
2. Cihaz 2: Cihaz 1'e arkadaşlık isteği göndermeyi dene
3. **Beklenen:** İstek gönderilemez. Cihaz 2'de "engellendiniz" gibi bir mesaj **görünmez** (sessiz başarısızlık).
4. Cihaz 1: Engeli kaldır → istek tekrar gönderilebilir olmalı.

### D4. Oyuna davet — iki yol
1. Cihaz 1: Oda oluştur → bekleme ekranında arkadaş listesinden Cihaz 2'yi davet et
2. **Beklenen:** Cihaz 2'nin Arkadaşlar ekranında davet görünür → Katıl → doğrudan o odaya girer.
3. Cihaz 1: Arkadaşlar → arkadaş satırındaki **oyun kolu simgesi** → **Beklenen:** liste yerini
   bekleme paneline bırakır: "… bekleniyor", oda kodu ve "Odayı kapat".
4. Cihaz 2 daveti kabul edince → **Beklenen:** iki cihazda da tahta açılır; geri tuşu
   arkadaşlar ekranına değil, ana menüye çıkar (oda paneli geride bırakılmaz).
5. Cihaz 1: 3. maddeyi tekrarla, ama bu kez **Odayı kapat**'a bas → **Beklenen:** panel kapanır
   ve Cihaz 2'nin daveti artık bir odaya götürmez.

### D5. İstek çubuğu — davet nerede olursan ol gelir
Rövanş ve oyun daveti aynı canlı kanalda gider; ikisi de ekranın tepesinde tek bir çubukta çıkar.

1. Cihaz 2: **oyun ekranında** dur (bota karşı bir maç açık olsun)
2. Cihaz 1: Arkadaşlar → Cihaz 2'yi oyuna davet et
3. **Beklenen:** Cihaz 2'de tepede bir çubuk belirir — "… seni maça davet etti", kabul ve ret
   simgeleriyle. Çubuk, ekranın **geri butonunun üstüne binmez**, onun altında durur.
4. Çubuğun **dışına** dokun → **Beklenen:** dokunuş alttaki ekrana geçer, çubuk kaybolmaz.
5. Kabul et → **Beklenen:** bot maçı kapanır, yerine çevrim içi tahta açılır; geri tuşu
   **ikinci bir tahtaya çıkmaz** (üst üste binmiş iki maç yok).
6. Tekrar dene, bu kez **reddet** → **Beklenen:** çubuk kapanır, Cihaz 1'in odası bekler.
7. Şifreli bir odaya davet et → kabul et → **Beklenen:** çubukta şifre sorulmaz; lobiye kod
   girilmiş hâlde gidilir.
8. Sistem Ayarları → Erişilebilirlik → **Animasyonları kaldır** açıkken tekrarla →
   **Beklenen:** çubuk kayarak değil, doğrudan belirir.

### D6. Rövanş
1. İki cihazla çevrim içi bir maç oyna ve bitir
2. **Beklenen:** Kazanan ekranında "Rövanş" ve altında "Başka bir oyun" düğmeleri; bota veya
   aynı telefonda oynanan maçtan sonra yalnızca eski "Tekrar oyna" düğmesi çıkar.
3. Cihaz 1: **Rövanş** → **Beklenen:** "Rakibinin yanıtı bekleniyor…"; Cihaz 2'de tepede
   "… rövanş istiyor" çubuğu belirir.
4. Cihaz 2: Kabul et → **Beklenen:** iki cihazda da yeni maç açılır ve **renkler ilk maça göre
   yer değiştirir** — ilk maçta mavi olan bu kez kırmızıdır; yine mavi olan başlar.
5. Tekrarla, bu kez Cihaz 2 **reddetsin** → **Beklenen:** Cihaz 1'de "Rakibin rövanşı kabul
   etmedi." yazar, "Başka bir oyun" ve "Ana menü" hâlâ tek dokunuş uzakta.
6. Tekrarla, bu kez Cihaz 1 yanıt gelmeden **Ana menü**'ye çıksın → **Beklenen:** Cihaz 2'deki
   çubuk kaybolur ve o oda kapanmıştır (kod lobide işe yaramaz).
7. Cihaz 1 misafir hesapla oynasın → **Beklenen:** kazanan ekranında Rövanş **yoktur**, düğme
   eski hâliyle "Tekrar oyna"dır.

### D7. Rakibin profili
1. Çevrim içi maç sürerken tur başlığındaki **rakip adına** dokun
2. **Beklenen:** rakibin profili açılır — kullanıcı adı, avatarı, puanı ve "Arkadaş ekle".
   Sayfada "Profili düzenle" gibi kendi hesabına ait hiçbir düğme **yoktur**.
3. Arkadaş ekle → Cihaz 2 kabul etsin (sayfa açıkken) → **Beklenen:** düğme kendiliğinden
   "Arkadaşsınız"a döner.
4. Bota karşı ve aynı telefonda oynanan maçlarda tur başlığı → **Beklenen:** isimler düz yazıdır,
   dokunulacak bir şey yoktur.
5. Liderlik tablosunda bir satıra dokun → **Beklenen:** aynı profil sayfası açılır. Ekranın
   altına sabitlenmiş **kendi** sıralamana dokunmak hiçbir şey yapmaz.
6. Liderlik tablosunda **kendi** satırına dokun → **Beklenen:** sayfa açılır ama arkadaşlık
   düğmesi hiç çıkmaz.

### D8. Profilde son oyunlar
Listeyi sunucu yazar, telefon değil: bir maç bittikten sonra satırın görünmesi **bir dakikaya
kadar** sürebilir. Erken bakıp "gelmedi" demek bu senaryonun tek tuzağıdır.
1. İki cihazla dereceli bir maç oynayıp bitir → bir dakika bekle → kendi profilini aç
2. **Beklenen:** "Son oyunlar" başlığı altında maç durur: rakibin adı, tarih, "Galibiyet" ya
   da "Mağlubiyet" ve `+12` gibi işaretli bir puan değişimi
3. Cihaz 2'de aynı maç → **Beklenen:** aynı satır, ters sonuç ve ters işaretli puan
4. Dereceli olmayan bir maç oyna → **Beklenen:** o da listede, ama puan yerine "Derecesiz"
   yazar — **sıfır değil**
5. Üçten fazla maç oynanmış bir hesapta → **Beklenen:** yalnızca üç satır ve altında
   "Tümünü göster (n)"; dokununca hepsi açılır, "Daha az göster" geri toplar
6. Üç ya da daha az maçı olan bir hesapta → **Beklenen:** "Tümünü göster" satırı hiç yoktur
7. Hiç çevrim içi oynamamış bir hesapta → **Beklenen:** "Henüz çevrim içi oyun oynanmadı."
8. Uçak modunda profili aç → **Beklenen:** bölüm hiç çizilmez; hata da yazmaz, "hiç
   oynamadın" da demez
9. Rakibinin profilini aç (tur başlığındaki adına dokunarak) → **Beklenen:** aynı bölüm
   orada da var ve **onun** maçlarını gösterir
10. Profil ekranını varsayılan yazı tipi ölçeğiyle aç → **Beklenen:** dört düğme kaydırmadan
    görünür ve "Son oyunlar" başlığı ekranın alt ucundan görünür — bölümün düğmelerin
    *altında* durmasının tek sebebi budur

### D9. Bildirme ve engelleme
Play'in kullanıcı içeriği yükümlülüğünün karşılığı. Yazdığı görülen iki alan var — kullanıcı
adı ve oda adı — ve ikisinin de bir bildirme yolu olmak zorunda. Bildirimi hiçbir istemci
okuyamaz, dolayısıyla doğrulaması veritabanı konsolundan yapılır.

1. Cihaz 2 ile bir maç oyna, maç sırasında rakibin adına dokun → profil açılır
2. **Beklenen:** sayfada "Bildir" ve "Engelle" düğmeleri var
3. "Bildir" → **Beklenen:** dört gerekçe listelenir, yazılacak bir alan yoktur
4. Bir gerekçe seç → **Beklenen:** "Teşekkürler. Bildirimin gönderildi." yazar
5. Konsolda `contentReports/{bildirilenin uid}/{bildirenin uid}` → **Beklenen:** gerekçe ve
   sunucu zaman damgası duruyor
6. Cihaz 2'de aynı profili aç → **Beklenen:** bildirimden hiçbir iz yok, bir bildirim aldığı
   hiçbir yerde yazmıyor
7. Cihaz 1: "Engelle" → onayla → Cihaz 2'den oyuna davet et → **Beklenen:** davet gitmiyor
8. Cihaz 2: Cihaz 1'i **o da** engellesin (Arkadaşlar → ara → Engelle) → **Beklenen:** engel
   uygulanır, hata çıkmaz; "Engellenenler" bölümünde görünür
9. Çevrim İçi Oyna → açık odalar listesindeki bir odanın yanındaki bayrak → **Beklenen:** aynı
   liste açılır, seçince bildirilen kişi odayı açan oyuncudur
10. Misafir olarak gir, bir oyuncunun profilini aç → **Beklenen:** "Bildir" var, "Engelle" yok
    ve arkadaşlığın hesap gerektirdiği yazıyor

---

## E. Satın alma

### E1. Satın alma iptali
1. Mağaza → Satın al → Google Play diyaloğunu **geri tuşuyla kapat**
2. **Beklenen:** Hata mesajı yok, uygulama çökmez, ürün hâlâ satın alınabilir görünür.

### E2. Satın alımları geri yükleme
1. Ürünü satın al (test hesabıyla) → uygulamayı sil → yeniden kur → aynı hesapla giriş yap
2. Mağaza → **Satın alımları geri yükle**
3. **Beklenen:** Ürün "Sahipsin" olur, reklamlar kaybolur.

### E3. Hesap değiştirince entitlement karışmaması (kritik)
1. Hesap A ile Premium satın al → reklamlar kalkar
2. Çıkış yap → Hesap B ile giriş yap
3. **Beklenen:** Hesap B'de reklamlar **görünür**, Premium aktif değil.
4. Tekrar Hesap A'ya dön → Premium geri gelir.

### E4. Misafir uyarısı
1. Misafirken Mağaza'yı aç
2. **Beklenen:** "Misafir olarak oynuyorsun… hesabını bağla" uyarısı görünür.

### E5. Bekleyen satın alma
1. Test hesabında "yavaş test kartı" (pending) ile satın al
2. **Beklenen:** "Ödeme bekleniyor" gösterilir, ürün **açılmaz**, uygulama hata vermez. Ödeme onaylanınca otomatik açılır.

---

## F. Dil, RTL ve erişilebilirlik

### F1. 10 dilin tamamı
Ayarlar → Dil → her dili tek tek seç. Her birinde kontrol et:
- Ana menü, oyun ekranı, ayarlar, mağaza, arkadaşlar ekranlarında **çevrilmemiş metin kalmamalı**
- Butonlarda metin **taşmamalı** veya kırpılmamalı
- **Beklenen dil listesi:** Türkçe, English, Español, Português (Brasil), Deutsch, Français, Русский, العربية, Bahasa Indonesia, हिन्दी

### F2. Arapça RTL (kritik)
1. Dil → العربية
2. **Beklenen:**
   - Tüm ekranlar sağdan sola akar
   - Geri oku ve "giriş" oku **ters yöne** bakar
   - Oyun **tahtası ters çevrilmez** (tahta bir metin değildir; 9×9 düzeni aynı kalmalı)
   - Liste satırlarında avatar sağda, sayılar solda

### F3. Cihaz dili ve yedek dil
1. Cihaz dilini desteklenmeyen bir dile ayarla (ör. Japonca), uygulama dilini "Cihaz dili" yap
2. **Beklenen:** Uygulama **İngilizce** açılır (boş ekran veya karışık dil değil).

### F4. Android 13+ sistem dil ayarı
1. Sistem Ayarları → Uygulamalar → Koridor → Dil
2. **Beklenen:** 10 dil listelenir; buradan yapılan seçim uygulamaya yansır.

### F5. Türkçe karakterler
Tüm ekranlarda `ç ğ ı İ ö ş ü` doğru görünmeli; özellikle **büyük harfe çevrilen** yerlerde
(`İ` yerine `I` çıkmamalı).

### F6. Pseudolocale ile taşma testi
1. Debug derlemesi kur → Geliştirici Seçenekleri → Dil → **English (XA)**
2. **Beklenen:** Metinler ~%40 uzasa da hiçbir buton veya kart taşmaz.
3. **English (XB)** → düzen RTL'ye döner, F2'deki kontrolleri tekrarla.

### F7. TalkBack
1. TalkBack'i aç
2. **Beklenen:**
   - Tahta "Oyun tahtası, 9'a 9 kare" olarak okunur
   - Simge-butonların hepsi (geri, geri al, pes et, arkadaş ekle/engelle) adıyla okunur
   - Öğreticide yanlış hamle yapılınca ipucu **otomatik okunur** (live region)
   - Şifre göster/gizle butonu durumuna göre doğru okunur

### F8. Küçük ekran ve büyük yazı tipi
1. Ayarlar → Ekran → Yazı tipi boyutu **en büyük**, Görüntüleme boyutu **en büyük**
2. Küçük ekranlı bir cihazda (veya 4.7" emülatör) tüm ekranları gez
3. **Beklenen:** Metin kırpılmaz, butonlara erişilebilir, dil seçici satır atlar (FlowRow), oyun tahtası kare kalır.

### F9. Karanlık mod
1. Sistem karanlık moduna geç
2. **Beklenen:** Açılışta beyaz "flash" yok; tüm ekranlar okunabilir; tahta temaları doğru.

---

## G. Öğretici

### G1. İlk açılış akışı
1. Temiz kurulum → misafir olarak gir
2. **Beklenen:** Öğretici otomatik açılır ve **atlanmadan/tamamlanmadan gerçek maç başlatılamaz**.

### G2. Altı adımın tamamı
Her adımda yalnızca istenen hamle kabul edilmeli:
1. Taş seçme — başka yere dokunmak ilerletmemeli
2. İlerleme — yanlış kareye dokunmak "O değil" ipucu vermeli
3. **Atlama** — rakibin üzerinden atlanabilmeli
4. Duvar koyma — duvar modunda doğru aralığa
5. **Yolu kapatan duvar** — reddedilmeli, kırmızı gösterilmeli, adım yine de geçilmeli
6. Kazanma — son sıraya ulaşınca biter

### G3. Atlama ve tekrar oynatma
1. Öğreticide "Atla" → onayla → **Beklenen:** ana menüye gidilir, bir daha otomatik açılmaz
2. Ayarlar → Hesap → **Öğreticiyi tekrar oyna** → baştan açılır
3. **Beklenen:** Öğretici istatistikleri **etkilemez** (İstatistikler ekranında maç sayısı artmaz).

---

## H. Release derlemesi

### H1. Temiz kurulum
1. `./gradlew :app:bundleRelease` → `bundletool` ile APK üret → cihaza kur
2. **Beklenen:** Uygulama açılır, giriş yapılabilir, çevrim içi maç oynanabilir.

### H2. R8 sonrası doğrulama (kritik)
Release derlemesinde şunların hepsi çalışmalı — R8 bir şeyi yanlışlıkla sildiyse burada
ortaya çıkar:
1. Google ile giriş
2. Firebase profil okuma/yazma
3. Çevrim içi maç (Realtime Database dinleyicileri)
4. Mağaza ürün listesi (Play Billing)
5. Reklam gösterimi

### H3. Loglama sızıntısı kontrolü
1. Release derlemesini çalıştırırken `adb logcat | grep Koridor`
2. **Beklenen:** Yalnızca `operation failed: ExceptionType` biçiminde satırlar; **e-posta, token, oda kodu veya stack trace yok**.
