# MCX Beta 1 — Final Project

## Included
- First-run welcome screen.
- Full file browser using Android Storage Access Framework.
- User-selected folders within Android's permitted locations.
- Minecraft-specific icons:
  - .mcpack
  - .mcaddon
  - .mcworld
- ZIP / RAR / 7Z extraction.
- Password field for encrypted archives.
- Safe extraction against path traversal (Zip Slip).
- Opens Minecraft package files through Android Intent; no direct access to Minecraft internal data.
- R8/minification for Release.
- Minimal permissions.
- Neutral advertising placeholder only: no AdMob, no ad SDK, no ad IDs.

## You add yourself
- Your preferred advertising provider, SDK and IDs.
- Your release signing key.
- Privacy-policy URL/content if you later add ads or other data-processing services.
- Store listing assets and publisher information.

## Cloud build
The included GitHub Actions workflow builds:
app/build/outputs/apk/release/app-release.apk

## Important Android limitation
The file browser can browse locations Android exposes through Storage Access Framework. It does not bypass Android storage restrictions and does not read Minecraft's private/internal data.
