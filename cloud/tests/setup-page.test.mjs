import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
const scriptFor=path=>readFileSync(new URL(`../../site/dist/${path}/index.html`,import.meta.url),'utf8').match(/<script>([\s\S]*?)<\/script>/)[1];
function render(path,hash,sessionStorage){
 const events={},timers=new Map(),status={},link={disabled:true,removeAttribute(name){if(name==='aria-disabled')this.disabled=false;},addEventListener(name,fn){events[name]=fn;}};
 const document={visibilityState:'visible',getElementById:id=>id==='open'?link:status,addEventListener:(name,fn)=>events[name]=fn};
 const location={hash,pathname:`/${path}/`};
 runInNewContext(scriptFor(path),{location,sessionStorage,document,window:{addEventListener:(name,fn)=>events[name]=fn},setTimeout:fn=>{const id=timers.size+1;timers.set(id,fn);return id;},clearTimeout:id=>timers.delete(id)});
 return {link,status,document,events,timers,location};
}
for(const [path,app] of [['passkey-setup','practice'],['passkey-probe','passkeyprobe']])test(`${path} exposes a direct browser intent anchor without ambiguous URL fragments`,()=>{
 const ticket='a'.repeat(64),r=render(path,'#'+ticket);
 assert.equal(r.location.hash,'#'+ticket);
 assert.equal(r.link.disabled,false);
 assert.equal(r.link.href,`intent://rachels-english.pages.dev/${path}/#Intent;scheme=https;package=com.rachelsenglish.${app};action=android.intent.action.VIEW;category=android.intent.category.BROWSABLE;S.enrollment=${ticket};end`);
 assert.equal(r.link.href.split('#').length,2);
 const invalid=render(path,'#invalid');assert.equal(invalid.link.href,undefined);assert.equal(invalid.link.disabled,true);assert.match(invalid.status.textContent,/未读取到绑定信息/);
});
test('refreshing a setup tab resumes its pending ticket without sending it to the server',()=>{
 const items=new Map(),sessionStorage={setItem:(key,value)=>items.set(key,value),getItem:key=>items.get(key)},ticket='b'.repeat(64);
 render('passkey-setup','#'+ticket,sessionStorage);
 assert.ok(render('passkey-setup','',sessionStorage).link.href.includes('S.enrollment='+ticket));
 items.set('pending-/passkey-setup/',JSON.stringify({ticket,expires:0}));assert.equal(render('passkey-setup','',sessionStorage).link.href,undefined);
});
test('a blocked launch offers help, while leaving the page cancels the delayed message',()=>{
 const r=render('passkey-setup','#'+'c'.repeat(64));r.events.click();assert.equal(r.timers.size,1);
 [...r.timers.values()][0]();assert.match(r.status.textContent,/小米浏览器/);
 const launched=render('passkey-setup','#'+'c'.repeat(64));launched.events.click();launched.document.visibilityState='hidden';launched.events.visibilitychange();assert.equal(launched.timers.size,0);
 const left=render('passkey-setup','#'+'c'.repeat(64));left.events.click();left.events.pagehide();assert.equal(left.timers.size,0);
});
