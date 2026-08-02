# Store asset notes

The Koridor launcher icon is an original project-bound raster asset generated with the built-in image generation workflow and then downsampled with Lanczos resampling for Android launcher densities. Launcher-density images use a rounded alpha safety mask; the Google Play 512 px asset remains an opaque square as required for store submission.

Final prompt:

> An original minimal emblem for Koridor: a clean top-down 3×3 abstract board motif with one cool blue pawn, one warm orange pawn, and two golden corridor walls forming an open path. Crisp vector-like flat app icon, modern Material 3 sensibility, deep charcoal-green background and emerald tiles. Centered inside the launcher safe zone. No text, trademarks, watermark, mockup, border, or tiny details.

Files:

- `icon-master.png`: full-resolution project master
- `icon-512.png`: Google Play listing icon
- `feature-graphic-1024x500.png`: Google Play feature graphic
- `screenshots/phone-01-gameplay-tr.png`: Turkish gameplay screenshot (1080×2160)
- `screenshots/phone-02-main-menu-tr.png`: Turkish main menu screenshot (1080×2160)
- `screenshots/phone-03-exit-confirmation-tr.png`: Turkish exit confirmation screenshot (1080×2160)
- `app/src/main/res/mipmap-*/ic_launcher.png`: Android launcher densities

The feature graphic was generated with the built-in image generation mode, using the project icon as the visual reference, and then center-cropped to the exact Google Play size of 1024×500 pixels.

Feature graphic prompt:

> Create a polished 1024×500 landscape Google Play feature graphic for the Android strategy board game Koridor. Use the attached official app icon as the visual identity reference: deep charcoal-teal background, emerald rounded board tiles, one glossy cool-blue pawn, one glossy warm-orange pawn, and golden corridor walls. Show a cinematic three-quarter view of an abstract board with the two pawns facing across a strategic maze of gold walls. Premium modern 3D game art, dramatic rim lighting, clean negative space, high contrast, sharp silhouettes, balanced composition, no text, no letters, no logo, no watermark, no device mockup, no border, no unrelated objects.

The phone screenshots preserve the real tested app UI. They are cropped only to remove the Android navigation bar and meet Google Play's supported 2:1 aspect ratio; no interface elements were generated or retouched.
