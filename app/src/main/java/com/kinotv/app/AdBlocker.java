package com.kinotv.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import org.json.JSONArray;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Conservative advertising filter for 13.0 / 13.12 / 13.16.
 *
 * Explicit VAST/VPAID XML decision endpoints observed in diagnostics are neutralized.
 * After primary playback has started (or after user marks «Это видеореклама»), additional
 * resume/midroll ad descriptors and known/learned ad-CDN creatives are neutralized.
 * Player bootstrap JavaScript, Stravers app/bootstrap, and unknown media stay allowed.
 * Manual DOM rules remain separate.
 */
final class AdBlocker {
    private static final int MAX_RECENT = 80;
    private static final int MAX_MEDIA = 40;
    private static final long KEEP_MS = 45_000L;
    private static final String PREFS = "sidi_video_ad_marks_v1";
    private static final String KEY_HOSTS = "hosts";
    private static final int MAX_LEARNED = 40;

    private final Deque<ObservedRequest> recent = new ArrayDeque<>();
    private final Deque<ObservedRequest> recentMedia = new ArrayDeque<>();
    private final SharedPreferences prefs;
    private final Set<String> learnedHosts = new LinkedHashSet<>();
    private volatile long lastVastLogAt;
    private volatile boolean postPlaybackAdSuppress;

    private static final class ObservedRequest {
        final long at;
        final String source;
        final String method;
        final String url;
        final String referer;
        final String origin;
        final boolean mainFrame;
        final boolean adCandidate;

        ObservedRequest(long at, String source, String method, String url, String referer, String origin,
                        boolean mainFrame, boolean adCandidate) {
            this.at = at;
            this.source = source;
            this.method = method;
            this.url = url;
            this.referer = referer;
            this.origin = origin;
            this.mainFrame = mainFrame;
            this.adCandidate = adCandidate;
        }
    }

    AdBlocker(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        loadLearnedHosts();
        if (!learnedHosts.isEmpty()) {
            postPlaybackAdSuppress = true;
            Log.i("SidiAdBlock", "AD_POST_PLAYBACK_SUPPRESS_ON reason=learned-marks hosts=" + learnedHosts.size());
        }
    }

    /** First trusted main media seen — enable resume/midroll suppress without touching cold start. */
    void markPrimaryPlaybackSeen() {
        if (postPlaybackAdSuppress) return;
        postPlaybackAdSuppress = true;
        Log.i("SidiAdBlock", "AD_POST_PLAYBACK_SUPPRESS_ON");
    }

    void resetPlaybackAdState() {
        // Learned marks keep suppress across refresh / new play in this process.
        if (!learnedHosts.isEmpty()) {
            postPlaybackAdSuppress = true;
            Log.i("SidiAdBlock", "AD_POST_PLAYBACK_SUPPRESS_KEEP reason=learned-marks");
            return;
        }
        if (postPlaybackAdSuppress) Log.i("SidiAdBlock", "AD_POST_PLAYBACK_SUPPRESS_RESET");
        postPlaybackAdSuppress = false;
    }

    boolean isPostPlaybackAdSuppress() {
        return postPlaybackAdSuppress;
    }

    boolean hasLearnedVideoAdMarks() {
        return !learnedHosts.isEmpty();
    }

    List<String> learnedVideoAdHosts() {
        synchronized (learnedHosts) {
            return new ArrayList<>(learnedHosts);
        }
    }

