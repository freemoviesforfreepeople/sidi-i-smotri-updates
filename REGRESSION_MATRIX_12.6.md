# REGRESSION MATRIX 12.6

- Version: `12.6` / `69`.
- Player bootstrap: `code.moviead55.ru` non-media bootstrap resources are allowed; VAST/VPAID/VMAP/preroll/midroll/postroll and ad-media context stay blockable.
- Stravers remains a cross-origin iframe; it is never promoted to the top-level document.
- DOM cleanup: no persistent generic ad `MutationObserver`; only bounded targeted cleanup after a player iframe exists.
- Loading: `onPageCommitVisible`, iframe detection, main-media detection and the 8s safety deadline all release the app overlay.
- Logging: `PLAYER_IFRAME_CREATED`, `STRAVERS_REQUEST`, `STRAVERS_HTTP_ERROR`, `STRAVERS_NETWORK_ERROR`, `PLAYER_READY`, `PLAYER_INIT_ERROR`, `AD_BLOCKED`, `MAIN_MEDIA_ALLOWED`.
- Manual ad removal: user can enable picker mode, click a DOM ad, confirm, persist the selector in SharedPreferences and undo the last deletion. `video`, `audio`, player iframe and containers containing them are protected.
- URL replacement: radio selection with `kino → ss`, `poisk.ru → kino.win`, safe `без подмены`, custom rules, delete custom rule and persisted active rule.
- URL replacement is used for initial load, render-process recovery and active-tab reload after rule changes; retry is no longer hardcoded to `sspoisk.ru`.
- Input: real media key accepted; repeat and delayed `source=0` duplicate suppressed; no synthetic touch/media events.
- Fullscreen: native Chromium CustomView retained; system arrow cursor retained.
- Lifecycle: inactive tabs pause; foreground/background timer lifecycle retained.

## Checks before release

1. Run `./verify-source.sh`.
2. Run Gradle `:app:assembleDebug` with Java 17 / Android SDK 35.
3. Verify signer SHA-256 equals `e79f40929fe66e2136424cd32d36c4266c6e890ecd81077258a2c8c66ffa3916`.
4. On Android TV, verify film and episode playback through Stravers, including a title that previously showed the infinite spinner.
5. Verify a preroll/media-ad is skipped or blocked without blocking the main stream.
6. Verify manual ad picker, cancel, persistence after relaunch and undo.
7. Verify both built-in replacement rules, no-replacement mode and a custom rule.
8. Verify Play/Pause short press once and Button Mapper long-press screen-off behavior.
9. Verify native fullscreen entry/exit and air-mouse pointer.
