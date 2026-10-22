package com.kinotv.app;

/**
 * Read-only state bridge for same-origin HTML5 video/metadata.
 *
 * 12.4 intentionally contains no synthetic KeyEvent/touch injection and no focus
 * manipulation. Real media keys and mouse events are delivered directly by Android
 * and Chromium; cross-origin player iframes stay isolated.
 */
final class PlayerMediaController {
    private PlayerMediaController() {
    }

    static String buildStateScript() {
        return "(function(){try{" + locatorPrelude() +
                "var o={season:0,episode:0,translation:'',position:0,duration:0,videoFound:false,videoSource:''},metaSeen=[];" +
                "function meta(root){try{if(!root||metaSeen.indexOf(root)>=0)return;metaSeen.push(root);var nodes=root.querySelectorAll?root.querySelectorAll('[aria-selected=\\\"true\\\"],[class*=\\\"active\\\"],[class*=\\\"selected\\\"],[class*=\\\"current\\\"]'):[];" +
                "for(var i=0;i<nodes.length&&i<120;i++){var e=nodes[i],t=(e.innerText||e.textContent||'').trim().replace(/\\s+/g,' ');if(!t||t.length>100)continue;" +
                "var s=t.match(/(?:сезон|season)\\s*(\\d{1,3})/i)||t.match(/(\\d{1,3})\\s*(?:сезон|season)/i);if(s)o.season=parseInt(s[1]);" +
                "var p=t.match(/(?:серия|episode|эпизод)\\s*(\\d{1,4})/i)||t.match(/(\\d{1,4})\\s*(?:серия|episode|эпизод)/i);if(p)o.episode=parseInt(p[1]);" +
                "var cls=((e.id||'')+' '+(typeof e.className==='string'?e.className:''));if(/translation|translate|voice|dub|озвуч|перевод/i.test(cls)&&t.length<60)o.translation=t;}" +
                "var all=root.querySelectorAll?root.querySelectorAll('*'):[];for(var j=0;j<all.length;j++){try{if(all[j].shadowRoot)meta(all[j].shadowRoot);}catch(x){}}" +
                "var fs=root.querySelectorAll?root.querySelectorAll('iframe'):[];for(var k=0;k<fs.length;k++){try{if(fs[k].contentDocument)meta(fs[k].contentDocument);}catch(x){}}" +
                "}catch(e){}}" +
                "meta(document);var best=__sidiLocate();if(best){o.videoFound=true;o.videoSource=__sidiSource(best);o.position=Math.floor(best.currentTime||0);o.duration=Math.floor(best.duration||0);}" +
                "return JSON.stringify(o);" +
                "}catch(e){return JSON.stringify({season:0,episode:0,translation:'',position:0,duration:0,videoFound:false,videoSource:'',error:String(e)});}})();";
    }

    static String recentCommands() {
        return "12.5: synthetic media command injection disabled; hardware events pass directly to Chromium";
    }

    private static String locatorPrelude() {
        return "var __sidiSeen=[];" +
                "function __sidiVisible(v){try{var r=v.getBoundingClientRect(),st=getComputedStyle(v);return r.width>120&&r.height>80&&st.display!=='none'&&st.visibility!=='hidden'&&parseFloat(st.opacity||'1')>0.05;}catch(e){return false;}}" +
                "function __sidiAdLike(v){try{var n=v,p='';for(var i=0;i<6&&n;i++,n=n.parentElement){p+=' '+(n.id||'')+' '+(typeof n.className==='string'?n.className:'');}var src=(v.currentSrc||v.src||'');return /(vast|vpaid|preroll|midroll|advert|\\bad[sx]?\\b|banner|promo)/i.test(p+' '+src);}catch(e){return false;}}" +
                "function __sidiScore(v){try{var r=v.getBoundingClientRect(),n=r.width*r.height;if(__sidiAdLike(v))n-=1000000000;if(!v.paused)n+=100000000;if(v.readyState>=2)n+=1000000;if(v.currentTime>0)n+=500000;if(v.duration>60)n+=250000;if(v.muted)n-=10000;return n;}catch(e){return -1;}}" +
                "function __sidiCollect(root,out){try{if(!root||__sidiSeen.indexOf(root)>=0)return;__sidiSeen.push(root);var vs=root.querySelectorAll?root.querySelectorAll('video'):[];for(var i=0;i<vs.length;i++)if(__sidiVisible(vs[i]))out.push(vs[i]);var all=root.querySelectorAll?root.querySelectorAll('*'):[];for(var j=0;j<all.length;j++){try{if(all[j].shadowRoot)__sidiCollect(all[j].shadowRoot,out);}catch(e){}}var fs=root.querySelectorAll?root.querySelectorAll('iframe'):[];for(var k=0;k<fs.length;k++){try{if(fs[k].contentDocument)__sidiCollect(fs[k].contentDocument,out);}catch(e){}}}catch(e){}}" +
                "function __sidiLocate(){var c=[];__sidiCollect(document,c);var best=null,bestScore=-1;for(var i=0;i<c.length;i++){var sc=__sidiScore(c[i]);if(sc>bestScore){best=c[i];bestScore=sc;}}return best;}" +
                "function __sidiSource(v){try{var root=v.getRootNode?v.getRootNode():document;if(root&&root.host)return 'shadow-dom';var w=v.ownerDocument&&v.ownerDocument.defaultView;return w&&w.frameElement?'same-origin-iframe':'document';}catch(e){return 'unknown';}}";
    }
}