    /**
     * User pressed «Это видеореклама»: learn hosts from recent ad/media traffic + optional DOM video src,
     * persist them, and enable suppress for subsequent plays.
     */
    synchronized int learnFromVideoAdMark(String pageUrl, String videoSrc) {
        Set<String> added = new LinkedHashSet<>();
        pruneLocked(SystemClock.elapsedRealtime());
        for (ObservedRequest r : recent) {
            String h = learnableHost(r.url);
            if (h != null) added.add(h);
        }
        for (ObservedRequest r : recentMedia) {
            String h = learnableHost(r.url);
            if (h != null) added.add(h);
        }
        String fromVideo = learnableHost(videoSrc);
        if (fromVideo != null) added.add(fromVideo);
        // Always reinforce known video-ad CDNs when user explicitly marks.
        for (String hint : new String[]{"moviead55", "buzzoola", "video-mech", "moe.video",
                "prestreamnos", "vqserve", "betweendigital", "skcrtxr", "showjet", "ussp.io", "traffaret"}) {
            for (ObservedRequest r : recentMedia) {
                String h = hostOf(r.url);
                if (h.contains(hint) && !isProtectedPlayerHost(h)) added.add(h);
            }
            for (ObservedRequest r : recent) {
                String h = hostOf(r.url);
                if (h.contains(hint) && !isProtectedPlayerHost(h)) added.add(h);
            }
        }
        int before = learnedHosts.size();
        for (String h : added) {
            if (learnedHosts.size() >= MAX_LEARNED) break;
            if (learnedHosts.add(h)) {
                Log.i("SidiVideoAd", "VIDEO_AD_MARK_LEARNED host=" + h + " page=" + safe(pageUrl));
            }
        }
        saveLearnedHosts();
        postPlaybackAdSuppress = true;
        Log.i("SidiAdBlock", "AD_POST_PLAYBACK_SUPPRESS_ON reason=user-video-mark learned=" + learnedHosts.size()
                + " new=" + Math.max(0, learnedHosts.size() - before));
        return added.size();
    }

    void observeRequest(WebResourceRequest request) {
        observeRequest("web", request);
    }

    void observeRequest(String source, WebResourceRequest request) {
        if (request == null || request.getUrl() == null) return;
        String url = request.getUrl().toString();
        Map<String, String> headers = request.getRequestHeaders();
        String referer = headerValue(headers, "Referer");
        String origin = headerValue(headers, "Origin");
        boolean ad = looksLikeAdCandidate(url) || isLearnedHostUrl(url);
        if (ad) {
            remember(recent, new ObservedRequest(SystemClock.elapsedRealtime(), source == null ? "" : source,
                    request.getMethod(), url, referer, origin, request.isForMainFrame(), true));
        }
        if (isLikelyMedia(url) && !isProtectedPlayerHost(hostOf(url))) {
            remember(recentMedia, new ObservedRequest(SystemClock.elapsedRealtime(), source == null ? "" : source,
                    request.getMethod(), url, referer, origin, request.isForMainFrame(), ad));
        }
    }

