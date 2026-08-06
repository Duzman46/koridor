# Koridor — Manuel Test Senaryoları

Otomatik testler saf mantığı kapsar (154 JVM testi). Bu belge, **yalnızca gerçek cihazda
doğrulanabilecek** senaryoları listeler: ağ davranışı, iki cihaz arası eşzamanlılık, sistem
diyalogları ve görsel yerleşim.

Her senaryonun sonunda beklenen davranış yazılıdır. Bir madde geçmezse, hangi adımda
başarısız olduğu not edilmelidir.

**Ön koşullar:** `firebase.properties`, `monetization.properties`, `app.properties` ve
`keystore.properties` doldurulmuş; `firebase deploy --only database,functions` yapılmış olmalı.

---

## A. Çevrim içi maç — iki gerçek cihaz

### A1. Oda oluşturma ve kodla katılma
1. Cihaz 1: Çevrim İçi Oyna → Oda oluştur → oda adı gir, "Sadece kodla", dereceli açık → Oda oluştur
2. Cihaz 1: 6 karakterli kod görünmeli, bekleme ekranı açılmalı
3. Cihaz 2: Çevrim İçi Oyna → Kodla katıl → kodu gir → Katıl
4. **Beklenen:** İki cihazda da tahta açılır; Cihaz 1 (mavi) başlar, Cihaz 2 tahtayı ters çevrilmiş görür.

### A2. Açık odalar listesinden katılma
1. Cihaz 1: Oda oluştur → görünürlük **Herkes**
2. Cihaz 2: Açık odalar sekmesi → yenile
3. **Beklenen:** Oda listede görünür; oda adı, sahibinin kullanıcı adı ve puanı, süre, dereceli/derecesiz ve 1/2 oyuncu sayısı doğru.
4. Cihaz 2: Odaya dokun → maç başlar, oda listeden kaybolur.

### A3. Şifreli oda
1. Cihaz 1: Oda oluştur → şifre `1234` (en az 4 karakter) → Herkes
2. Cihaz 2: Listeden odaya dokun → şifre sorulur → **yanlış** şifre gir
3. **Beklenen:** "Oda şifresi hatalı" mesajı, maç başlamaz.
4. Doğru şifreyi gir → maç başlar.

### A4. Aynı anda hamle gönderme (yarış koşulu)
1. Maç sürerken iki cihazda da aynı anda hamle yapmayı deneyin (sırası olmayan taraf da dokunsun)
2. **Beklenen:** Sırası olmayan oyuncunun dokunuşu hiçbir şey yapmaz. Tahta iki cihazda da aynı kalır, tur numarası tek artar. Hiçbir cihazda "hayalet" hamle görünmez.

### A5. Hızlı eşleşme
1. Cihaz 1: Oda oluştur → **Herkes** görünürlüğü, şifresiz
2. Cihaz 2: Hızlı eşleşme
3. **Beklenen:** Cihaz 2 doğrudan Cihaz 1'in odasına girer.
4. Hiç açık oda yokken Hızlı eşleşme: **Beklenen:** "Rakip bulunamadı. Bunun yerine bir oda oluşturmayı dene."

---

## B. Bağlantı ve dayanıklılık

### B1. Maç sırasında interneti kapatma
1. Maç sürerken Cihaz 2'de uçak modunu aç
2. Cihaz 2'de hamle yapmayı dene
3. **Beklenen:** Hamle gitmez, "Yeniden bağlanılıyor…" görünür, uygulama **çökmez**.
4. Uçak modunu kapat → **Beklenen:** Tahta otomatik olarak güncel duruma senkronize olur.

### B2. Uygulamayı kapatıp maça geri dönme
1. Maç sürerken Cihaz 2'de uygulamayı **görev listesinden tamamen kapat**
2. Uygulamayı yeniden aç → Çevrim İçi Oyna
3. **Beklenen:** "Maçına dön" kartı görünür. Dokununca aynı maça aynı tahtayla dönülür.

### B3. Kısa kopma yenilgi sayılmamalı
1. Maç sürerken Cihaz 2'yi 10 saniye uçak moduna al, sonra geri getir
2. **Beklenen:** Maç devam eder, kimse kaybetmez.

### B4. Süre aşımı
1. Oda oluştururken **hamle süresi 30 sn** seç
2. Sırası gelen oyuncu hiçbir şey yapmasın
3. **Beklenen:** Sayaç 0:00'a iner, karşı tarafta "Galibiyeti al" butonu belirir.
4. Butona bas → **Beklenen:** Maç biter, kazanan doğru.
5. **Ayrıca:** Sırası olan oyuncuda "Galibiyeti al" butonu **görünmemeli**.

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

---

## D. Arkadaşlık ve davet

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

### D4. Oyuna davet
1. Cihaz 1: Oda oluştur → bekleme ekranında arkadaş listesinden Cihaz 2'yi davet et
2. **Beklenen:** Cihaz 2'nin Arkadaşlar ekranında davet görünür → Katıl → doğrudan o odaya girer.

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
