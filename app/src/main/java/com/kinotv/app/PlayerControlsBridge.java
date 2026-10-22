package com.kinotv.app;

import android.util.Log;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Адаптер нативных селекторов к DOM-меню Kinobox.
 *
 * В отличие от video-локатора меню сезонов/серий/озвучек находится в документе
 * обёртки и доступно WebView. Адаптер не использует координаты и не вмешивается
 * в воспроизведение. Если структура чужого плеера перестала распознаваться,
 * нативные селекторы просто скрываются.
 */
final class PlayerControlsBridge {
    static final String KIND_TRANSLATION = "translation";
    static final String KIND_SEASON = "season";
    static final String KIND_EPISODE = "episode";

    interface StateCallback {
        void onResult(State state);
    }

    interface SelectCallback {
        void onResult(boolean sent, String reason);
    }

    static final class Menu {
        final String kind;
        final List<String> options;
        final int selectedIndex;
        final String selectedLabel;

        Menu(String kind, List<String> options, int selectedIndex, String selectedLabel) {
            this.kind = kind == null ? "" : kind;
            this.options = Collections.unmodifiableList(new ArrayList<>(options));
            this.selectedIndex = selectedIndex;
            this.selectedLabel = selectedLabel == null ? "" : selectedLabel;
        }

        boolean available() {
            return !options.isEmpty();
        }
    }

    static final class State {
        final boolean supported;
        final String provider;
        final Menu translation;
        final Menu season;
        final Menu episode;
        final String error;

        State(boolean supported, String provider, Menu translation, Menu season,
              Menu episode, String error) {
            this.supported = supported;
            this.provider = provider == null ? "" : provider;
            this.translation = translation;
            this.season = season;
            this.episode = episode;
            this.error = error == null ? "" : error;
        }

        Menu menu(String kind) {
            if (KIND_TRANSLATION.equals(kind)) return translation;
            if (KIND_SEASON.equals(kind)) return season;
            if (KIND_EPISODE.equals(kind)) return episode;
            return emptyMenu(kind);
        }

        String signature() {
            return supported + "|" + provider + "|" + menuSignature(translation)
                    + "|" + menuSignature(season) + "|" + menuSignature(episode);
        }
    }

    private PlayerControlsBridge() {
    }

    static void install(WebView web) {
        if (web == null) return;
        try {
            web.evaluateJavascript(INSTALL_SCRIPT, null);
        } catch (Throwable error) {
            Log.w("SidiControls", "bridge install failed", error);
        }
    }

    static void read(WebView web, StateCallback callback) {
        if (web == null) {
            if (callback != null) callback.onResult(emptyState("web_missing"));
            return;
        }
        try {
            web.evaluateJavascript(READ_SCRIPT, value -> {
                State state = parseState(value);
                if (callback != null) callback.onResult(state);
            });
        } catch (Throwable error) {
            Log.w("SidiControls", "bridge read failed", error);
            if (callback != null) callback.onResult(emptyState(error.getClass().getSimpleName()));
        }
    }

    static void select(WebView web, String kind, int index, String label,
                       SelectCallback callback) {
        if (web == null) {
            if (callback != null) callback.onResult(false, "web_missing");
            return;
        }
        String safeKind = JSONObject.quote(kind == null ? "" : kind);
        String safeLabel = JSONObject.quote(label == null ? "" : label);
        String script = "(function(){try{" + BRIDGE_PRELUDE
                + "return JSON.stringify(window.__sidiControlsSelect(" + safeKind + ","
                + index + "," + safeLabel + "));"
                + "}catch(e){return JSON.stringify({sent:false,reason:'exception:'+String(e)});}})();";
        try {
            web.evaluateJavascript(script, value -> {
                JSONObject result = parseObject(value);
                boolean sent = result.optBoolean("sent", false);
                String reason = result.optString("reason", sent ? "ok" : "unknown");
                Log.i("SidiControls", "select kind=" + kind + " index=" + index
                        + " label=" + label + " sent=" + sent + " reason=" + reason);
                if (callback != null) callback.onResult(sent, reason);
            });
        } catch (Throwable error) {
            Log.w("SidiControls", "bridge select failed", error);
            if (callback != null) callback.onResult(false, error.getClass().getSimpleName());
        }
    }

    private static State parseState(String value) {
        JSONObject root = parseObject(value);
        if (!root.optBoolean("supported", false)) {
            return emptyState(root.optString("error", "unsupported"));
        }
        return new State(true, root.optString("provider", "kinobox"),
                parseMenu(KIND_TRANSLATION, root.optJSONObject(KIND_TRANSLATION)),
                parseMenu(KIND_SEASON, root.optJSONObject(KIND_SEASON)),
                parseMenu(KIND_EPISODE, root.optJSONObject(KIND_EPISODE)),
                root.optString("error", ""));
    }

