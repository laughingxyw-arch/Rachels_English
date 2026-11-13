/** Real browser WebAuthn smoke test against SQLite and the production handlers. No production data. */
import {createServer} from 'node:http';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFileSync} from 'node:fs';
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
  if(req.url.startsWith('/passkey-probe/')){res.setHeader('Content-Type','text/html');res.end(readFileSync(new URL('../../site/dist/passkey-probe/index.html',import.meta.url)));return}
  if(req.url==='/passkey-enroll.js'){res.setHeader('Content-Type','text/javascript');res.end(readFileSync(new URL('../../site/dist/passkey-enroll.js',import.meta.url)));return}
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
 await page.goto(origin+'/passkey-probe/#'+ticket);
 // Cancel before saving: no enrollment is consumed and retry remains available.
 await page.evaluate(()=>{const original=navigator.credentials.create.bind(navigator.credentials);let first=true;window.createdKeys=0;navigator.credentials.create=async options=>{if(first){first=false;throw new DOMException('Cancelled','NotAllowedError');}window.createdKeys++;return original(options);};});
 await page.locator('#save').click();
 await page.waitForFunction(()=>document.getElementById('status').textContent.includes('已取消'));
 assert.equal((await db.prepare('SELECT used FROM passkey_enrollments WHERE hash=?').bind(await sha(ticket)).first()).used,0);
 // Lost connection at finish: retry the same assertion, never create another key.
 let failFinish=true;
 await page.route('**/api/passkey/register/finish',async route=>{if(failFinish){failFinish=false;await route.abort();}else await route.continue();});
 await page.locator('#save').click();
 await page.waitForFunction(()=>document.getElementById('save').textContent==='重试确认绑定');
 assert.equal(await page.evaluate(()=>window.createdKeys),1);
 await page.locator('#save').click();
 await page.waitForFunction(()=>document.getElementById('save').textContent==='已保存');
 assert.match(await page.locator('#status').textContent(),/通行密钥已关联/);
 assert.equal(await page.evaluate(()=>location.hash),'');
 assert.equal(await page.evaluate(()=>sessionStorage.getItem('pending-/passkey-probe/')),null);
 assert.equal((await db.prepare('SELECT used FROM passkey_enrollments WHERE hash=?').bind(await sha(ticket)).first()).used,1);
 assert.equal((await db.prepare('SELECT COUNT(*) AS n FROM passkeys WHERE account=?').bind(account).first()).n,1);
 assert.equal(await page.evaluate(()=>localStorage.length),0);
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
