import sys
from pathlib import Path
from playwright.sync_api import sync_playwright

main = Path(sys.argv[1])
src = main.read_text().splitlines()
line = next((x.strip() for x in src if '"function visualAt(x,y,raw)' in x), None)
if not line or not line.startswith('"') or not line.endswith('" +'):
    raise SystemExit('visualAt source line not found')
js = line[1:-3]
js = bytes(js, 'utf-8').decode('unicode_escape')
html = '''<!doctype html><html><head><style>
html,body{margin:0;width:960px;height:540px;overflow:hidden}
#main{position:absolute;left:100px;top:90px;width:700px;height:390px;background:#111}
#controls{position:absolute;left:100px;top:430px;width:700px;height:50px;background:#333;z-index:20}
#mystery{position:absolute;left:160px;top:130px;width:520px;height:250px;background:rgba(255,0,0,.5);z-index:9999;pointer-events:none}
</style></head><body><video id="main"></video><div id="controls"></div><div id="mystery" class="mystery-layer"></div></body></html>'''
setup = """var box=null;
function known(e){return null;}
function externalLink(e){return null;}
function protectedNode(e){if(!e)return true;var t=(e.tagName||'').toLowerCase();return t==='html'||t==='body'||t==='video'||t==='audio';}
function overlayRoot(e){return e;}
function choose(e){return protectedNode(e)?null:e;}
""" + js
with sync_playwright() as pw:
    browser = pw.chromium.launch(headless=True, executable_path='/usr/bin/chromium', args=['--no-sandbox','--disable-dev-shm-usage'])
    page = browser.new_page(viewport={'width': 960, 'height': 540})
    page.set_content(html)
    page.evaluate(setup)
    raw = page.evaluate("document.elementFromPoint(300,220).id")
    picked = page.evaluate("(function(){var raw=document.elementFromPoint(300,220);var e=visualAt(300,220,raw);return e?e.id:'NONE';})()")
    browser.close()
if raw != 'main':
    raise SystemExit(f'fixture invalid: raw hit should be main video, got {raw}')
if picked != 'mystery':
    raise SystemExit(f'visual picker failed: expected mystery, got {picked}')
print('visual picker fixture OK: raw click hits video, visual hit-test selects pointer-events:none overlay')
