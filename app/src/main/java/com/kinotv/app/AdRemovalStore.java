package com.kinotv.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class AdRemovalStore {
    private static final String PREFS = "sidi_manual_ad_rules_manual_only_v1";
    private static final String KEY_RULES = "rules";
    private static final String KEY_SCHEMA = "schema";
    /** 139: seed nTgQB (live yellow popup) + sticky remove random-id (13.21). */
    private static final int SCHEMA = 139;
    private static final int MAX_RULES = 100;

    static final class Rule {
        final String id;
        final String host;
        final String selector;
        final String tag;
        final String href;
        final String text;
        final long createdAt;

        Rule(String id, String host, String selector, String tag, String href, String text, long createdAt) {
            this.id = id;
            this.host = host;
            this.selector = selector;
            this.tag = tag;
            this.href = href;
            this.text = text;
            this.createdAt = createdAt;
        }
    }

    private final SharedPreferences prefs;

    AdRemovalStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int schema = prefs.getInt(KEY_SCHEMA, 0);
        if (schema != SCHEMA) {
            // Do not wipe user rules — migrate + sanitize (13.15).
            prefs.edit().putInt(KEY_SCHEMA, SCHEMA).apply();
            Log.i("SidiAdPicker", "AD_RULES_SCHEMA_MIGRATE from=" + schema + " to=" + SCHEMA);
        }
        sanitizeDangerousRules();
        ensureApprovedLearnedRules();
    }

    /**
     * Session 2026-10-22: intentional random-id overlay prefixes on fixtyflux (mTTEi, nGRXI).
     * Prefixes rotate on reload — pattern auto-hide covers ALL /^[A-Za-z]{5}[0-9]{10,22}$/ banners.
     * Never seed obrut iframe rules: Kinobox «Turbo» embeds https://*.obrut.show/embed/… (13.17 regression).
     */
    private void ensureApprovedLearnedRules() {
        try {
            add("fixtyflux.cfd", "[id^=\"mTTEi\"]", "div", "", "");
            Log.i("SidiAdPicker", "AD_RULE_LEARNED_APPROVED host=fixtyflux.cfd selector=[id^=\"mTTEi\"]");
        } catch (Throwable ignored) {
        }
        try {
            add("fixtyflux.cfd", "[id^=\"nGRXI\"]", "div", "", "");
            Log.i("SidiAdPicker", "AD_RULE_LEARNED_APPROVED host=fixtyflux.cfd selector=[id^=\"nGRXI\"]");
        } catch (Throwable ignored) {
        }
        try {
            add("fixtyflux.cfd", "[id^=\"odPgP\"]", "div", "", "");
            Log.i("SidiAdPicker", "AD_RULE_LEARNED_APPROVED host=fixtyflux.cfd selector=[id^=\"odPgP\"]");
        } catch (Throwable ignored) {
        }
        try {
            add("fixtyflux.cfd", "iframe[src*=\"moviead55\"]", "iframe", "", "");
            Log.i("SidiAdPicker", "AD_RULE_LEARNED_APPROVED host=fixtyflux.cfd selector=iframe[src*=\"moviead55\"]");
        } catch (Throwable ignored) {
        }
        try {
            add("fixtyflux.cfd", "[id^=\"nTgQB\"]", "div", "", "");
            Log.i("SidiAdPicker", "AD_RULE_LEARNED_APPROVED host=fixtyflux.cfd selector=[id^=\"nTgQB\"]");
        } catch (Throwable ignored) {
        }
        try {
            add("fixtyflux.cfd", "[id^=\"dcXeb\"]", "div", "", "");
            Log.i("SidiAdPicker", "AD_RULE_LEARNED_APPROVED host=fixtyflux.cfd selector=[id^=\"dcXeb\"]");
        } catch (Throwable ignored) {
        }
    }

    /** Drop rules that hide the player / our chrome / absurdly broad selectors. */
    synchronized void sanitizeDangerousRules() {
        List<Rule> rules = all();
        List<Rule> kept = new ArrayList<>();
        int removed = 0;
        for (Rule rule : rules) {
            if (isDangerousRule(rule)) {
                removed++;
                Log.i("SidiAdPicker", "AD_RULE_SANITIZE_DROP host=" + rule.host
                        + " selector=" + rule.selector);
                continue;
            }
            kept.add(rule);
        }
        if (removed > 0) {
            save(kept);
            Log.i("SidiAdPicker", "AD_RULE_SANITIZE_DONE removed=" + removed + " kept=" + kept.size());
        }
    }

    static boolean isDangerousRule(Rule rule) {
        if (rule == null) return true;
        return isDangerousSelector(rule.selector, rule.tag, rule.href, rule.host);
    }

    static boolean isDangerousSelector(String selector, String tag, String href, String host) {
        String s = selector == null ? "" : selector.trim().toLowerCase(Locale.US);
        String t = tag == null ? "" : tag.trim().toLowerCase(Locale.US);
        String u = href == null ? "" : href.trim().toLowerCase(Locale.US);
        String h = host == null ? "" : host.trim().toLowerCase(Locale.US);
        if (s.isEmpty() || s.equals("div") || s.equals("span") || s.equals("a")
                || s.equals("iframe") || s.equals("html") || s.equals("body")
                || s.equals("video") || s.equals("audio")) return true;
        if (s.contains("__sidi_") || s.contains("sidi_picker") || s.contains("sidi_ad_picker")) return true;
        if (s.contains("kinobox") || s.contains("kbt_") || s.contains("allplay")
                || s.contains("stravers") || s.contains("voidboost") || s.contains("kodik")) return true;
        // Turbo / other Kinobox providers use these hosts as the main player iframe.
        if (s.contains("obrut") || s.contains("nextembed") || s.contains("shortener2")
                || s.contains("videotoday.site")) return true;
        if (h.contains("stravers.live") && !(s.contains("rmp-") || s.contains("vpaid")
                || s.contains("vast") || s.contains("ad-"))) return true;
        if (u.contains("stravers.live") || u.contains("bulkikim") || u.contains("villybizy")
                || u.contains("alloha") || u.contains("kodik") || u.contains("voidboost")
                || u.contains("videocdn") || u.contains("collaps") || u.contains("hdvb")
                || u.contains("obrut") || u.contains("nextembed") || u.contains("shortener2")) return true;
        if ("iframe".equals(t) && s.equals("iframe")) return true;
        return false;
    }

    synchronized Rule add(String host, String selector, String tag, String href, String text) {
        String cleanHost = normalizeHost(host);
        String cleanSelector = clean(selector, 500);
        String cleanTag = clean(tag, 32).toLowerCase(Locale.US);
        String cleanHref = clean(href, 700);
        String cleanText = clean(text, 160);
        if (cleanHost.isEmpty() || cleanSelector.isEmpty()) {
            throw new IllegalArgumentException("Некорректное правило");
        }
        if (isDangerousSelector(cleanSelector, cleanTag, cleanHref, cleanHost)) {
            throw new IllegalArgumentException("Нельзя блокировать плеер или служебный слой");
        }
        List<Rule> rules = all();
        for (Rule rule : rules) {
            if (rule.host.equals(cleanHost) && rule.selector.equals(cleanSelector)
                    && rule.href.equals(cleanHref) && rule.text.equals(cleanText)) return rule;
        }
        Rule rule = new Rule(UUID.randomUUID().toString(), cleanHost, cleanSelector,
                cleanTag, cleanHref, cleanText, System.currentTimeMillis());
        rules.add(rule);
        while (rules.size() > MAX_RULES) rules.remove(0);
        save(rules);
        return rule;
    }

    synchronized Rule undoLast() {
        List<Rule> rules = all();
        if (rules.isEmpty()) return null;
        int newest = 0;
        for (int i = 1; i < rules.size(); i++) if (rules.get(i).createdAt > rules.get(newest).createdAt) newest = i;
        Rule removed = rules.remove(newest);
        save(rules);
        return removed;
    }

    synchronized boolean delete(String id) {
        if (id == null) return false;
        List<Rule> rules = all();
        boolean removed = false;
        for (int i = rules.size() - 1; i >= 0; i--) {
            if (id.equals(rules.get(i).id)) { rules.remove(i); removed = true; }
        }
        if (removed) save(rules);
        return removed;
    }

    synchronized int count() { return all().size(); }

    synchronized List<Rule> forHost(String host) {
        String h = normalizeHost(host);
        List<Rule> out = new ArrayList<>();
        for (Rule rule : all()) if (rule.host.equals(h)) out.add(rule);
        return out;
    }

    synchronized List<Rule> all() {
        List<Rule> out = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY_RULES, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) continue;
                String id = o.optString("id", "");
                String host = normalizeHost(o.optString("host", ""));
                String selector = o.optString("selector", "");
                if (id.isEmpty() || host.isEmpty() || selector.isEmpty()) continue;
                out.add(new Rule(id, host, selector,
                        o.optString("tag", ""),
                        o.optString("href", ""),
                        o.optString("text", ""),
                        o.optLong("createdAt", 0L)));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private void save(List<Rule> rules) {
        JSONArray array = new JSONArray();
        for (Rule rule : rules) {
            JSONObject o = new JSONObject();
            try {
                o.put("id", rule.id);
                o.put("host", rule.host);
                o.put("selector", rule.selector);
                o.put("tag", rule.tag);
                o.put("href", rule.href);
                o.put("text", rule.text);
                o.put("createdAt", rule.createdAt);
                array.put(o);
            } catch (Throwable ignored) {
            }
        }
        prefs.edit().putString(KEY_RULES, array.toString()).apply();
    }

    private static String clean(String value, int max) {
        if (value == null) return "";
        String out = value.trim();
        return out.length() <= max ? out : out.substring(0, max);
    }

    private static String normalizeHost(String host) {
        if (host == null) return "";
        String h = host.trim().toLowerCase(Locale.US);
        if (h.startsWith("www.")) h = h.substring(4);
        return h;
    }
}
