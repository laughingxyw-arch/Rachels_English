import test from 'node:test';
import assert from 'node:assert/strict';
import {database} from './fixture-db.mjs';
import {authenticator} from './passkey-authenticator.mjs';
import {handlePasskey} from '../lib/passkeys.js';
import {sha} from '../lib/study-auth.js';
import {onRequest as study} from '../functions/api/study.js';
const account='1'.repeat(64),ticket='2'.repeat(64);
async function setup(mode='owner'){
 const db=database();await db.prepare('INSERT INTO accounts VALUES(?,?)').bind(account,Date.now()).run();
 await db.prepare('INSERT INTO passkey_profiles VALUES(?,?)').bind(account,mode).run();
 await db.prepare('INSERT INTO passkey_enrollments(hash,account,expires) VALUES(?,?,?)').bind(await sha(ticket),account,Date.now()+600000).run();
 await db.prepare('INSERT INTO study VALUES(?,?,?,?,?,?)').bind(account,'a'.repeat(32),'2026-11-01','HIMYM010402',120000,15000).run();
 return {db,env:{STUDY_DB:db}};
}
async function call(env,path,body={},auth){
 const r=await handlePasskey({env,request:new Request('https://rachels-english.pages.dev/api/passkey/'+path,{method:'POST',headers:{'Content-Type':'application/json',...(auth?{Authorization:'Bearer '+auth}:{})},body:JSON.stringify(body)})});
 return {status:r.status,...await r.json()};
}
async function register(env,opts={}){const a=await authenticator();const c=await call(env,'register/options',{ticket});assert.equal(c.status,200);const credential=await a.register(c.options,opts);return {a,c,credential,result:await call(env,'register/finish',{ticket,challengeId:c.challengeId,credential})}}
test('only private setup can bind old records, with a discoverable verified credential',async()=>{
 const {env,db}=await setup();assert.equal((await call(env,'register/options')).status,401);assert.equal((await call(env,'register/options',{ticket:'0'.repeat(64)})).status,401);
 const {a,c,result}=await register(env);assert.equal(c.options.authenticatorSelection.residentKey,'required');assert.equal(c.options.authenticatorSelection.userVerification,'required');assert.equal(result.status,200);assert.equal(result.account,account);assert.ok(result.token.startsWith('session.'));
 const r=await study({env,request:new Request('https://example.com/api/study',{headers:{Authorization:'Bearer '+result.token}})});assert.equal(r.status,200);assert.equal((await r.json()).rows[0].audioMs,120000);
 assert.equal((await call(env,'register/options',{ticket})).status,401);assert.equal((await db.prepare('SELECT COUNT(*) AS n FROM passkeys').first()).n,1);
 const challenge=await call(env,'authenticate/options');assert.equal(challenge.options.allowCredentials[0].id,a.id);
 const login=await call(env,'authenticate/finish',{challengeId:challenge.challengeId,credential:await a.login(challenge.options)});assert.equal(login.status,200);assert.equal(login.account,account);
 assert.equal((await call(env,'authenticate/finish',{challengeId:challenge.challengeId,credential:await a.login(challenge.options)})).status,401);
});
test('registration rejects wrong origin, RP and missing fingerprint/PIN verification without consuming setup',async()=>{
 for(const opts of [{origin:'https://attacker.example'},{rpID:'attacker.example'},{uv:false}]){const {env,db}=await setup();const {result}=await register(env,opts);assert.equal(result.status,401);assert.equal((await db.prepare('SELECT used FROM passkey_enrollments').first()).used,0)}
});
test('authentication rejects invalid signatures, missing verification, unknown keys, expired challenges and replays',async()=>{
 const {env,db}=await setup();const {a}=await register(env);
 for(const opts of [{origin:'https://attacker.example'},{rpID:'attacker.example'},{uv:false}]){const c=await call(env,'authenticate/options');assert.equal((await call(env,'authenticate/finish',{challengeId:c.challengeId,credential:await a.login(c.options,opts)})).status,401)}
 let c=await call(env,'authenticate/options');let cred=await a.login(c.options);cred.response.signature=cred.response.signature.slice(0,-2)+'AA';assert.equal((await call(env,'authenticate/finish',{challengeId:c.challengeId,credential:cred})).status,401);
 c=await call(env,'authenticate/options');const unknown=await authenticator();assert.equal((await call(env,'authenticate/finish',{challengeId:c.challengeId,credential:await unknown.login(c.options)})).status,401);
 c=await call(env,'authenticate/options');await db.prepare('UPDATE passkey_challenges SET expires=0 WHERE id=?').bind(c.challengeId).run();assert.equal((await call(env,'authenticate/finish',{challengeId:c.challengeId,credential:await a.login(c.options)})).status,401);
});
test('probe keys cannot restore the personal profile; expired sessions never create a new identity',async()=>{
 const {env,db}=await setup('probe');const {result}=await register(env);assert.equal(result.status,200);assert.equal((await call(env,'authenticate/options')).status,404);assert.equal((await call(env,'authenticate/options',{mode:'probe'})).status,200);
 await db.prepare('UPDATE study_sessions SET expires=0').run();
 for(const method of ['GET','PUT']){const r=await study({env,request:new Request('https://example.com/api/study',{method,headers:{Authorization:'Bearer '+result.token},...(method==='PUT'?{body:JSON.stringify({device:'b'.repeat(32),rows:[]})}:{})})});assert.equal(r.status,401)}
 assert.equal((await db.prepare('SELECT COUNT(*) AS n FROM accounts').first()).n,1);
});
test('concurrent setup completion grants one credential and cannot steal the enrollment',async()=>{
 const {env,db}=await setup();const c1=await call(env,'register/options',{ticket}),c2=await call(env,'register/options',{ticket});const a1=await authenticator(),a2=await authenticator();
 const responses=await Promise.all([[c1,a1],[c2,a2]].map(async([c,a])=>call(env,'register/finish',{ticket,challengeId:c.challengeId,credential:await a.register(c.options)})));
 assert.deepEqual(responses.map(r=>r.status).sort(),[200,401]);assert.equal((await db.prepare('SELECT COUNT(*) AS n FROM passkeys').first()).n,1);
});
test('restored sessions retain old counters and idempotently merge a new installation',async()=>{
 const {env,db}=await setup();const {a}=await register(env);
 const c=await call(env,'authenticate/options'),login=await call(env,'authenticate/finish',{challengeId:c.challengeId,credential:await a.login(c.options)});
 const send=async device=>study({env,request:new Request('https://example.com/api/study',{method:'PUT',headers:{Authorization:'Bearer '+login.token},body:JSON.stringify({device,rows:[{day:'2026-11-01',course:'HIMYM010402',audioMs:60000,shadowMs:0}]})})});
 assert.equal((await send('b'.repeat(32))).status,200);assert.equal((await send('b'.repeat(32))).status,200);assert.equal((await send('a'.repeat(32))).status,200);
 const total=await db.prepare('SELECT SUM(audio) AS n FROM study WHERE account=?').bind(account).first();assert.equal(total.n,180000);
 assert.equal((await call(env,'register/options',{},login.token)).status,200);
});
test('expired private links and oversized bodies do not allocate challenges',async()=>{
 const {env,db}=await setup();await db.prepare('UPDATE passkey_enrollments SET expires=0').run();
 assert.equal((await call(env,'register/options',{ticket})).status,401);
 assert.equal((await call(env,'authenticate/options',{padding:'x'.repeat(66000)})).status,413);
 assert.equal((await db.prepare('SELECT COUNT(*) AS n FROM passkey_challenges').first()).n,0);
});
test('only provisioned legacy aliases route old uploads to their recovered owner',async()=>{
 const {env,db}=await setup();const legacy='e'.repeat(64),source=await sha(legacy);
 await db.prepare('INSERT INTO accounts VALUES(?,?)').bind(source,Date.now()).run();await db.prepare('INSERT INTO study_aliases VALUES(?,?)').bind(source,account).run();
 const r=await study({env,request:new Request('https://example.com/api/study',{headers:{Authorization:'Bearer '+legacy}})});assert.equal(r.status,200);assert.equal((await r.json()).rows[0].audioMs,120000);
 const unknown=await study({env,request:new Request('https://example.com/api/study',{headers:{Authorization:'Bearer '+'f'.repeat(64)}})});assert.equal(unknown.status,404);
});
test('a private probe setup link cannot accidentally bind the production application',async()=>{
 const {env,db}=await setup('probe');assert.equal((await call(env,'register/options',{ticket,mode:'owner'})).status,401);assert.equal((await call(env,'register/options',{ticket,mode:'probe'})).status,200);assert.equal((await db.prepare('SELECT used FROM passkey_enrollments').first()).used,0);
});
test('an authorized existing install can enroll without a link; a fresh install cannot claim its history',async()=>{
 const {env,db}=await setup();const legacy='e'.repeat(64),source=await sha(legacy);
 await db.prepare('INSERT INTO accounts VALUES(?,?)').bind(source,Date.now()).run();await db.prepare('INSERT INTO study_aliases VALUES(?,?)').bind(source,account).run();
 assert.equal((await call(env,'register/options',{mode:'owner'},'f'.repeat(64))).status,401);
 const c=await call(env,'register/options',{mode:'owner'},legacy);assert.equal(c.status,200);
 const a=await authenticator(),r=await call(env,'register/finish',{mode:'owner',challengeId:c.challengeId,credential:await a.register(c.options)},legacy);assert.equal(r.status,200);assert.equal(r.account,account);
 const fresh=await call(env,'authenticate/options');assert.equal((await call(env,'authenticate/finish',{challengeId:fresh.challengeId,credential:await a.login(fresh.options)})).account,account);
});
