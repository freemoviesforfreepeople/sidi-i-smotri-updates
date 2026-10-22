package com.kinotv.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MovieStore {
    private static final String PREFS = "kino_tv_prefs";
    private static final String DATA = "library_v2";
    private static final String RECENTS = "recent_keys_v2";
    private static final String TABS = "open_tabs_v2";
    private static final String ACTIVE_TAB = "active_tab_v2";
    private static final String LEGACY_RECENTS = "recent_items";
    private static final int MAX_RECENTS = 30;

    private final SharedPreferences prefs;
    private final Map<String, MovieRecord> records = new LinkedHashMap<>();
    private final List<String> recentKeys = new ArrayList<>();

    MovieStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
        migrateLegacy();
        sanitizeDuplicatePosters();
    }

    synchronized MovieRecord merge(MovieRecord incoming) {
        if (incoming == null || incoming.key == null || incoming.key.isEmpty()) return incoming;
        MovieRecord old = records.get(incoming.key);
        if (old == null) {
            records.put(incoming.key, incoming);
            save();
            return incoming;
        }
        if (!incoming.title.isEmpty() && !incoming.title.matches("(Фильм|Сериал) \\d+")) old.title = incoming.title;
        if (!incoming.year.isEmpty()) old.year = incoming.year;
        if (!incoming.type.isEmpty()) old.type = incoming.type;
        if (!incoming.sourceUrl.isEmpty()) old.sourceUrl = incoming.sourceUrl;
        if (!incoming.posterUrl.isEmpty()) old.posterUrl = incoming.posterUrl;
        old.updatedAt = System.currentTimeMillis();
        save();
        return old;
    }

    synchronized MovieRecord get(String key) {
        return records.get(key);
    }

    synchronized MovieRecord getOrCreateFromUrl(String title, String url) {
        String key = MovieRecord.keyFromUrl(url);
        MovieRecord found = records.get(key);
        if (found != null) return found;
        String type = key.startsWith("series:") ? "series" : "film";
        MovieRecord created = MovieRecord.fromSearch(title, "", type, url, "");
        records.put(created.key, created);
        save();
        return created;
    }

    synchronized void touchRecent(MovieRecord record) {
        if (record == null || record.key == null || record.key.isEmpty()) return;
        records.put(record.key, record);
        recentKeys.remove(record.key);
        recentKeys.add(0, record.key);
        while (recentKeys.size() > MAX_RECENTS) recentKeys.remove(recentKeys.size() - 1);
        record.updatedAt = System.currentTimeMillis();
        save();
    }

    synchronized void removeRecent(String key) {
        recentKeys.remove(key);
        save();
    }

    synchronized void update(MovieRecord record) {
        if (record == null || record.key == null || record.key.isEmpty()) return;
        record.updatedAt = System.currentTimeMillis();
        records.put(record.key, record);
        save();
    }

    synchronized List<MovieRecord> recents() {
        List<MovieRecord> result = new ArrayList<>();
        for (String key : recentKeys) {
            MovieRecord record = records.get(key);
            if (record != null) result.add(record);
        }
        return result;
    }

    synchronized List<MovieRecord> favorites() {
        List<MovieRecord> result = new ArrayList<>();
        for (MovieRecord record : records.values()) if (record.favorite) result.add(record);
        result.sort((a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        return result;
    }

    synchronized int clearHistory() {
        int count = recentKeys.size();
        recentKeys.clear();
        save();
        return count;
    }

    synchronized int clearFavorites() {
        int count = 0;
        for (MovieRecord record : records.values()) {
            if (record.favorite) {
                record.favorite = false;
                record.updatedAt = System.currentTimeMillis();
                count++;
            }
        }
        save();
        return count;
    }

    synchronized void saveTabs(List<String> keys, String activeKey) {
        JSONArray a = new JSONArray();
        for (String key : keys) if (records.containsKey(key)) a.put(key);
        prefs.edit().putString(TABS, a.toString())
                .putString(ACTIVE_TAB, activeKey == null ? "" : activeKey).apply();
    }

    synchronized List<String> loadTabKeys() {
        return parseStringArray(prefs.getString(TABS, "[]"));
    }

    synchronized String loadActiveTabKey() {
        return prefs.getString(ACTIVE_TAB, "");
    }

    synchronized JSONObject exportData() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("version", 2);
        JSONArray items = new JSONArray();
        for (MovieRecord record : records.values()) items.put(record.toJson());
        root.put("items", items);
        JSONArray recentsJson = new JSONArray();
        for (String key : recentKeys) recentsJson.put(key);
        root.put("recents", recentsJson);
        return root;
    }

    synchronized int importData(JSONObject root) {
        int changed = 0;
        JSONArray items = root.optJSONArray("items");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject o = items.optJSONObject(i);
                if (o == null) continue;
                MovieRecord incoming = MovieRecord.fromJson(o);
                if (incoming.key.isEmpty()) continue;
                MovieRecord old = records.get(incoming.key);
                if (old == null || incoming.updatedAt >= old.updatedAt) {
                    records.put(incoming.key, incoming);
                    changed++;
                }
            }
        }
        JSONArray recent = root.optJSONArray("recents");
        if (recent != null) {
            for (int i = recent.length() - 1; i >= 0; i--) {
                String key = recent.optString(i, "");
                if (records.containsKey(key)) {
                    recentKeys.remove(key);
                    recentKeys.add(0, key);
                }
            }
        }
        while (recentKeys.size() > MAX_RECENTS) recentKeys.remove(recentKeys.size() - 1);
        save();
        return changed;
    }


    private synchronized void sanitizeDuplicatePosters() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (MovieRecord record : records.values()) {
            String poster = record.posterUrl == null ? "" : record.posterUrl.trim();
            if (!poster.isEmpty()) counts.put(poster, counts.getOrDefault(poster, 0) + 1);
        }
        boolean changed = false;
        for (MovieRecord record : records.values()) {
            String poster = record.posterUrl == null ? "" : record.posterUrl.trim();
            if (!poster.isEmpty() && counts.getOrDefault(poster, 0) > 1) {
                record.posterUrl = "";
                changed = true;
            }
        }
        if (changed) save();
    }

    private synchronized void load() {
        records.clear();
        recentKeys.clear();
        try {
            JSONArray a = new JSONArray(prefs.getString(DATA, "[]"));
            for (int i = 0; i < a.length(); i++) {
                MovieRecord record = MovieRecord.fromJson(a.getJSONObject(i));
                if (!record.key.isEmpty()) records.put(record.key, record);
            }
        } catch (Exception ignored) {
        }
        recentKeys.addAll(parseStringArray(prefs.getString(RECENTS, "[]")));
    }

    private synchronized void migrateLegacy() {
        if (!records.isEmpty() || !recentKeys.isEmpty()) return;
        String legacy = prefs.getString(LEGACY_RECENTS, "[]");
        try {
            JSONArray a = new JSONArray(legacy);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                String url = o.optString("url", "");
                String key = MovieRecord.keyFromUrl(url);
                if (key.isEmpty()) continue;
                MovieRecord record = MovieRecord.fromSearch(
                        o.optString("title", "Видео"), "",
                        key.startsWith("series:") ? "series" : "film", url, "");
                records.put(key, record);
                recentKeys.add(key);
            }
            save();
        } catch (Exception ignored) {
        }
    }

    private synchronized void save() {
        JSONArray items = new JSONArray();
        for (MovieRecord record : records.values()) {
            try {
                items.put(record.toJson());
            } catch (JSONException ignored) {
            }
        }
        JSONArray recents = new JSONArray();
        for (String key : recentKeys) recents.put(key);
        prefs.edit().putString(DATA, items.toString()).putString(RECENTS, recents.toString()).apply();
    }

    private static List<String> parseStringArray(String json) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(json == null ? "[]" : json);
            for (int i = 0; i < a.length(); i++) {
                String value = a.optString(i, "");
                if (!value.isEmpty() && !out.contains(value)) out.add(value);
            }
        } catch (Exception ignored) {
        }
        return out;
    }
}
