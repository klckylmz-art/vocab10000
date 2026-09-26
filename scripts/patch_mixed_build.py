import re, sys
from pathlib import Path

root = Path(sys.argv[1])
g = root / 'app/build.gradle'
s = g.read_text(encoding='utf-8')
s = re.sub(r"applicationId\s+['\"][^'\"]+['\"]", "applicationId 'net.ytutor.sentenceapp.mixed6'", s, count=1)
s = re.sub(r'versionCode\s+\d+', 'versionCode 6', s, count=1)
s = re.sub(r"versionName\s+['\"][^'\"]+['\"]", "versionName '6.0'", s, count=1)
g.write_text(s, encoding='utf-8')

p = root / 'app/src/main/assets/index.html'
h = p.read_text(encoding='utf-8')

override = '''<script>
(function(){
  function fixedNavigate(delta){
    const wasPlaying=!!isPlaying;
    isPlaying=false;
    try{ speechSynthesis.cancel(); }catch(e){}
    const len=(order && order.length) ? order.length : ACTIVE_DATA.length;
    orderPos=(orderPos+delta+len)%len;
    syncIndex();
    render();
    if(wasPlaying){ setTimeout(()=>playCurrent(),120); }
  }
  const prevBtn=document.getElementById('prev');
  const nextBtn=document.getElementById('next');
  if(prevBtn) prevBtn.onclick=function(){ fixedNavigate(-1); };
  if(nextBtn) nextBtn.onclick=function(){ fixedNavigate(1); };
  window.__fixedMixedNavigate=fixedNavigate;
})();
</script>'''
if 'window.__fixedMixedNavigate' not in h:
    if '</body>' not in h:
        raise SystemExit('No body close tag in mixed HTML')
    h = h.replace('</body>', override + '\n</body>', 1)
p.write_text(h, encoding='utf-8')

assert "applicationId 'net.ytutor.sentenceapp.mixed6'" in s
assert 'window.__fixedMixedNavigate' in h
assert 'Konu 2' in h
print('mixed package and navigation patch verified')
