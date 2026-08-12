# Play Console — yükleme sırası

Bu belge, Play Console'da adım adım ne yapılacağını ve her alana tam olarak ne yazılacağını
tutar. Sıra önemli: 7. adım 6. adımın çıktısına bağlı, 8. adım da 2. adımdan sonra işliyor.

Beyanları geliştirici olarak **sen** yapıyorsun. Bu belge cevapları hazırlar; yanlış beyan
geliştirici hesabının sorumluluğunda.

## Nerede kalındı — 2026-08-09

1–7. adımlar tamamlandı, inceleme geçti, kapalı test başladı. Somut durum:

| | |
|---|---|
| Uygulama | `Koridor: Wall & Path Strategy` / `Koridor: Duvar ve Yol Oyunu` |
| Play uygulama kimliği | `4975646665764434612`, geliştirici `8806161756596118584` |
| İnceleme | ✅ geçti — mağaza sayfası ve on beyan onaylandı |
| İç test | sürüm 5 (1.0.0) yayında, `Koridor ic testi` listesi (2 kişi) |
| Kapalı test | `Kapalı test - Alpha`, sürüm 5 (1.0.0) yayında, 177 ülke/bölge |
| Kapalı test listesi | `Koridor kapali test` — 12 kişi, **hepsi kaydoldu** |
| Katılım bağlantısı | `https://play.google.com/apps/testing/com.duzman46.gridbound` |
| Kategori | Oyun → Masa |
| Üretim | Etkin değil — kapalı test şartı beklemede |

**14 günlük sayaç 2026-08-09'da başladı**, yani üretime en erken **2026-08-23**'te
başvurulabilir. Şart kesintisiz: kayıtlı test kullanıcısı 12'nin altına düşerse süreklilik
bozulur ve sayaç yeniden başlar. Payanda yok — listede tam 12 kişi var.

Başvuru üç şey soruyor ve ikisinin cevabı ancak testten çıkar: test kullanıcılarını bulmak ne
kadar zordu, ne kadar etkileşim oldu, **geri bildirimler neydi ve sonucunda uygulamada neyi
değiştirdin**. Yani 14 gün beklenecek bir süre değil, toplanacak bir malzeme.

Kalanlar: 14 günün dolması; üretim başvurusu (~7 gün inceleme); ve 8. adımdaki AdMob işleri.

---

## 0. Yüklenecek dosya

```
app/build/outputs/bundle/release/app-release.aab
```

Üretmek için (yeniden gerekirse):

```
& "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" -Xmx2g -classpath "C:/Users/furka/Documents/Uygulamalarım/Koridor/source/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain :app:bundleRelease -x :app:uploadCrashlyticsMappingFileRelease
```

| | |
|---|---|
| Paket adı | `com.duzman46.gridbound` |
| Sürüm | versionCode 5, versionName 1.0.0 |
| İmza | `CN=Koridor Upload`, SHA-1 `BA:49:80:EE:36:DC:A6:31:7C:D3:E0:3A:B0:12:21:CB:05:60:75:18` |
| Boyut | ~12.7 MB |

APK değil AAB yükleniyor; Play yeni uygulamalarda APK kabul etmiyor.

---

## 1. Uygulamayı oluştur

Play Console → **Tüm uygulamalar → Uygulama oluştur**.

| Alan | Değer |
|---|---|
| Uygulama adı | `Koridor` |
| Varsayılan dil | **İngilizce (en-US)** |
| Uygulama mı, oyun mu | Oyun |
| Ücretsiz mi, ücretli mi | **Ücretsiz** |

Ücretsiz seçimi geri alınamaz — sonradan ücretliye çevrilemiyor. Koridor ücretsiz + uygulama içi
satın alma modelinde, yani doğru olan bu.

