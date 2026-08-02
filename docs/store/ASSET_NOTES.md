# Store asset notes

The Koridor launcher icon is an original project-bound raster asset generated with the built-in image generation workflow and then downsampled with Lanczos resampling for Android launcher densities. Launcher-density images use a rounded alpha safety mask; the Google Play 512 px asset remains an opaque square as required for store submission.

Final prompt:

> An original minimal emblem for Koridor: a clean top-down 3×3 abstract board motif with one cool blue pawn, one warm orange pawn, and two golden corridor walls forming an open path. Crisp vector-like flat app icon, modern Material 3 sensibility, deep charcoal-green background and emerald tiles. Centered inside the launcher safe zone. No text, trademarks, watermark, mockup, border, or tiny details.

Files:

- `icon-master.png`: full-resolution project master
- `icon-512.png`: Google Play listing icon
- `app/src/main/res/mipmap-*/ic_launcher.png`: Android launcher densities
