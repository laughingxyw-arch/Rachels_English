import test from 'node:test';
import assert from 'node:assert/strict';
import {database} from './fixture-db.mjs';
import {sha} from '../lib/study-auth.js';
import {onRequest} from '../functions/private-install/[[path]].js';
async function fixture(){
 const db=database(),account='1'.repeat(64),ticket='2'.repeat(64),capability='3'.repeat(64),now=Date.now();
 await db.prepare('INSERT INTO accounts VALUES(?,?)').bind(account,now).run();
 await db.prepare('INSERT INTO passkey_profiles VALUES(?,?)').bind(account,'probe').run();
 await db.prepare('INSERT INTO passkey_enrollments(hash,account,expires) VALUES(?,?,?)').bind(await sha(ticket),account,now+60000).run();
 await db.prepare('INSERT INTO private_installs VALUES(?,?,?)').bind(await sha(capability),await sha(ticket),now+60000).run();
 let reads=0;const env={STUDY_DB:db,ASSETS:{fetch:async()=>{reads++;return new Response('isolated APK fixture',{headers:{'Cache-Control':'public, max-age=86400'}})}}};
 const get=(cap=capability,method='GET')=>onRequest({env,request:new Request('https://example.com/private-install/'+cap+'/rachels-english.apk',{method})});
 return {db,get,ticket,capability,reads:()=>reads};
}
test('private setup package requires the exact random capability and cannot be cached',async()=>{
 const f=await fixture();try{const r=await f.get();assert.equal(r.status,200);assert.equal(r.headers.get('Cache-Control'),'no-store');assert.equal(r.headers.get('Referrer-Policy'),'no-referrer');assert.match(r.headers.get('X-Robots-Tag'),/noindex/);assert.equal(await r.text(),'isolated APK fixture');assert.equal((await f.get('4'.repeat(64))).status,410);assert.equal((await f.get('invalid')).status,404);assert.equal((await f.get(f.capability,'POST')).status,404);assert.equal(f.reads(),1);}finally{f.db.sql.close();}
});
test('binding or either expiry revokes the download before any asset is read',async()=>{
 for(const action of ['used','enrollment-expired','download-expired']){
  const f=await fixture();try{
   if(action==='used')await f.db.prepare('UPDATE passkey_enrollments SET used=1').run();
   if(action==='enrollment-expired')await f.db.prepare('UPDATE passkey_enrollments SET expires=0').run();
   if(action==='download-expired')await f.db.prepare('UPDATE private_installs SET expires=0').run();
   assert.equal((await f.get()).status,410);assert.equal(f.reads(),0);
  }finally{f.db.sql.close();}
 }
});