Varsayılan dil, **uygulamanın dili değil, mağaza sayfasının yedeği**. Uygulama kendi dilini
cihazdan seçiyor ve on dil derlemenin içinde; buradaki alan yalnızca şunu belirliyor: birinin
dilinde mağaza sayfası yoksa hangisini görecek. Türkçe seçilseydi, İspanyolca sayfa olmadığı için
İspanya'daki bir kullanıcı Türkçe sayfa görürdü.

Alttaki beyan kutuları (geliştirici programı politikaları, ABD ihracat yasaları) senin
taahhüdün; okuyup sen işaretle.

---

## 2. Mağaza girişi

Play Console → **Büyüme → Mağaza varlığı → Ana mağaza girişi**.

Önce varsayılan dilde (İngilizce) doldur, sonra **Türkçe çeviri ekle**.

Metinler `docs/store/PLAY_STORE_LISTING_EN.md` ve `..._TR.md` dosyalarında; oradan kopyalanır.

| Sayfa | Başlık (Play'in sınırı 30 karakter) |
|---|---|
| İngilizce (varsayılan) | `Koridor: Wall & Path Strategy` |
| Türkçe | `Koridor: Duvar ve Yol Oyunu` |

İsim her dilde **Koridor** kalıyor. `Quoridor` Gigamic'in 1997'den tescilli markası ve
kullanılamaz; İngilizcedeki bariz alternatif `Barricade` ise Play'de aynı nişte iki uygulamanın
adı, yani sahip olmadığın bir isimde üçüncü sıraya düşersin. Gerekçenin tamamı
`docs/store/PLAY_STORE_LISTING_EN.md` sonundaki notta.

**İletişim bilgileri** bölümünde:

| Alan | Değer |
|---|---|
| Web sitesi | `https://gridbound-duzman46.web.app` |
| E-posta | `furkanduzman46@gmail.com` |

Web sitesi alanı boş bırakılamaz: AdMob `app-ads.txt` dosyasını **yalnızca buradan** buluyor,
ve o dosya olmadan programatik alıcıların büyük kısmı envantere teklif vermiyor.

**Görseller** — hangisinin kullanılacağı `docs/store/ASSET_NOTES.md` içinde yazılı:

- Simge: `store-assets/play-icon-512.png`. 2026-08-12'de değişti: `reference/simge.png`'den
  kesiliyor — altın piyon, siyah piyon, altın çerçeve. APK'nın içinde değil, konsola ayrıca
  yüklenir.
- Ekran görüntüleri: `docs/store/screenshots/` altında iki set var. `-en` ile bitenler
  İngilizce sayfaya, `-tr` ile bitenler Türkçe sayfaya. Kendi dilinde sayfası olmayan herkes
  varsayılanın — yani İngilizce setin — görüntülerini görür. İkisi de 2026-08-08'de yayın
  derlemesinden çekildi.
- Öne çıkan görsel: `docs/store/feature-graphic-1024x500.png`. Koyu tahta, mavi ve kırmızı piyon,
  mint duvarlar; **solda simgenin kendisi** — çizimi değil, `app-icon.py`'nin ürettiği kutucuğun
  ta kendisi, çalışma anında kesiliyor. Üreten betik `docs/store/feature-graphic.py`; simge ya da
  palet değişirse `app-icon.py`'den **sonra** yeniden çalıştırılır.
- **Depo dışında, bir üst klasörde** (`Koridor/store-assets/`) hem eski bir `icon-512.png` hem de
  **yayınlanacakla aynı adı taşıyan** eski bir `feature-graphic-1024x500.png` duruyor. Buradaki
  tek gerçek tehlike, adına bakıp o ikisinden birini yüklemek.

Telefon ekran görüntüleri 1080×2160, yani 1:2 — Play'in metninde yazan 9:16'dan uzun. Konsol
bunu sorunsuz kabul ediyor; yalnızca "tanıtımdan yararlanma" için en az üç tanesinin 16:9 ya da
9:16 olması isteniyor, bu da yayını engelleyen bir şey değil.

Tablet ekran görüntüleri (7 ve 10 inç) formda yıldızlı görünüyor ama boş bırakılabiliyor:
mağaza girişi onlarsız kaydedildi. Yokluklarının tek sonucu büyük ekran yüzeylerinde öne
çıkmamak.

---

## 3. Uygulama içeriği — gizlilik ve veri silme

**Politika → Uygulama içeriği → Gizlilik politikası**:

```
https://gridbound-duzman46.web.app/privacy
```

**Politika → Uygulama içeriği → Veri güvenliği**, veri silme adımı:

| Soru | Cevap |
|---|---|
| Kullanıcılar hesap oluşturabiliyor mu? | **Evet** |
| Hesap/veri silme URL'si | `https://gridbound-duzman46.web.app/privacy` |
| Kullanıcılar veri silme talep edebiliyor mu? | **Evet** |

**Sondaki eğik çizgiyi yazma.** `/privacy/` 404 döndürüyor ve bu alanda 404 tek başına ret
sebebi. Sayfa hem uygulama içi silme yolunu (Ayarlar → Hesap → Hesabı sil) hem de uygulamayı
silmiş biri için e-posta yolunu anlatıyor; Play ikincisini arıyor.

---

## 4. Veri güvenliği formu

Cevaplar `docs/DATA_SAFETY.md` §5 tablosundan birebir aktarılır. O belge kodun gerçekte ne
topladığından türetildi ve yayımlanan gizlilik sayfasıyla aynı şeyi söylüyor — form ile sayfa
arasındaki çelişki tek başına ret sebebi.

Toplananlar özetle: e-posta, kullanıcı kimlikleri, kullanıcı adı, uygulama içi eylemler, oda
adı, maç içi hazır mesajlar, satın alma geçmişi, kilitlenme günlükleri, tanılama, cihaz
kimlikleri ve yaklaşık konum. **Paylaşılan iki kalem** Reklam Kimliği ve yaklaşık konum;
ikisi de AdMob'dan gelir.

Yaklaşık konum, uygulamanın kendi topladığı bir şey değil: konum izni istenmez, konum API'si
hiç çağrılmaz. AdMob reklam sunarken isteğin IP'sinden şehir düzeyinde konum türetir ve Play'in
formu üçüncü taraf SDK'ları da kapsar. Yayımlanan gizlilik sayfası bunu zaten söylüyor;
beyan etmemek, form ile sayfa arasında çelişki demek olurdu.

Analytics kararı: kalıyor ve beyan ediliyor — "Uygulama etkinliği → Uygulama içi eylemler" ve
"Cihaz veya diğer kimlikler" satırlarına **Analitik** amacı ekleniyor. Gerekçesi
`docs/DATA_SAFETY.md` §6'da.

---

## 5. Kalan beyanlar

**Politika → Uygulama içeriği** altındaki diğer formlar. Doğruyu yaz; aşağısı hazırlık, cevap
değil:

- **Reklamlar:** Evet, uygulama reklam içeriyor.
- **Hedef kitle ve içerik:** oyun 13 yaş altına özel hedeflenmiyor. Aile programına girmeyi
  seçme — uygulama reklam ve çevrim içi etkileşim taşıyor ve o programın şartlarını
  karşılamıyor.
- **İçerik derecelendirme anketi:** şiddet, cinsellik, küfür, kumar, uyuşturucu yok. Anketin
  sorduğu iki gerçek şey: kullanıcıların birbiriyle etkileşimi (**evet** — kullanıcı adı, oda
  adı ve sabit listeden seçilen maç içi mesajlar) ve dijital satın alma (**evet** — reklamları
  kaldır).
- **Uygulama erişimi:** tüm içerik giriş yapmadan erişilebilir değil; çevrim içi bölüm hesap
  istiyor. İncelemeci için misafir girişi yeterli — "Misafir olarak oyna" ile her şey açılıyor,
  test hesabı vermeye gerek yok. Bunu açıklama alanına yaz.
- **Reklam kimliği:** Evet, kullanılıyor (AdMob). Amaç olarak *Analiz* ve *Reklam veya
  pazarlama* işaretlenir — veri güvenliği formundaki "Cihaz veya diğer kimlikler" satırıyla
  aynı iki amaç. İkisinin çelişmemesi gerekiyor.
- **Resmi kurum uygulamaları:** Hayır.
- **Finans ile ilgili özellikler:** hiçbiri. Listedeki kalemler bankacılık, kredi, para
  transferi, kripto ve yatırım; "reklamları kaldır" satın alması bunların hiçbiri değil,
  dolayısıyla "Uygulamamda finans ile ilgili özellik sağlanmıyor" işaretlenir.
- **Sağlık uygulamaları:** hiçbiri.

---

## 6. Sürüm oluştur ve iç teste gönder

**Test ve yayınlama → Test → İç test** ile başla, doğrudan üretime değil.

Sebebi: Play Billing yalnızca Play'den kurulmuş bir derlemede çalışıyor, yani "Reklamları
kaldır" satın alımını başka türlü sınayamıyorsun. `docs/MANUAL_TESTS.md` E bölümü bu adımda
koşulur.

**2026-08-08'de yapıldı.** Sürüm 5 (1.0.0) iç test kanalında yayında; kanal etkin ve
`Koridor ic testi` listesi (`furkanduzman46@gmail.com`, `kolaydibos@gmail.com`) tanımlı.
Sürüm notları en-US ve tr-TR için girildi.

Sürüm notunda ve mağaza metinlerinde **`Quoridor` kelimesi geçmemeli** — ilk yazımda geçmişti ve
gönderilmeden önce düzeltildi. Gerekçesi 2. adımdaki marka notu; kural yalnızca başlık ve
açıklama için değil, sürüm notu dahil Play'e yazdığın her metin için geçerli.

---

## 7. Yükledikten sonra — App Signing (atlanırsa Google girişi kırılır)

**Test ve yayınlama → Kurulum → Uygulama bütünlüğü → Uygulama imzalama**.

**2026-08-09'da tamamlandı.** Play'in imzalama anahtarı yükleme anahtarından farklı, dolayısıyla
bu adım gerekliydi:

| | SHA-1 | SHA-256 |
|---|---|---|
| Yükleme anahtarı (senin `.jks`) | `BA:49:80:EE:36:DC:A6:31:7C:D3:E0:3A:B0:12:21:CB:05:60:75:18` | `21:4A:F2:…:DD:AB` |
| **Play imzalama anahtarı** | `7F:15:A2:7C:47:2A:09:A8:08:85:83:E9:65:FB:DA:B3:39:C9:31:31` | `DC:1F:92:92:5D:12:F2:50:91:B5:B4:73:D7:34:19:A5:2B:3D:BC:2E:4E:AC:F5:FF:49:23:F1:09:91:E6:E7:D6` |

Alttaki satırın ikisi de Firebase'e (`gridbound-duzman46` → Proje ayarları → Android uygulaması
→ Parmak izi ekle) eklendi. Anahtar değişmediği sürece bir daha yapılmayacak.

