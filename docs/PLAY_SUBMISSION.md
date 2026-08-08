# Play Console — yükleme sırası

Bu belge, Play Console'da adım adım ne yapılacağını ve her alana tam olarak ne yazılacağını
tutar. Sıra önemli: 4. adım 3. adımın çıktısına bağlı, 8. adım da 6. adımdan sonra yapılabilir.

Beyanları geliştirici olarak **sen** yapıyorsun. Bu belge cevapları hazırlar; işaretlemeyi sen
yaparsın, çünkü yanlış beyan geliştirici hesabının sorumluluğunda.

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

## 1. Mağaza girişi

Play Console → **Büyüme → Mağaza varlığı → Ana mağaza girişi**.

Metinler `docs/store/PLAY_STORE_LISTING_TR.md` ve `..._EN.md` dosyalarında; oradan kopyalanır.

**İletişim bilgileri** bölümünde:

| Alan | Değer |
|---|---|
| Web sitesi | `https://gridbound-duzman46.web.app` |
| E-posta | `furkanduzman46@gmail.com` |

Web sitesi alanı boş bırakılamaz: AdMob `app-ads.txt` dosyasını **yalnızca buradan** buluyor,
ve o dosya olmadan programatik alıcıların büyük kısmı envantere teklif vermiyor.

**Görseller** — hangisinin kullanılacağı `docs/store/ASSET_NOTES.md` içinde yazılı:

- Simge: `store-assets/play-icon-512.png`
- Ekran görüntüleri: `docs/store/screenshots/` altındaki dördü (2026-08-08'de yayın
  derlemesinden çekildi)
- Öne çıkan görsel: `docs/store/feature-graphic-1024x500.png` **eski** — eski amblemi ve eski
  paleti gösteriyor. Kullanmadan önce yenilenmeli.

---

## 2. Uygulama içeriği — gizlilik ve veri silme

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

## 3. Veri güvenliği formu

Cevaplar `docs/DATA_SAFETY.md` §5 tablosundan birebir aktarılır. O belge kodun gerçekte ne
topladığından türetildi ve yayımlanan gizlilik sayfasıyla aynı şeyi söylüyor — form ile sayfa
arasındaki çelişki tek başına ret sebebi.

Toplananlar özetle: e-posta, kullanıcı kimlikleri, kullanıcı adı, uygulama içi eylemler, oda
adı, maç içi hazır mesajlar, satın alma geçmişi, kilitlenme günlükleri, tanılama, ve cihaz
kimlikleri. **Paylaşılan tek kalem** Reklam Kimliği (AdMob).

Analytics kararı: kalıyor ve beyan ediliyor — "Uygulama etkinliği → Uygulama içi eylemler" ve
"Cihaz veya diğer kimlikler" satırlarına **Analitik** amacı ekleniyor. Gerekçesi
`docs/DATA_SAFETY.md` §6'da.

---

## 4. Kalan beyanlar

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
- **Reklam kimliği:** Evet, kullanılıyor (AdMob).

---

## 5. Sürüm oluştur ve iç teste gönder

**Test ve yayınlama → Test → İç test** ile başla, doğrudan üretime değil.

Sebebi: Play Billing yalnızca Play'den kurulmuş bir derlemede çalışıyor, yani "Reklamları
kaldır" satın alımını başka türlü sınayamıyorsun. `docs/MANUAL_TESTS.md` E bölümü bu adımda
koşulur.

---

## 6. Yükledikten sonra — App Signing (atlanırsa Google girişi kırılır)

**Test ve yayınlama → Kurulum → Uygulama bütünlüğü → Uygulama imzalama**.

Oradaki **App signing key** sertifikasının SHA-1'ini oku.

- Yukarıdaki yükleme anahtarının SHA-1'i ile aynıysa yapacak bir şey yok.
- Farklıysa: Firebase Console → `gridbound-duzman46` → Proje ayarları → Android uygulaman →
  **Parmak izi ekle** ile hem SHA-1'i hem SHA-256'yı ekle.

Play uygulamayı kendi anahtarıyla yeniden imzalıyor, yani mağazadan inen sürümün sertifikası
senin test ettiğin sertifika değil. Google ile giriş sertifikaya bağlı: bu adım atlanırsa senin
telefonunda çalışmaya devam eder ve **mağazadan indiren herkeste kırılır**. Yeniden derleme
gerekmiyor, OAuth istemcisi sunucu tarafında.

Doğrulaması: iç test kanalından kur, karşılama ekranında Google düğmesine bas.

---

## 7. AdMob

Yayınlandıktan sonra:

- **AdMob → Uygulamalar → app-ads.txt**: 1. adımdaki web sitesi alanı dolduktan sonra AdMob
  siteyi tarıyor. İlk gün "bulunamadı" yazması normal.
- **AdMob → Ayarlar → Test cihazları**: kendi telefonunu ekle. Hem yerleşimleri test
  reklamlarıyla görürsün hem de kendi canlı reklamına tıklayıp hesabı riske atmazsın.
- Yeni bir uygulamada dolum düşük başlıyor. Banner'ın boş kalması bir hata değil.

---

## Kapanmamış, yayını engellemeyenler

`docs/WORK_ORDER.md` sonundaki liste geçerliliğini koruyor. Yayın açısından bilinmesi gerekenler:

- Öne çıkan görsel eski (1. adım).
- `recentMatches` için temizlik işi yok: silinen bir hesabın maç geçmişi satırları kalıyor ve
  bu `docs/DATA_SAFETY.md` §4.2'de beyan edilmiş durumda.
- Worker en kötü dakikada ücretsiz planın 50 alt isteğinden 47'sini kullanıyor.
- Analytics, UMP rıza sonucuna bağlı değil; AEA'da ciddi kitle oluşursa yapılacak iş.
