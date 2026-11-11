/** Real browser WebAuthn smoke test against SQLite and the production handlers. No production data. */
import {createServer} from 'node:http';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {database} from './fixture-db.mjs';
import {handlePasskey} from '../lib/passkeys.js';
import {onRequest} from '../functions/api/study.js';
import {sha} from '../lib/study-auth.js';
const require=createRequire(import.meta.url);
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||'playwright');
const db=database(),ticket='7'.repeat(64),account='8'.repeat(64);
await db.prepare('INSERT INTO accounts VALUES(?,?)').bind(account,Date.now()).run();
await db.prepare('INSERT INTO passkey_profiles VALUES(?,?)').bind(account,'probe').run();
await db.prepare('INSERT INTO passkey_enrollments(hash,account,expires) VALUES(?,?,?)').bind(await sha(ticket),account,Date.now()+300000).run();
await db.prepare('INSERT INTO study VALUES(?,?,?,?,?,?)').bind(account,'a'.repeat(32),'2026-11-11','PASSKEY0001',60000,0).run();
let origin;
const server=createServer(async(req,res)=>{
 try {
  if(req.url==='/'){res.setHeader('Content-Type','text/html');res.end('<!doctype html><title>Local passkey verification</title>');return}
  const parts=[];for await(const part of req)parts.push(part);
  const request=new Request(origin+req.url,{method:req.method,headers:req.headers,...(req.method==='POST'?{body:Buffer.concat(parts)}:{})});
  const env={STUDY_DB:db,PASSKEY_RP_ID:'localhost',PASSKEY_ALLOWED_ORIGINS:JSON.stringify([origin])};
  const r=await (req.url.startsWith('/api/passkey/')?handlePasskey:onRequest)({env,request});res.writeHead(r.status,Object.fromEntries(r.headers));res.end(await r.text());
 }catch{res.writeHead(500);res.end('{}')}
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));origin='http://localhost:'+server.address().port;
const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||undefined,headless:true,args:['--no-sandbox']});
try {
 const page=await browser.newPage();const cdp=await page.context().newCDPSession(page);await cdp.send('WebAuthn.enable');
 await cdp.send('WebAuthn.addVirtualAuthenticator',{options:{protocol:'ctap2',transport:'internal',hasResidentKey:true,hasUserVerification:true,isUserVerified:true,automaticPresenceSimulation:true}});
 await page.goto(origin);
 const registered=await page.evaluate(async({ticket})=>{
  const post=async(path,body)=>{const r=await fetch('/api/passkey/'+path,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});if(!r.ok)throw Error('Registration HTTP '+r.status);return r.json()};
  const decode=s=>Uint8Array.from(atob(s.replaceAll('-','+').replaceAll('_','/')),c=>c.charCodeAt(0));
  const c=await post('register/options',{ticket}),o=c.options;o.challenge=decode(o.challenge);o.user.id=decode(o.user.id);o.excludeCredentials=o.excludeCredentials.map(k=>({...k,id:decode(k.id)}));
  const credential=await navigator.credentials.create({publicKey:o});return post('register/finish',{ticket,challengeId:c.challengeId,credential:credential.toJSON()});
 },{ticket});assert.equal(registered.account,account);
 // Fresh page, no saved token or app storage; only the system authenticator retains its key.
 await page.goto(origin);
 const restored=await page.evaluate(async()=>{
  const post=async(path,body)=>{const r=await fetch('/api/passkey/'+path,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});if(!r.ok)throw Error('Restore HTTP '+r.status);return r.json()};
  const decode=s=>Uint8Array.from(atob(s.replaceAll('-','+').replaceAll('_','/')),c=>c.charCodeAt(0));
  const c=await post('authenticate/options',{mode:'probe'}),o=c.options;o.challenge=decode(o.challenge);o.allowCredentials=o.allowCredentials.map(k=>({...k,id:decode(k.id)}));
  const credential=await navigator.credentials.get({publicKey:o}),login=await post('authenticate/finish',{challengeId:c.challengeId,credential:credential.toJSON()});
  const r=await fetch('/api/study',{headers:{Authorization:'Bearer '+login.token}});return {account:login.account,rows:(await r.json()).rows};
 });assert.equal(restored.account,account);assert.equal(restored.rows[0].audioMs,60000);
 console.log('Browser WebAuthn registration and fresh-client restoration passed; Xiaomi provider NOT tested.');
} finally {await browser.close();server.close();db.sql.close()}
