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
 assert.equal(location.hash,"#"+ticket);assert.equal(history.length,0);button.onclick();
 assert.equal(location.href,`intent://rachels-english.pages.dev/${path}/#${ticket}#Intent;scheme=https;package=com.rachelsenglish.${app};S.enrollment=${ticket};end`);
 const invalid={};runInNewContext(script,{location:{hash:'#invalid',pathname:`/${path}/`},history:{replaceState(){}},document:{getElementById:id=>id==='open'?invalid:{}}});assert.equal(invalid.disabled,true);assert.notEqual(invalid.hidden,true);
});

test('refreshing a setup tab can resume its private pending ticket without sending it to the server',()=>{
 const html=readFileSync(new URL('../../site/dist/passkey-setup/index.html',import.meta.url),'utf8'),script=html.match(/<script>([\s\S]*?)<\/script>/)[1];
 const items=new Map(),sessionStorage={setItem:(key,value)=>items.set(key,value),getItem:key=>items.get(key)};
 const ticket='b'.repeat(64);let button={};
 runInNewContext(script,{location:{hash:'#'+ticket,pathname:'/passkey-setup/'},sessionStorage,document:{getElementById:id=>id==='open'?button:{}}});
 const location={hash:'',pathname:'/passkey-setup/',href:null};button={};
 runInNewContext(script,{location,sessionStorage,document:{getElementById:id=>id==='open'?button:{}}});button.onclick();assert.ok(location.href.includes('#'+ticket+'#Intent;'));
 items.set('pending-/passkey-setup/',JSON.stringify({ticket,expires:0}));button={};
 runInNewContext(script,{location:{hash:'',pathname:'/passkey-setup/'},sessionStorage,document:{getElementById:id=>id==='open'?button:{}}});assert.equal(button.disabled,true);
});
