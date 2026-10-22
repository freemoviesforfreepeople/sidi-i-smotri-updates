# Smoke test 13.11

Passed before packaging:

- `verify-source.sh` — PASS
- shell syntax (`bash -n`) — PASS
- Java parser check with JDK 21 — no Java syntax/string-literal errors (Android classes are unavailable in this container, so this is not a full Android compile)
- Playwright + Chromium visual picker fixture — PASS:
  - a `pointer-events:none` overlay covers the video;
  - native hit-testing sees the underlying `<video>`;
  - the actual `visualAt(...)` function extracted from `MainActivity.java` selects the covering overlay instead of the protected video.
- player choice persistence markers/storage — present (`PlayerChoiceBridge`, `playerIndex`, `playerLabel`, restore path)
- full Android Gradle build — not run here; run `./build.sh` on the Mac build environment.
