package com.kinotv.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class PlayerUrlRules {
    static final String RULE_SS = "builtin_ss";
    static final String RULE_KINO_WIN = "builtin_kino_win";
    private static final String PREFS = "sidi_player_url_rules";
    private static final String KEY_CUSTOM = "custom_rules";
    private static final String KEY_ACTIVE = "active_rule_id";

    static final class Rule {
        final String id;
        final String label;
        final String from;
        final String to;
        final boolean builtIn;

        Rule(String id, String label, String from, String to, boolean builtIn) {
            this.id = id;
            this.label = label;
            this.from = from;
            this.to = to;
            this.builtIn = builtIn;
        }
    }

    private final SharedPreferences prefs;

    PlayerUrlRules(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.contains(KEY_ACTIVE)) prefs.edit().putString(KEY_ACTIVE, RULE_SS).apply();
    }

    List<Rule> allRules() {
        List<Rule> out = new ArrayList<>();
        out.add(new Rule(RULE_SS, "kino → ss", "kinopoisk.ru", "www.sspoisk.ru", true));
        out.add(new Rule(RULE_KINO_WIN, "poisk.ru → kino.win", "kinopoisk.ru", "kino.win", true));
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY_CUSTOM, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) continue;
                String id = o.optString("id", "").trim();
                String from = o.optString("from", "").trim();
                String to = o.optString("to", "").trim();
                if (id.isEmpty() || from.isEmpty() || to.isEmpty()) continue;
                out.add(new Rule(id, from + " → " + to, from, to, false));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    String activeRuleId() {
        String id = prefs.getString(KEY_ACTIVE, RULE_SS);
        if (id == null) return "";
        for (Rule rule : allRules()) if (rule.id.equals(id)) return id;
        return "";
    }

    String activeRuleLabel() {
        String id = activeRuleId();
        if (id.isEmpty()) return "без подмены";
        for (Rule rule : allRules()) if (rule.id.equals(id)) return rule.label;
        return "без подмены";
    }

    void setActiveRule(String id) {
        String safe = id == null ? "" : id;
        boolean exists = safe.isEmpty();
        for (Rule rule : allRules()) if (rule.id.equals(safe)) { exists = true; break; }
        prefs.edit().putString(KEY_ACTIVE, exists ? safe : "").apply();
    }

    Rule addCustomRule(String from, String to) {
        String cleanFrom = from == null ? "" : from.trim();
        String cleanTo = to == null ? "" : to.trim();
        if (cleanFrom.isEmpty() || cleanTo.isEmpty()) throw new IllegalArgumentException("Оба поля обязательны");
        String id = "custom_" + UUID.randomUUID().toString();
        JSONArray array;
        try { array = new JSONArray(prefs.getString(KEY_CUSTOM, "[]")); }
        catch (Throwable ignored) { array = new JSONArray(); }
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("from", cleanFrom);
            o.put("to", cleanTo);
            array.put(o);
        } catch (Throwable ignored) {
        }
        prefs.edit().putString(KEY_CUSTOM, array.toString()).putString(KEY_ACTIVE, id).apply();
        return new Rule(id, cleanFrom + " → " + cleanTo, cleanFrom, cleanTo, false);
    }

    boolean deleteCustomRule(String id) {
        if (id == null || id.isEmpty()) return false;
        JSONArray next = new JSONArray();
        boolean removed = false;
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY_CUSTOM, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) continue;
                if (id.equals(o.optString("id"))) { removed = true; continue; }
                next.put(o);
            }
        } catch (Throwable ignored) {
        }
        if (removed) {
            SharedPreferences.Editor edit = prefs.edit().putString(KEY_CUSTOM, next.toString());
            if (id.equals(activeRuleId())) edit.putString(KEY_ACTIVE, "");
            edit.apply();
        }
        return removed;
    }

    String apply(String sourceUrl) {
        if (sourceUrl == null) return "";
        Rule active = null;
        String activeId = activeRuleId();
        for (Rule rule : allRules()) if (rule.id.equals(activeId)) { active = rule; break; }
        if (active == null) return sourceUrl;
        if (RULE_SS.equals(active.id)) return replaceKinopoiskHost(sourceUrl, "www.sspoisk.ru");
        if (RULE_KINO_WIN.equals(active.id)) return replaceKinopoiskHost(sourceUrl, "kino.win");
        return applyCustom(sourceUrl, active.from, active.to);
    }

    boolean isKnownMirrorHost(String host) {
        if (host == null || host.trim().isEmpty()) return false;
        String h = host.toLowerCase(Locale.US);
        if (h.equals("sspoisk.ru") || h.endsWith(".sspoisk.ru") || h.equals("kino.win") || h.endsWith(".kino.win")) return true;
        for (Rule rule : allRules()) {
            if (rule.builtIn) continue;
            String targetHost = hostCandidate(rule.to);
            if (!targetHost.isEmpty() && (h.equals(targetHost) || h.endsWith("." + targetHost))) return true;
        }
        return false;
    }

    private static String replaceKinopoiskHost(String sourceUrl, String targetHost) {
        try {
            Uri uri = Uri.parse(sourceUrl.trim());
            String host = uri.getHost();
            if (host == null) return sourceUrl;
            String h = host.toLowerCase(Locale.US);
            if (!(h.equals("kinopoisk.ru") || h.equals("www.kinopoisk.ru"))) return sourceUrl;
            return uri.buildUpon().scheme("https").encodedAuthority(targetHost).clearQuery().fragment(null).build().toString();
        } catch (Throwable ignored) {
            return sourceUrl;
        }
    }

    private static String applyCustom(String sourceUrl, String from, String to) {
        try {
            Uri uri = Uri.parse(sourceUrl.trim());
            String currentHost = uri.getHost();
            String fromHost = hostCandidate(from);
            String toHost = hostCandidate(to);
            if (currentHost != null && !fromHost.isEmpty() && !toHost.isEmpty()) {
                String h = currentHost.toLowerCase(Locale.US);
                if (h.equals(fromHost) || h.endsWith("." + fromHost)) {
                    return uri.buildUpon().encodedAuthority(toHost).build().toString();
                }
            }
        } catch (Throwable ignored) {
        }
        return sourceUrl.replace(from, to);
    }

    private static String hostCandidate(String value) {
        if (value == null) return "";
        String v = value.trim().toLowerCase(Locale.US);
        if (v.isEmpty()) return "";
        try {
            Uri parsed = Uri.parse(v.contains("://") ? v : "https://" + v);
            String host = parsed.getHost();
            if (host != null && !host.isEmpty() && (parsed.getPath() == null || parsed.getPath().isEmpty() || "/".equals(parsed.getPath()))) {
                return host.startsWith("www.") ? host.substring(4) : host;
            }
        } catch (Throwable ignored) {
        }
        return "";
    }
}