**Parmak izini konsoldan okuma, cihazdan ölç.** İlk denemede konsolun imzalama sayfasından
`B7:E2:72:…` okundu ve Firebase'e o eklendi; sayfa birden fazla anahtar listeliyor (klasik,
kuantum sonrası, yükleme) ve yanlış satır alındı. Sonuç: sideload edilen APK'da Google girişi
çalışmaya devam etti — onun sertifikası zaten kayıtlıydı — ve yalnızca **mağazadan kuranlarda**
kırıldı, yani hatanın görüldüğü yer test edilen yer değildi. Şüpheye yer bırakmayan ölçüm şu:

```
adb shell pm path com.duzman46.gridbound
adb pull <base.apk yolu> .
apksigner verify --print-certs base.apk
```

`Signer #1` satırı, kullanıcının cihazına inen APK'yı fiilen imzalayan sertifikadır. `Source
Stamp Signer` başka bir şeydir, Firebase'e o eklenmez.

Play uygulamayı kendi anahtarıyla yeniden imzalıyor, yani mağazadan inen sürümün sertifikası
senin test ettiğin sertifika değil. Google ile giriş sertifikaya bağlı: bu adım atlanırsa senin
telefonunda çalışmaya devam eder ve **mağazadan indiren herkeste kırılır**. Yeniden derleme
gerekmiyor, OAuth istemcisi sunucu tarafında.

Doğrulaması: iç test kanalından kur, karşılama ekranında Google düğmesine bas.

---

## 8. AdMob

**2026-08-09 durumu.** Reklamlar çıkıyor ve kazanç üretiyor (ilk günlerde ₺1'in altında), ama
uygulama **"Sınırlı reklam sunumu"** kısıtı altında: AdMob onaylanmamış uygulamalara envanterin
küçük bir kısmını veriyor.

Kısıtı kaldıran şey mağaza bağlantısı, ve o **üretime çıkmadan kurulamıyor**. AdMob eşleşmeyi
herkese açık Play listesinde arıyor; kapalı testteki bir uygulamanın öyle bir sayfası yok ve
sihirbaz "Eşleşen Google Play uygulaması bulunamadı" ile duruyor. Yani bu bir eksik ayar değil,
sıraya bağlı bir adım: kapalı test → üretim → AdMob onayı → tam sunum.

Sihirbazda bir tuzak var: "Kurulumu bitir" akışı varsayılan olarak **yeni bir AdMob uygulaması
oluştur** seçili geliyor. Onaylanırsa derlemedeki reklam birimleri boşta kalır. Doğrusu "mevcut
bir AdMob uygulamasına ekle" ve `…3421123804` — `monetization.properties` içindeki kimlik odur.

- **`app-ads.txt`**: `https://gridbound-duzman46.web.app/app-ads.txt` yayında ve doğru
  (`pub-8456650313142312`). AdMob henüz taramadı; tarama da mağaza sayfasına bağlı.
