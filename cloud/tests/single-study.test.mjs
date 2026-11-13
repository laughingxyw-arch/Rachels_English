import test from 'node:test';
import assert from 'node:assert/strict';
import {database} from './fixture-db.mjs';
import {onRequest} from '../functions/api/study.js';
import {handlePasskey} from '../lib/passkeys.js';
async function fixture(){
 const db=database(),account='a'.repeat(64);
 await db.prepare('INSERT INTO accounts VALUES(?,?)').bind(account,Date.now()).run();
 await db.prepare('INSERT INTO single_study_profile VALUES(1,?)').bind(account).run();
 await db.prepare('INSERT INTO study VALUES(?,?,?,?,?,?)').bind(account,'b'.repeat(32),'2026-11-01','HIMYM010102',120000,0).run();
 const env={STUDY_DB:db,SINGLE_USER_STUDY:'true'};
 const get=async(headers={},query='')=>onRequest({env,request:new Request('https://example.com/api/study'+query,{headers})});
 const put=async(device,ms)=>onRequest({env,request:new Request('https://example.com/api/study',{method:'PUT',body:JSON.stringify({device,rows:[{day:'2026-11-01',course:'HIMYM010102',audioMs:ms,shadowMs:0}]})})});
 return {db,env,get,put};
}
test('fresh install and old recovery/session tokens all retrieve the original fixed profile',async()=>{
 const f=await fixture();try{
  for(const headers of [{},{Authorization:'Bearer '+'c'.repeat(64)},{Authorization:'Bearer session.'+'d'.repeat(64)}]){
   const r=await f.get(headers);assert.equal(r.status,200);assert.equal(r.headers.get('Cache-Control'),'no-store');assert.equal((await r.json()).rows[0].audioMs,120000);
  }
 }finally{f.db.sql.close();}
});
test('reinstall counters merge without duplicating retries or erasing historical rows',async()=>{
 const f=await fixture();try{
  assert.equal((await f.put('e'.repeat(32),60000)).status,200);
  await f.put('e'.repeat(32),60000);await f.put('e'.repeat(32),5000);
  const rows=(await (await f.get()).json()).rows;assert.equal(rows.length,2);assert.equal(rows.reduce((n,r)=>n+r.audioMs,0),180000);
  const exported=await (await f.get({},'?backup=1')).json();assert.equal(exported.schemaVersion,1);assert.deepEqual(exported.rows,rows);
 }finally{f.db.sql.close();}
});
test('missing fixed profile fails clearly and retired passkey setup cannot allocate new keys',async()=>{
 const f=await fixture();try{
  assert.equal((await handlePasskey({env:f.env,request:new Request('https://example.com/api/passkey/register/options',{method:'POST',body:'{}'})})).status,410);
  await f.db.prepare('DELETE FROM single_study_profile').run();assert.equal((await f.get()).status,503);
 }finally{f.db.sql.close();}
});
