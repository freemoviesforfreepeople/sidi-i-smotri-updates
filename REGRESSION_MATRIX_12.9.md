# REGRESSION MATRIX 12.9

## Главная проверка

1. Открыть фильм/сериал со Stravers.
2. В logcat должен появиться `AD_VAST_NOFILL_OK` для `code.moviead55.ru/vast2?...cp.adtype=pre`.
3. Не должно быть Chromium CORS ошибки вида `wildcard '*' when the request's credentials mode is 'include'`.
4. Основной поток должен появиться как `MAIN_MEDIA_ALLOWED` и начать воспроизведение без минутного рекламного waterfall.

## Нельзя ломать

- `code.moviead55.ru/player_js`
- `player.moviead55.ru/.../mstream2inline.js`
- `theatre.stravers.live`
- Kinobox / зеркало плеера
- HLS/DASH/MP4 основного фильма
- fullscreen / air mouse / Play-Pause
- ручные DOM ad-rules и Undo
- replacement rules

## CORS / VAST

- credentialed XHR получает конкретный `Access-Control-Allow-Origin`, не `*`.
- присутствует `Access-Control-Allow-Credentials: true`.
- `OPTIONS` получает быстрый 204.
- GET VAST получает валидный пустой VAST 3.0 как no-fill.

## Ожидаемый результат теста

12.8 показала фактический запуск main playback, но примерно через 74 секунды из-за CORS-rejected no-fill и повторного рекламного waterfall. В 12.9 проверяется устранение именно этой задержки. Целевое время не считается подтверждённым до теста на приставке.
