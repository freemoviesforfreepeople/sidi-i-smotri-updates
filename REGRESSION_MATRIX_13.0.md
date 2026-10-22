# Regression matrix 13.0

1. Build: versionName 13.0 / versionCode 73.
2. Player page uses desktop UA, wide viewport and overview mode.
3. Injected viewport is width=1366 and logs PLAYER_DESKTOP_LAYOUT.
4. Largest non-ad player iframe/video is expanded only when abnormally short.
5. moviead55/Buzzoola/VAST frames are excluded from player-size expansion.
6. Kinobox source selector should be visible when desktop breakpoint is active.
7. Manual ad picker never accepts html/body/video/audio/iframe or an ancestor containing player media.
8. Confirmed manual removal hides the exact picked element immediately via data-sidi-picker-token.
9. Saved rules include selector/tag/href/text and are re-applied with selector + fallback matching.
10. Undo restores elements using data-sidi-manual-rule id.
11. Old broken manual rules are reset by schema 130.
12. VAST no-fill/CORS rules from 12.9 remain intact.
13. player_js, mstream2inline, Stravers and main media are not blanket-blocked.
14. Native Chromium fullscreen, system pointer, Button Mapper duplicate suppression and tab lifecycle remain unchanged.
