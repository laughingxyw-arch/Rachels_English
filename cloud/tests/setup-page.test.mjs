import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
for(const [path,app] of [['passkey-setup','practice'],['passkey-probe','passkeyprobe']])test(`${path} sends both a legacy URL fragment and the enrollment extra`,()=>{
 const html=readFileSync(new URL(`../../site/dist/${path}/index.html`,import.meta.url),'utf8');
 const script=html.match(/<script>([\s\S]*?)<\/script>/)[1];
 const ticket='a'.repeat(64),button={},status={},history=[];
 const location={hash:'#'+ticket,pathname:`/${path}/`,href:null};
 runInNewContext(script,{location,history:{replaceState:(...args)=>history.push(args)},document:{getElementById:id=>id==='open'?button:status}});
 assert.equal(history[0][2],`/${path}/`);button.onclick();
 assert.equal(location.href,`intent://rachels-english.pages.dev/${path}/#${ticket}#Intent;scheme=https;package=com.rachelsenglish.${app};S.enrollment=${ticket};end`);
 const invalid={};runInNewContext(script,{location:{hash:'#invalid',pathname:`/${path}/`},history:{replaceState(){}},document:{getElementById:id=>id==='open'?invalid:{}}});assert.equal(invalid.hidden,true);
});