- **Ödeme**: AdSense (Türkiye) hesabı kurulu, eşik ₺200. Kimlik doğrulaması eşiğe yaklaşınca
  isteniyor, şimdilik bir işlem yok.
- **AdMob → Ayarlar → Test cihazları**: kendi telefonunu ekle. **Yapılmadı ve önemli** —
  eklenmediği sürece geliştirici uygulamayı her açtığında gerçek gösterim üretiyor. Kendi
  reklamına tıklamak geçersiz trafik ve hesap kapatma sebebi.
- Kapalı test kullanıcılarına "reklamlara tıkla" denmemeli; aynı politika onları da kapsıyor.
- Yeni bir uygulamada dolum düşük başlıyor. Banner'ın boş kalması bir hata değil.

---

## Kapanmamış, yayını engellemeyenler

`docs/WORK_ORDER.md` sonundaki liste geçerliliğini koruyor. Yayın açısından bilinmesi gerekenler:

- Tablet ekran görüntüsü yok (2. adım) ve PC Üzerinde Google Play Games için de ekran görüntüsü
  yüklenmedi. İkisi de isteğe bağlı; sonucu yalnızca o yüzeylerde öne çıkmamak.
- `recentMatches` için temizlik işi yok: silinen bir hesabın maç geçmişi satırları kalıyor ve
  bu `docs/DATA_SAFETY.md` §4.2'de beyan edilmiş durumda.
- Worker en kötü dakikada ücretsiz planın 50 alt isteğinden 47'sini kullanıyor.
- Analytics, UMP rıza sonucuna bağlı değil; AEA'da ciddi kitle oluşursa yapılacak iş.
