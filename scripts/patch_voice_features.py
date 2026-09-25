from pathlib import Path

p = Path('buildsrc/app/src/main/assets/index.html')
s = p.read_text(encoding='utf-8')

css = r'''
.voiceActions{display:flex;flex-wrap:wrap;gap:7px;margin-top:8px}
.voiceActionBtn{flex:1 1 auto;min-width:86px;background:#1a2330;color:var(--text);border:1px solid rgba(255,255,255,.09);border-radius:10px;padding:8px 9px;font-size:12px;font-weight:700}
.voiceActionBtn.primary{background:#1c5f82;border-color:#2f83ad}
.voiceActionBtn.active{background:#5a4813;border-color:#b99722}
.voiceActionBtn.danger{background:#402126;border-color:#7b3944}
.voiceSavedHint{margin-top:6px;color:var(--muted);font-size:11px;line-height:1.35}
'''
if '.voiceActions{' not in s:
    s = s.replace('</style>', css + '\n</style>', 1)

tr_old = '''    <div class="voiceSelectBox">\n      <label for="trVoiceSelect">Türkçe ses kişisi</label>\n      <select id="trVoiceSelect"></select>\n    </div>'''
tr_new = tr_old + '''\n    <div class="voiceActions">\n      <button class="voiceActionBtn" id="trPreviewVoice" type="button">▶ Sesi Dene</button>\n      <button class="voiceActionBtn primary" id="trApplyVoice" type="button">✓ Uygula</button>\n      <button class="voiceActionBtn" id="trFavVoice" type="button">☆ Favori</button>\n      <button class="voiceActionBtn danger" id="trHideVoice" type="button">🚫 Gizle</button>\n      <button class="voiceActionBtn" id="trShowHidden" type="button">Gizlenenleri göster</button>\n    </div>\n    <div class="voiceSavedHint" id="trVoiceSavedHint"></div>'''
if 'id="trPreviewVoice"' not in s:
    s = s.replace(tr_old, tr_new, 1)

en_old = '''    <div class="voiceSelectBox">\n      <label for="enVoiceSelect">İngilizce ses kişisi / aksanı</label>\n      <select id="enVoiceSelect"></select>\n    </div>'''
en_new = en_old + '''\n    <div class="voiceActions">\n      <button class="voiceActionBtn" id="enPreviewVoice" type="button">▶ Sesi Dene</button>\n      <button class="voiceActionBtn primary" id="enApplyVoice" type="button">✓ Uygula</button>\n      <button class="voiceActionBtn" id="enFavVoice" type="button">☆ Favori</button>\n      <button class="voiceActionBtn danger" id="enHideVoice" type="button">🚫 Gizle</button>\n      <button class="voiceActionBtn" id="enShowHidden" type="button">Gizlenenleri göster</button>\n    </div>\n    <div class="voiceSavedHint" id="enVoiceSavedHint"></div>'''
if 'id="enPreviewVoice"' not in s:
    s = s.replace(en_old, en_new, 1)