    private static Menu parseMenu(String kind, JSONObject object) {
        if (object == null) return emptyMenu(kind);
        List<String> options = new ArrayList<>();
        JSONArray values = object.optJSONArray("options");
        if (values != null) {
            for (int i = 0; i < values.length(); i++) {
                String value = values.optString(i, "").trim();
                if (!value.isEmpty() && !containsIgnoreCase(options, value)) options.add(value);
            }
        }
        int selectedIndex = object.optInt("selectedIndex", -1);
        String selectedLabel = object.optString("selectedLabel", "").trim();
        if (selectedIndex < 0 && !selectedLabel.isEmpty()) {
            for (int i = 0; i < options.size(); i++) {
                if (options.get(i).equalsIgnoreCase(selectedLabel)) {
                    selectedIndex = i;
                    break;
                }
            }
        }
        if (selectedLabel.isEmpty() && selectedIndex >= 0 && selectedIndex < options.size()) {
            selectedLabel = options.get(selectedIndex);
        }
        return new Menu(kind, options, selectedIndex, selectedLabel);
    }

    private static boolean containsIgnoreCase(List<String> values, String target) {
        for (String value : values) if (value.equalsIgnoreCase(target)) return true;
        return false;
    }

    private static State emptyState(String error) {
        return new State(false, "", emptyMenu(KIND_TRANSLATION), emptyMenu(KIND_SEASON),
                emptyMenu(KIND_EPISODE), error);
    }

    private static Menu emptyMenu(String kind) {
        return new Menu(kind, Collections.emptyList(), -1, "");
    }

    private static JSONObject parseObject(String value) {
        try {
            Object decoded = new JSONTokener(value == null ? "" : value).nextValue();
            String json = decoded instanceof String ? (String) decoded : String.valueOf(value);
            return new JSONObject(json);
        } catch (Throwable ignored) {
            return new JSONObject();
        }
    }

    private static String menuSignature(Menu menu) {
        StringBuilder values = new StringBuilder();
        for (String option : menu.options) {
            if (values.length() > 0) values.append('\u001f');
            values.append(option);
        }
        return menu.kind + ":" + menu.selectedIndex + ":" + menu.selectedLabel + ":" + values;
    }

