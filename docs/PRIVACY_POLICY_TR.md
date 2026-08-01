# Gridbound Gizlilik Politikası

Son güncelleme: 1 Ağustos 2026

Gridbound çevrimdışı ve çevrimiçi oynanabilen bir strateji oyunudur.

## Toplanan veriler

Uygulama ad, e-posta, telefon numarası veya reklam kimliği istemez ve reklam göstermez. Çevrimiçi oyun seçildiğinde Firebase Authentication cihaz için rastgele bir anonim kullanıcı kimliği oluşturur. Oda kodu, anonim oyuncu kimlikleri, tahta durumu, hamle geçmişi ve zaman bilgileri oyun eşzamanlaması için Firebase Realtime Database üzerinde saklanır. Bu veriler oda üyeliğini ve oyun sırasını doğrulamak dışında kullanılmaz.

Oyun ayarları ile toplam oyun, galibiyet ve mağlubiyet gibi istatistikler cihazda Android DataStore içinde saklanır.

## İzinler

Uygulama çevrimiçi oyun için internet/ağ durumu, oyun geri bildirimi için titreşim iznini kullanır. Konum, kamera, mikrofon, kişiler veya depolama izni istemez.

## Veri silme

Kullanıcı Android uygulama ayarlarından Gridbound verilerini temizleyerek cihazda saklanan tüm ayar ve istatistikleri silebilir. Uygulamanın kaldırılması da yerel verileri siler. Bekleyen oda kapatıldığında oda verisi silinir; tamamlanan veya terk edilen çevrimiçi oda verileri operasyonel temizlik kapsamında silinebilir. Çevrimiçi verilerle ilgili silme talebi proje sahibine oda koduyla iletilebilir.

## Çocukların gizliliği

Uygulama doğrudan kişisel bilgi istemez. Çocukların çevrimiçi oda kodlarını yalnızca tanıdıkları kişilerle paylaşması önerilir.

## Hizmet sağlayıcı

Çevrimiçi özellikler Google Firebase Authentication ve Firebase Realtime Database kullanır. Firebase’in veri işleme ve güvenlik koşulları Google’ın ilgili hizmet politikalarına tabidir.

## Değişiklikler

Politika değişirse güncel sürüm bu dosyada ve uygulamanın mağaza sayfasında yayımlanır.
