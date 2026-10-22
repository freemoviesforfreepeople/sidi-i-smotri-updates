package com.kinotv.app;

import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

final class MovieRecord {
    String key;
    String title;
    String year;
    String type;
    String sourceUrl;
    String posterUrl;
    boolean favorite;
    String note;
    String translation;
    boolean translationManual;
    boolean episodeManual;
    int season;
    int episode;
    long positionSeconds;
    long durationSeconds;
    int playerIndex;
    String playerLabel;
    long updatedAt;

    MovieRecord() {
        title = "Видео";
        year = "";
        type = "film";
        sourceUrl = "";
        posterUrl = "";
        note = "";
        translation = "";
        playerIndex = -1;
        playerLabel = "";
        updatedAt = System.currentTimeMillis();
    }

    static String keyFromUrl(String rawUrl) {
        try {
            Uri uri = Uri.parse(rawUrl);
            List<String> parts = uri.getPathSegments();
            if (parts.size() >= 2 && ("film".equals(parts.get(0)) || "series".equals(parts.get(0)))) {
                return parts.get(0) + ":" + parts.get(1);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    static MovieRecord fromSearch(String title, String year, String type, String url, String poster) {
        MovieRecord record = new MovieRecord();
        record.key = keyFromUrl(url);
        record.title = clean(title, "Видео");
        record.year = clean(year, "");
        record.type = "series".equals(type) ? "series" : "film";
        record.sourceUrl = url == null ? "" : url;
        record.posterUrl = poster == null ? "" : poster;
        record.updatedAt = System.currentTimeMillis();
        return record;
    }

    String playerUrl() {
        Uri uri = Uri.parse(sourceUrl);
        return uri.buildUpon()
                .scheme("https")
                .encodedAuthority("www.sspoisk.ru")
                .clearQuery()
                .fragment(null)
                .build()
                .toString();
    }

    String progressLabel() {
        StringBuilder out = new StringBuilder();
        if (season > 0) out.append("Сезон ").append(season);
        if (episode > 0) {
            if (out.length() > 0) out.append(" · ");
            out.append("Серия ").append(episode);
        }
        if (!translation.trim().isEmpty()) {
            if (out.length() > 0) out.append(" · ");
            out.append(translation.trim());
        }
        if (positionSeconds > 0) {
            if (out.length() > 0) out.append(" · ");
            out.append(formatTime(positionSeconds));
        }
        return out.toString();
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("key", key);
        o.put("title", title);
        o.put("year", year);
        o.put("type", type);
        o.put("sourceUrl", sourceUrl);
        o.put("posterUrl", posterUrl);
        o.put("favorite", favorite);
        o.put("note", note);
        o.put("translation", translation);
        o.put("translationManual", translationManual);
        o.put("episodeManual", episodeManual);
        o.put("season", season);
        o.put("episode", episode);
        o.put("positionSeconds", positionSeconds);
        o.put("durationSeconds", durationSeconds);
        o.put("playerIndex", playerIndex);
        o.put("playerLabel", playerLabel);
        o.put("updatedAt", updatedAt);
        return o;
    }

    static MovieRecord fromJson(JSONObject o) {
        MovieRecord record = new MovieRecord();
        record.key = o.optString("key", "");
        record.title = clean(o.optString("title", "Видео"), "Видео");
        record.year = o.optString("year", "");
        record.type = "series".equals(o.optString("type")) ? "series" : "film";
        record.sourceUrl = o.optString("sourceUrl", o.optString("url", ""));
        record.posterUrl = o.optString("posterUrl", "");
        record.favorite = o.optBoolean("favorite", false);
        record.note = o.optString("note", "");
        record.translation = o.optString("translation", "");
        record.translationManual = o.optBoolean("translationManual", false);
        record.episodeManual = o.optBoolean("episodeManual", false);
        record.season = Math.max(0, o.optInt("season", 0));
        record.episode = Math.max(0, o.optInt("episode", 0));
        record.positionSeconds = Math.max(0, o.optLong("positionSeconds", 0));
        record.durationSeconds = Math.max(0, o.optLong("durationSeconds", 0));
        record.playerIndex = o.has("playerIndex") ? o.optInt("playerIndex", -1) : -1;
        if (record.playerIndex < -1) record.playerIndex = -1;
        record.playerLabel = o.optString("playerLabel", "");
        record.updatedAt = o.optLong("updatedAt", System.currentTimeMillis());
        if (record.key.isEmpty()) record.key = keyFromUrl(record.sourceUrl);
        return record;
    }

    private static String clean(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        return value.trim();
    }

    static String formatTime(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return h > 0
                ? String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
                : String.format(java.util.Locale.US, "%d:%02d", m, s);
    }
}
