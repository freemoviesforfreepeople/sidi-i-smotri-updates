# Adblock test matrix 12.5

Expected BLOCK:
- https://player.moviead55.ru/ad/preroll.mp4
- https://ssp.video-mech.ru/path/video.m3u8
- https://tube.buzzoola.com/creative/video.mp4
- https://example-ad-cdn.test/vast.xml
- https://theatre.stravers.live/ads/system/preroll.m3u8
- media with Referer/Origin pointing to a known ad host
- main-frame/popup navigation to known ad hosts

Expected ALLOW:
- https://theatre.stravers.live/build/app.js
- https://theatre.stravers.live/content/master.m3u8
- unknown rotating CDN https://cdn-new.example/video/master.m3u8 when URL and context have no ad signature
- SSPoisk -> bulkikim.* same-film redirects

DOM cleanup:
- may remove banner/promo/telegram/floating widgets
- must not remove video or trusted player iframe