    private static final String BRIDGE_PRELUDE = """
            function __sidiCtlText(e){try{return String((e&&((e.innerText||e.textContent)))||'').trim().replace(/\\s+/g,' ');}catch(x){return '';}}
            function __sidiCtlNorm(s){return String(s||'').trim().replace(/\\s+/g,' ').toLowerCase();}
            function __sidiCtlNumber(s){var m=String(s||'').match(/\\d{1,4}/);return m?parseInt(m[0],10):0;}
            function __sidiCtlUnique(nodes){var out=[],seen={};for(var i=0;i<nodes.length;i++){var e=nodes[i],t=__sidiCtlText(e),n=__sidiCtlNorm(t);if(!n||t.length>100||seen[n])continue;seen[n]=1;out.push({el:e,label:t});}return out;}
            function __sidiCtlItems(body){var preferred=body.querySelectorAll('.kinobox__menuItem');var nodes=preferred.length?preferred:body.querySelectorAll('[role=option],li,button,a,[class*=menuItem]');return __sidiCtlUnique(nodes);}
            function __sidiCtlTrigger(body,index,triggers){var root=null;try{root=body.parentElement&&body.parentElement.closest?body.parentElement.closest('.kinobox__menu,[class*=kinobox][class*=menu]'):body.parentElement;}catch(x){}if(!root)root=body.parentElement;var candidates=[];if(root){var q=root.querySelectorAll('.kinobox__menuHeader,.kinobox__menuButton,.kinobox__menuCurrent,[aria-haspopup=listbox],[aria-haspopup=menu],button,[role=button]');for(var i=0;i<q.length;i++)if(!body.contains(q[i]))candidates.push(q[i]);if(!candidates.length&&root.children){for(var j=0;j<root.children.length;j++)if(root.children[j]!==body)candidates.push(root.children[j]);}}if(candidates.length)return candidates[0];return triggers[index]||null;}
            function __sidiCtlKind(label,items,index,numericOrder){var l=__sidiCtlNorm(label);if(/сезон|season/.test(l))return 'season';if(/серия|episode|эпизод/.test(l))return 'episode';if(/озвуч|перевод|translation|voice|dub/.test(l))return 'translation';var numeric=0;for(var i=0;i<items.length;i++)if(__sidiCtlNumber(items[i].label)>0)numeric++;if(items.length&&numeric/items.length>=0.75){return numericOrder===0?'season':'episode';}return 'translation';}
            function __sidiCtlSelected(items,triggerText){var selected=-1;for(var i=0;i<items.length;i++){var e=items[i].el,c=((e.id||'')+' '+(typeof e.className==='string'?e.className:''));if(e.getAttribute('aria-selected')==='true'||/(^|[ _-])(active|selected|current|checked)([ _-]|$)/i.test(c)){selected=i;break;}}if(selected<0){var tn=__sidiCtlNorm(triggerText),num=__sidiCtlNumber(triggerText);for(var j=0;j<items.length;j++){var n=__sidiCtlNorm(items[j].label);if(n===tn||(num>0&&__sidiCtlNumber(items[j].label)===num)){selected=j;break;}}}return selected;}
            function __sidiCtlScanRaw(){var bodyNodes=document.querySelectorAll('.kinobox__menuBody,[class*=kinobox][class*=menuBody]');var triggers=document.querySelectorAll('.kinobox__menuHeader,.kinobox__menuButton,.kinobox__menuCurrent,[aria-haspopup=listbox]');var groups=[],numericOrder=0;for(var i=0;i<bodyNodes.length;i++){var body=bodyNodes[i],items=__sidiCtlItems(body);if(!items.length)continue;var trigger=__sidiCtlTrigger(body,i,triggers),triggerText=__sidiCtlText(trigger);var kind=__sidiCtlKind(triggerText,items,i,numericOrder);var numericCount=0;for(var z=0;z<items.length;z++)if(__sidiCtlNumber(items[z].label)>0)numericCount++;if(items.length&&numericCount/items.length>=0.75)numericOrder++;groups.push({kind:kind,body:body,trigger:trigger,triggerText:triggerText,items:items,selected:__sidiCtlSelected(items,triggerText)});}var best={};for(var g=0;g<groups.length;g++){var group=groups[g],old=best[group.kind];if(!old||group.items.length>old.items.length)best[group.kind]=group;}return best;}
            function __sidiCtlPublicMenu(group){if(!group)return {options:[],selectedIndex:-1,selectedLabel:''};var values=[];for(var i=0;i<group.items.length;i++)values.push(group.items[i].label);var selected=group.selected;return {options:values,selectedIndex:selected,selectedLabel:selected>=0&&selected<values.length?values[selected]:(group.triggerText||'')};}
            function __sidiCtlBuildState(){try{var raw=__sidiCtlScanRaw(),supported=!!(raw.translation||raw.season||raw.episode);return {supported:supported,provider:supported?'kinobox':'',translation:__sidiCtlPublicMenu(raw.translation),season:__sidiCtlPublicMenu(raw.season),episode:__sidiCtlPublicMenu(raw.episode),error:supported?'':'menu_not_found'};}catch(e){return {supported:false,provider:'',translation:{options:[],selectedIndex:-1,selectedLabel:''},season:{options:[],selectedIndex:-1,selectedLabel:''},episode:{options:[],selectedIndex:-1,selectedLabel:''},error:String(e)};}}
            function __sidiCtlClick(kind,index,label){try{var raw=__sidiCtlScanRaw(),group=raw[kind];if(!group)return {sent:false,reason:'menu_not_found'};var wanted=__sidiCtlNorm(label),targetIndex=-1;for(var i=0;i<group.items.length;i++)if(__sidiCtlNorm(group.items[i].label)===wanted){targetIndex=i;break;}if(targetIndex<0&&index>=0&&index<group.items.length)targetIndex=index;if(targetIndex<0)return {sent:false,reason:'option_not_found'};var run=function(){var latest=__sidiCtlScanRaw()[kind],item=latest&&latest.items[targetIndex]?latest.items[targetIndex].el:null;if(!item&&latest){for(var j=0;j<latest.items.length;j++)if(__sidiCtlNorm(latest.items[j].label)===wanted){item=latest.items[j].el;break;}}if(!item)return;try{item.scrollIntoView({block:'nearest'});}catch(x){}try{item.click();}catch(x){try{item.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true,view:window}));}catch(y){}}setTimeout(__sidiCtlPublish,120);setTimeout(__sidiCtlPublish,500);};var target=group.items[targetIndex].el;var hidden=false;try{hidden=!target.offsetParent||getComputedStyle(target).display==='none'||getComputedStyle(target).visibility==='hidden';}catch(x){}if(hidden&&group.trigger){try{group.trigger.click();}catch(x){}setTimeout(run,80);}else run();return {sent:true,reason:'ok'};}catch(e){return {sent:false,reason:'exception:'+String(e)};}}
            function __sidiCtlPublish(){try{window.__sidiControlsState=__sidiCtlBuildState();return window.__sidiControlsState;}catch(e){return {supported:false,error:String(e)};}}
            window.__sidiControlsRead=__sidiCtlPublish;
            window.__sidiControlsSelect=__sidiCtlClick;
            """;

    private static final String INSTALL_SCRIPT = "(function(){try{"
            + "if(window.__sidiControlsInstalled){if(window.__sidiControlsRead)window.__sidiControlsRead();return 'already';}"
            + BRIDGE_PRELUDE
            + "window.__sidiControlsInstalled=true;"
            + "var pending=false;function schedule(){if(pending)return;pending=true;setTimeout(function(){pending=false;__sidiCtlPublish();},120);}"
            + "var root=document.documentElement||document.body;if(root)new MutationObserver(schedule).observe(root,{childList:true,subtree:true,attributes:true,attributeFilter:['class','aria-selected','style']});"
            + "__sidiCtlPublish();setInterval(__sidiCtlPublish,2500);return 'installed';"
            + "}catch(e){return 'error:'+String(e);}})();";

    private static final String READ_SCRIPT = "(function(){try{"
            + BRIDGE_PRELUDE
            + "return JSON.stringify(window.__sidiControlsRead());"
            + "}catch(e){return JSON.stringify({supported:false,error:String(e)});}})();";
}