js = r'''

// ---- Native voice preview / favourites / hidden voices ----
const voiceUiState={tr:{showHidden:false},en:{showHidden:false}};
function voiceStoreSet(key){
  try{return new Set(JSON.parse(localStorage.getItem(key)||'[]'));}catch(e){return new Set();}
}
function saveVoiceStoreSet(key,set){ localStorage.setItem(key,JSON.stringify([...set])); }
function currentEngineKey(){
  try{return (ttsEngineSelect&&ttsEngineSelect.value)||AndroidApp.getSavedTtsEngine()||'default';}catch(e){return 'browser';}
}
function storedVoiceKey(lang,name){ return currentEngineKey()+'|'+lang+'|'+name; }
function currentSavedVoice(lang){
  try{if(window.AndroidApp) return AndroidApp.getSavedTtsVoice(lang)||'';}catch(e){}
  return localStorage.getItem(lang+'VoiceName')||'';
}
function setCurrentSavedVoice(lang,name){
  localStorage.setItem(lang+'VoiceName',name||'');
  try{if(window.AndroidApp) AndroidApp.setTtsVoice(lang,name||'');}catch(e){}
}
function updateVoiceButtons(lang){
  const sel=lang==='tr'?trVoiceSelect:enVoiceSelect;
  const name=sel.value||'';
  const fav=voiceStoreSet('ttsVoiceFavorites');
  const hidden=voiceStoreSet('ttsVoiceHidden');
  const key=name?storedVoiceKey(lang,name):'';
  const favBtn=$(lang+'FavVoice'), hideBtn=$(lang+'HideVoice'), hint=$(lang+'VoiceSavedHint'), showBtn=$(lang+'ShowHidden');
  if(favBtn){favBtn.disabled=!name; favBtn.textContent=name&&fav.has(key)?'★ Favoriden çıkar':'☆ Favori'; favBtn.classList.toggle('active',!!name&&fav.has(key));}
  if(hideBtn){hideBtn.disabled=!name; hideBtn.textContent=name&&hidden.has(key)?'↩ Geri getir':'🚫 Gizle';}
  if(showBtn) showBtn.textContent=voiceUiState[lang].showHidden?'Gizlenenleri kapat':'Gizlenenleri göster';
  const saved=currentSavedVoice(lang);
  if(hint){
    const savedOpt=[...sel.options].find(o=>o.value===saved);
    hint.textContent='Aktif ses: '+(saved?(savedOpt?savedOpt.textContent:saved):'Otomatik — en kaliteli ses');
  }
}
function refillNativeVoiceSelect(selectEl,lang,list,savedName,pendingName){
  const fav=voiceStoreSet('ttsVoiceFavorites');
  const hidden=voiceStoreSet('ttsVoiceHidden');
  const showHidden=voiceUiState[lang].showHidden;
  const sorted=[...list].sort((a,b)=>{
    const af=fav.has(storedVoiceKey(lang,a.name))?1:0, bf=fav.has(storedVoiceKey(lang,b.name))?1:0;
    if(af!==bf) return bf-af;
    return Number(b.score||0)-Number(a.score||0);
  });
  selectEl.innerHTML='';
  const auto=document.createElement('option');
  auto.value='';
  auto.textContent='Otomatik — en doğal/yüksek kaliteli sesi seç';
  selectEl.appendChild(auto);
  sorted.forEach(v=>{
    const key=storedVoiceKey(lang,v.name), isHidden=hidden.has(key);
    if(isHidden&&!showHidden) return;
    const opt=document.createElement('option');
    opt.value=v.name;
    opt.textContent=(fav.has(key)?'⭐ ':'')+(isHidden?'🚫 ':'')+nativeVoiceLabel(v);
    selectEl.appendChild(opt);
  });
  const hasPending=pendingName&&[...selectEl.options].some(o=>o.value===pendingName);
  const hasSaved=savedName&&[...selectEl.options].some(o=>o.value===savedName);
  selectEl.value=hasPending?pendingName:(hasSaved?savedName:'');
  updateVoiceButtons(lang);
}
const __originalLoadAndroidTtsCatalog=loadAndroidTtsCatalog;
loadAndroidTtsCatalog=function(){
  if(!window.AndroidApp) return __originalLoadAndroidTtsCatalog();
  try{
    const engines=JSON.parse(AndroidApp.getTtsEngines()||'[]');
    const voicesNative=JSON.parse(AndroidApp.getTtsVoices()||'[]');
    window.__nativeVoiceCatalog=voicesNative;
    $('ttsEngineBox').style.display='block';
    const savedEngine=AndroidApp.getSavedTtsEngine()||'';
    const pendingEngine=ttsEngineSelect.value||savedEngine;
    ttsEngineSelect.innerHTML='';
    engines.forEach(e=>{
      const opt=document.createElement('option');
      opt.value=e.name||'';
      let label=e.label||e.name||'Sistem TTS';
      if((e.name||'').toLowerCase().includes('google')) label+=' • önerilen';
      if(e.default) label+=' • sistem varsayılanı';
      opt.textContent=label;
      ttsEngineSelect.appendChild(opt);
    });
    if(pendingEngine&&engines.some(e=>e.name===pendingEngine)) ttsEngineSelect.value=pendingEngine;
    else if(engines.length) ttsEngineSelect.value=engines[0].name;

    const trNative=voicesNative.filter(v=>(v.lang||'').toLowerCase().startsWith('tr'));
    const enNative=voicesNative.filter(v=>(v.lang||'').toLowerCase().startsWith('en'));
    refillNativeVoiceSelect(trVoiceSelect,'tr',trNative,AndroidApp.getSavedTtsVoice('tr')||'',trVoiceSelect.value||'');
    refillNativeVoiceSelect(enVoiceSelect,'en',enNative,AndroidApp.getSavedTtsVoice('en')||'',enVoiceSelect.value||'');
    $('voiceInfo').textContent='Bir sesi seç, “Sesi Dene” ile dinle; beğenirsen “Uygula”. ⭐ Favoriler üstte, 🚫 gizlenenler normal listede görünmez.';
    return true;
  }catch(e){ return false; }
};
function previewSelectedVoice(lang){
  const sel=lang==='tr'?trVoiceSelect:enVoiceSelect;
  const rate=lang==='tr'?Number(trRate.value):Number(enRate.value);
  const sample=lang==='tr'
    ?'Merhaba. Bu kısa örnekte seçtiğiniz sesin ne kadar doğal ve anlaşılır olduğunu dinleyebilirsiniz.'
    :'Hello. This short preview lets you hear how natural and clear the selected voice sounds.';
  const pending=sel.value||'';
  if(window.AndroidApp){
    const saved=currentSavedVoice(lang);
    try{
      AndroidApp.setTtsVoice(lang,pending);
      AndroidApp.speakOnce(sample,lang,rate);
    }finally{
      AndroidApp.setTtsVoice(lang,saved);
    }
  }else{
    speechSynthesis.cancel();
    const u=new SpeechSynthesisUtterance(sample);
    u.lang=lang==='tr'?'tr-TR':'en-GB';
    u.rate=rate;
    const v=voices.find(x=>x.name===pending);
    if(v)u.voice=v;
    speechSynthesis.speak(u);
  }
  $('voiceInfo').textContent='Önizleme: '+(sel.options[sel.selectedIndex]?.textContent||'Otomatik ses');
}
function applySelectedVoice(lang){
  const sel=lang==='tr'?trVoiceSelect:enVoiceSelect;
  setCurrentSavedVoice(lang,sel.value||'');
  updateVoiceButtons(lang);
  $('voiceInfo').textContent=(lang==='tr'?'Türkçe':'İngilizce')+' aktif ses kaydedildi: '+(sel.options[sel.selectedIndex]?.textContent||'Otomatik');
}
function toggleFavoriteVoice(lang){
  const sel=lang==='tr'?trVoiceSelect:enVoiceSelect;
  const name=sel.value||'';
  if(!name)return;
  const set=voiceStoreSet('ttsVoiceFavorites'), key=storedVoiceKey(lang,name);
  set.has(key)?set.delete(key):set.add(key);
  saveVoiceStoreSet('ttsVoiceFavorites',set);
  loadAndroidTtsCatalog();
}
function toggleHiddenVoice(lang){
  const sel=lang==='tr'?trVoiceSelect:enVoiceSelect;
  const name=sel.value||'';
  if(!name)return;
  const set=voiceStoreSet('ttsVoiceHidden'), key=storedVoiceKey(lang,name), was=set.has(key);
  was?set.delete(key):set.add(key);
  saveVoiceStoreSet('ttsVoiceHidden',set);
  if(!was && currentSavedVoice(lang)===name) setCurrentSavedVoice(lang,'');
  loadAndroidTtsCatalog();
}
function toggleShowHidden(lang){
  voiceUiState[lang].showHidden=!voiceUiState[lang].showHidden;
  loadAndroidTtsCatalog();
}
trVoiceSelect.onchange=()=>{
  updateVoiceButtons('tr');
  $('voiceInfo').textContent='Türkçe sesi seçildi. Önce “Sesi Dene”, sonra beğenirsen “Uygula”.';
};
enVoiceSelect.onchange=()=>{
  updateVoiceButtons('en');
  $('voiceInfo').textContent='İngilizce sesi seçildi. Önce “Sesi Dene”, sonra beğenirsen “Uygula”.';
};
$('trPreviewVoice').onclick=()=>previewSelectedVoice('tr');
$('enPreviewVoice').onclick=()=>previewSelectedVoice('en');
$('trApplyVoice').onclick=()=>applySelectedVoice('tr');
$('enApplyVoice').onclick=()=>applySelectedVoice('en');
$('trFavVoice').onclick=()=>toggleFavoriteVoice('tr');
$('enFavVoice').onclick=()=>toggleFavoriteVoice('en');
$('trHideVoice').onclick=()=>toggleHiddenVoice('tr');
$('enHideVoice').onclick=()=>toggleHiddenVoice('en');
$('trShowHidden').onclick=()=>toggleShowHidden('tr');
$('enShowHidden').onclick=()=>toggleShowHidden('en');
setTimeout(()=>{updateVoiceButtons('tr');updateVoiceButtons('en');},300);
'''

if 'ttsVoiceFavorites' not in s:
    s = s.replace('</script>', js + '\n</script>', 1)

p.write_text(s, encoding='utf-8')

assert 'id="trPreviewVoice"' in s
assert 'id="enPreviewVoice"' in s
assert 'ttsVoiceFavorites' in s
assert 'ttsVoiceHidden' in s
assert 'previewSelectedVoice' in s
assert 'applySelectedVoice' in s
print('Voice preview/favorite/hidden patch verified')