    void allowSessionUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty() || !looksLikeAdCandidate(rawUrl)) return;
        remember(recent, new ObservedRequest(SystemClock.elapsedRealtime(), "nav", "NAV", rawUrl,
                "", "", true, true));
    }

    boolean isPrimaryPlaybackCandidate(String rawUrl) {
        return isLikelyMedia(rawUrl);
    }

    boolean isKnownAdNavigation(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty()) return false;
        String lower = rawUrl.toLowerCase(Locale.US);
        String host = hostOf(rawUrl);
        return looksLikeAdCandidate(rawUrl)
                || isLearnedHostUrl(rawUrl)
                || host.contains("adhigh.net")
                || host.contains("skcrtxr.com")
                || host.contains("showjet.ru")
                || host.contains("vqserve.com")
                || host.contains("bidster.net")
                || host.contains("adlook")
                || lower.contains("clicktarget=_blank");
    }

    WebResourceResponse interceptKnownVideoAd(WebResourceRequest request) {
        if (request == null || request.getUrl() == null) return null;
        String url = request.getUrl().toString();
        boolean explicit = isExplicitVideoAdDescriptor(url);
        boolean resume = !explicit && postPlaybackAdSuppress && isResumeAdDescriptor(url);
        if (!explicit && !resume) return null;
        Map<String, String> requestHeaders = request.getRequestHeaders();
        String origin = headerValue(requestHeaders, "Origin");
        if (origin.isEmpty()) origin = originOf(headerValue(requestHeaders, "Referer"));

        Map<String, String> headers = new LinkedHashMap<>();
        if (!origin.isEmpty()) {
            headers.put("Access-Control-Allow-Origin", origin);
            headers.put("Access-Control-Allow-Credentials", "true");
            headers.put("Vary", "Origin");
        }
        headers.put("Access-Control-Allow-Methods", "GET, OPTIONS");
        String requestedHeaders = headerValue(requestHeaders, "Access-Control-Request-Headers");
        if (!requestedHeaders.isEmpty()) headers.put("Access-Control-Allow-Headers", requestedHeaders);
        headers.put("Cache-Control", "no-store, no-cache, must-revalidate");

        String method = request.getMethod();
        String kindPrefix = resume ? "AD_RESUME_SUPPRESS" : "AD_VAST";
        if (method != null && "OPTIONS".equalsIgnoreCase(method)) {
            logVastSample(kindPrefix + "_PREFLIGHT_OK", origin, url);
            return new WebResourceResponse("text/plain", "utf-8", 204, "No Content", headers,
                    new ByteArrayInputStream(new byte[0]));
        }

        if (resume && isLikelyAdCreativeMedia(url)) {
            logVastSample("AD_RESUME_SUPPRESS_MEDIA_OK", origin, url);
            return new WebResourceResponse("application/octet-stream", "utf-8", 204, "No Content", headers,
                    new ByteArrayInputStream(new byte[0]));
        }

        logVastSample(resume ? "AD_RESUME_SUPPRESS_OK" : "AD_VAST_NOFILL_OK", origin, url);
        byte[] body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><VAST version=\"3.0\"></VAST>"
                .getBytes(StandardCharsets.UTF_8);
        return new WebResourceResponse("application/xml", "utf-8", 200, "OK", headers,
                new ByteArrayInputStream(body));
    }

    private void logVastSample(String kind, String origin, String url) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastVastLogAt < 5000L) return;
        lastVastLogAt = now;
        Log.i("SidiAdBlock", kind + " origin=" + origin + " host=" + hostOf(url));
    }

    synchronized void dumpRecentRequests(long markerId, String pageUrl) {
        pruneLocked(SystemClock.elapsedRealtime());
        Log.i("SidiVideoAd", "VIDEO_AD_CONTEXT_BEGIN id=" + markerId
                + " page=" + (pageUrl == null ? "" : pageUrl) + " count=" + recent.size()
                + " media=" + recentMedia.size() + " learned=" + learnedHosts.size());
        long now = SystemClock.elapsedRealtime();
        for (ObservedRequest r : recent) {
            logObserved("VIDEO_AD_REQUEST", markerId, now, r);
        }
        for (ObservedRequest r : recentMedia) {
            logObserved("VIDEO_AD_MEDIA", markerId, now, r);
        }
        Log.i("SidiVideoAd", "VIDEO_AD_CONTEXT_END id=" + markerId);
    }

    private void logObserved(String kind, long markerId, long now, ObservedRequest r) {
        Log.i("SidiVideoAd", kind + " id=" + markerId
                + " ageMs=" + Math.max(0L, now - r.at)
                + " candidate=" + r.adCandidate
                + " mainFrame=" + r.mainFrame
                + " source=" + safe(r.source)
                + " method=" + safe(r.method)
                + " url=" + safe(r.url)
                + " referer=" + safe(r.referer)
                + " origin=" + safe(r.origin));
    }

    void shutdown() {
        synchronized (this) {
            recent.clear();
            recentMedia.clear();
        }
    }

    private void remember(Deque<ObservedRequest> deque, ObservedRequest request) {
        long now = SystemClock.elapsedRealtime();
        synchronized (this) {
            deque.addLast(request);
            pruneLocked(now);
            int max = deque == recentMedia ? MAX_MEDIA : MAX_RECENT;
            while (deque.size() > max) deque.removeFirst();
        }
    }

    private void pruneLocked(long now) {
        while (!recent.isEmpty() && now - recent.peekFirst().at > KEEP_MS) recent.removeFirst();
        while (!recentMedia.isEmpty() && now - recentMedia.peekFirst().at > KEEP_MS) recentMedia.removeFirst();
    }

    private void loadLearnedHosts() {
        learnedHosts.clear();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_HOSTS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                String h = normalizeHost(arr.optString(i, ""));
                if (!h.isEmpty() && !isProtectedPlayerHost(h)) learnedHosts.add(h);
            }
        } catch (Throwable ignored) {
        }
        if (!learnedHosts.isEmpty()) {
            Log.i("SidiVideoAd", "VIDEO_AD_MARKS_LOADED count=" + learnedHosts.size());
        }
    }

    private void saveLearnedHosts() {
        JSONArray arr = new JSONArray();
        for (String h : learnedHosts) arr.put(h);
        prefs.edit().putString(KEY_HOSTS, arr.toString()).apply();
    }

    private boolean isLearnedHostUrl(String rawUrl) {
        String host = hostOf(rawUrl);
        if (host.isEmpty()) return false;
        synchronized (learnedHosts) {
            if (learnedHosts.contains(host)) return true;
            for (String h : learnedHosts) {
                if (host.endsWith("." + h) || host.contains(h)) return true;
            }
        }
        return false;
    }

    private String learnableHost(String rawUrl) {
        String host = hostOf(rawUrl);
        if (host.isEmpty() || isProtectedPlayerHost(host)) return null;
        // Only learn hosts that look like ad / marked creative CDNs — not random CDNs.
        if (looksLikeAdCandidate(rawUrl) || isBuiltinAdCreativeHost(host) || isLearnedHostUrl(rawUrl)) {
            return host;
        }
        // Media on non-player host while user is marking — allow if path looks like ad creative.
        String lower = rawUrl == null ? "" : rawUrl.toLowerCase(Locale.US);
        if (isLikelyMedia(rawUrl) && (lower.contains("advert") || lower.contains("/ad/")
                || lower.contains("preroll") || lower.contains("midroll") || lower.contains("vast")
                || host.contains("moe.video") || host.contains("cdn") && lower.contains("ad"))) {
            return host;
        }
        return null;
    }

    private static boolean isBuiltinAdCreativeHost(String host) {
        return host.contains("moviead55") || host.contains("buzzoola") || host.contains("video-mech")
                || host.contains("vqserve") || host.contains("prestreamnos") || host.contains("betweendigital")
                || host.contains("skcrtxr") || host.contains("showjet") || host.contains("ussp.io")
                || host.contains("moe.video") || host.contains("traffaret") || host.contains("adriver");
    }

    private static boolean isProtectedPlayerHost(String host) {
        if (host == null || host.isEmpty()) return true;
        return host.contains("stravers.live") || host.contains("bulkikim") || host.contains("villybizy")
                || host.contains("alloha") || host.contains("kodik") || host.contains("voidboost")
                || host.contains("videocdn") || host.contains("collaps") || host.contains("hdvb")
                || host.contains("kinopoisk") || host.contains("fixtyflux") || host.contains("gorodyshka");
    }

    private static String headerValue(Map<String, String> headers, String key) {
        if (headers == null || key == null) return "";
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && key.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue() == null ? "" : entry.getValue();
            }
        }
        return "";
    }

    private static String safe(String value) {
        if (value == null) return "";
        return value.replace('\n', ' ').replace('\r', ' ');
    }

    private static String originOf(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty()) return "";
        try {
            Uri uri = Uri.parse(rawUrl);
            String scheme = uri.getScheme();
            String authority = uri.getEncodedAuthority();
            if (scheme == null || authority == null || scheme.isEmpty() || authority.isEmpty()) return "";
            return scheme + "://" + authority;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean isExplicitVideoAdDescriptor(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty()) return false;
        try {
            Uri uri = Uri.parse(rawUrl);
            String host = uri.getHost();
            String path = uri.getPath();
            host = host == null ? "" : host.toLowerCase(Locale.US);
            path = path == null ? "" : path.toLowerCase(Locale.US);

            if ("code.moviead55.ru".equals(host) && "/vast2".equals(path)) return true;
            if ("ads.vqserve.com".equals(host) && path.startsWith("/vast/")) return true;
            if ("ad.mail.ru".equals(host) && path.startsWith("/vast/")) return true;
            if ("ssp.bidster.net".equals(host) && path.startsWith("/vast/")) return true;
            if ("a.ussp.io".equals(host) && "/vast".equals(path)) return true;
            if ("imasdk.showjet.ru".equals(host) && path.startsWith("/ads/vast/")) return true;
            return "cdn-v.skcrtxr.com".equals(host) && path.endsWith("/vpaid.xml");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isResumeAdDescriptor(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty()) return false;
        String lower = rawUrl.toLowerCase(Locale.US);
        String host = hostOf(rawUrl);
        if (isProtectedPlayerHost(host)) return false;
        if (lower.contains("player_js") || lower.contains("mstream2inline")
                || lower.contains("stravers.live/build/") || lower.contains("theatre.stravers.live/build/")) {
            return false;
        }
        if (isExplicitVideoAdDescriptor(rawUrl)) return true;
        if (isLearnedHostUrl(rawUrl) && (isLikelyMedia(rawUrl) || looksLikeAdCandidate(rawUrl))) return true;
        if (!looksLikeAdCandidate(rawUrl) && !isLikelyAdCreativeMedia(rawUrl)) return false;
        if (lower.contains("vast") || lower.contains("vpaid") || lower.contains("vmap")
                || lower.contains("/preroll") || lower.contains("/midroll") || lower.contains("/postroll")) {
            return true;
        }
        return isLikelyAdCreativeMedia(rawUrl);
    }

    private boolean isLikelyAdCreativeMedia(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty()) return false;
        String host = hostOf(rawUrl);
        String lower = rawUrl.toLowerCase(Locale.US);
        boolean media = lower.contains(".mp4") || lower.contains(".webm") || lower.contains(".m3u8")
                || lower.contains(".mpd") || lower.contains(".m4s");
        if (!media) return false;
        if (isProtectedPlayerHost(host)) return false;
        if (isLearnedHostUrl(rawUrl)) return true;
        return isBuiltinAdCreativeHost(host);
    }

    private static boolean looksLikeAdCandidate(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty()) return false;
        String lower = rawUrl.toLowerCase(Locale.US);
        String host = hostOf(rawUrl);
        return host.contains("moviead55")
                || host.contains("buzzoola")
                || host.contains("video-mech")
                || host.contains("betweendigital")
                || host.contains("adriver")
                || host.contains("alfasense")
                || host.contains("ussp.io")
                || host.contains("traffaret")
                || host.contains("moe.video")
                || host.startsWith("ad.")
                || host.startsWith("ads.")
                || lower.contains("/preroll")
                || lower.contains("/midroll")
                || lower.contains("/postroll")
                || lower.contains("vast")
                || lower.contains("vpaid")
                || lower.contains("vmap")
                || lower.contains("prebid")
                || lower.contains("adtag")
                || lower.contains("ad_tag");
    }

    private static boolean isLikelyMedia(String rawUrl) {
        if (rawUrl == null) return false;
        String lower = rawUrl.toLowerCase(Locale.US);
        return lower.contains(".m3u8") || lower.contains(".mpd") || lower.contains(".mp4")
                || lower.contains(".webm") || lower.contains(".m4s") || lower.contains(".ts?")
                || lower.endsWith(".ts") || lower.contains("/hls/") || lower.contains("/dash/")
                || lower.contains("manifest") || lower.startsWith("blob:");
    }

    private static String hostOf(String rawUrl) {
        try {
            String host = Uri.parse(rawUrl).getHost();
            return host == null ? "" : host.toLowerCase(Locale.US);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String normalizeHost(String host) {
        if (host == null) return "";
        String h = host.trim().toLowerCase(Locale.US);
        if (h.startsWith("www.")) h = h.substring(4);
        return h;
    }
}
