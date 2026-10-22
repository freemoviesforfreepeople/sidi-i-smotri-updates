# Regression matrix 13.6

- [ ] versionName 13.6 / versionCode 79
- [ ] player opens without ANR or render-process crash
- [ ] first playback starts without needing repeated Play attempts
- [ ] player UI remains responsive while video is playing
- [ ] known MovieAd display request is not replaced by HTML/JS-incompatible response
- [ ] explicit VAST preroll no-fill remains active
- [ ] player_js / mstream2inline / Stravers / main m3u8/mp4/mpd remain allowed
- [ ] no persistent known-ad MutationObserver in MainActivity
- [ ] no persistent manual-rule MutationObserver in MainActivity
- [ ] player layout injection is not run during onPageStarted
- [ ] player layout is applied only at commit/finish plus one settling pass
- [ ] Windows Chrome desktop UA remains enabled for player WebView
- [ ] Play/Pause repeatCount > 0 is consumed and not forwarded as another toggle
- [ ] native Chromium fullscreen and system pointer behavior unchanged
