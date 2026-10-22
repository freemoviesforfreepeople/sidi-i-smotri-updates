# Regression matrix 13.3

1. versionName=13.3, versionCode=76.
2. Desktop viewport remains width=1366.
3. Stravers/player iframe is never force-expanded to 100% viewport width/height by layout injection.
4. Kinobox wrapper is the only element whose collapsed height may be repaired (330 CSS px).
5. .kinobox__menuItem / .kinobox_menu source selector remains visible/reachable.
6. Known player iframe/container cannot be removed manually.
7. Known ad wrappers (#movie_video, #tgWrapper, #TopAdMb, adlook/adlk wrappers) can be selected manually when outside protected player containers.
8. Saved manual ad selectors are re-applied after DOM recreation using exact saved rules only.
9. No generic promo/banner/advert MutationObserver is used.
10. Stravers, player_js, mstream2inline and main media remain allowed.
11. VAST no-fill keeps exact Origin + Access-Control-Allow-Credentials.
12. Native HTML5 fullscreen and input behavior remain unchanged.
