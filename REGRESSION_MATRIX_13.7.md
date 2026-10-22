# REGRESSION MATRIX 13.8

- versionName 13.8 / versionCode 81.
- Only one player WebView remains live: inactive tabs are paused and hibernated.
- WebView offscreen pre-raster is disabled.
- Web contents debugging is disabled even in debug APK.
- Multiple windows and automatic window.open are disabled; popup WebViews are not created.
- Request diagnostics retain only explicit ad candidates; HLS/DASH media segments are not stored/logged.
- MAIN_MEDIA is logged only for the first playback resource.
- VAST no-fill stays enabled with credentialed CORS, but its log is rate-limited.
- Windows Chrome UA and the 13.6 player sizing/layout are preserved.
- Main Stravers iframe / player_js / HLS / DASH are not blocked.
