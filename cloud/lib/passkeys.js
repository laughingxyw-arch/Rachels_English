import {generateRegistrationOptions,verifyRegistrationResponse,generateAuthenticationOptions,verifyAuthenticationResponse} from '@simplewebauthn/server';
import {ApiError,randomHex,sha,existingIdentity,readBody} from './study-auth.js';
const TTL=300000,SESSION_TTL=30*86400000;
const encode=bytes=>btoa(String.fromCharCode(...bytes)).replaceAll('+','-').replaceAll('/','_').replaceAll('=','');
const decode=text=>Uint8Array.from(atob(text.replaceAll('-','+').replaceAll('_','/')),c=>c.charCodeAt(0));
const headers={'Content-Type':'application/json','Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Referrer-Policy':'no-referrer'};
const reply=(body,status=200)=>new Response(JSON.stringify(body),{status,headers});
function config(env){
 const rpID=env.PASSKEY_RP_ID||'rachels-english.pages.dev';
 const origins=JSON.parse(env.PASSKEY_ALLOWED_ORIGINS||'["https://rachels-english.pages.dev"]');
 if(!Array.isArray(origins)||!origins.length)throw new Error('Invalid origin configuration');
 return {rpID,origins};
}
async function prune(db){
 const now=Date.now();await db.batch([
  db.prepare('DELETE FROM passkey_challenges WHERE expires<?').bind(now),
  db.prepare('DELETE FROM study_sessions WHERE expires<?').bind(now)
 ]);
}
async function saveChallenge(db,options,purpose,mode,account=null,enrollment=null){
 await prune(db);
 const count=await db.prepare('SELECT COUNT(*) AS n FROM passkey_challenges WHERE consumed=0').first();
 if(count.n>=64)throw new ApiError(429,'Please try again later');
 const id=randomHex();await db.prepare('INSERT INTO passkey_challenges(id,challenge,purpose,account,mode,enrollment,expires) VALUES(?,?,?,?,?,?,?)').bind(id,options.challenge,purpose,account,mode,enrollment,Date.now()+TTL).run();
 return {challengeId:id,options};
}
async function challenge(db,id,purpose){
 if(!/^[a-f0-9]{64}$/.test(id||''))throw new ApiError(400,'Invalid challenge');
 const row=await db.prepare('SELECT * FROM passkey_challenges WHERE id=? AND purpose=? AND consumed=0 AND expires>?').bind(id,purpose,Date.now()).first();
 if(!row)throw new ApiError(401,'Challenge expired');return row;
}
async function registrationIdentity(db,request,body){
 if(body.ticket!==undefined){
  if(!/^[a-f0-9]{64}$/.test(body.ticket))throw new ApiError(401,'Setup link unavailable');
  const hash=await sha(body.ticket);
  const e=await db.prepare('SELECT account,hash FROM passkey_enrollments WHERE hash=? AND used=0 AND expires>?').bind(hash,Date.now()).first();
  if(!e)throw new ApiError(401,'Setup link unavailable');
  const p=await db.prepare('SELECT mode FROM passkey_profiles WHERE account=?').bind(e.account).first();
  if(body.mode&&body.mode!==p.mode)throw new ApiError(401,'Wrong profile');
  return {account:e.account,mode:p.mode,enrollment:hash};
 }
 const account=await existingIdentity(db,request);const p=await db.prepare('SELECT mode FROM passkey_profiles WHERE account=?').bind(account).first();
 if(!p)throw new ApiError(401,'Setup required');
 if(body.mode&&body.mode!==p.mode)throw new ApiError(401,'Wrong profile');return {account,mode:p.mode,enrollment:null};
}
function sessionData(account){const token='session.'+randomHex();return {token,account,expires:Date.now()+SESSION_TTL}}
async function registerOptions(db,request,body,cfg){
 const owner=await registrationIdentity(db,request,body);
 const keys=await db.prepare('SELECT id,transports FROM passkeys WHERE account=?').bind(owner.account).all();
 if(keys.results.length>=8)throw new ApiError(409,'Too many passkeys');
 const options=await generateRegistrationOptions({rpName:'原声练习',rpID:cfg.rpID,
  userName:owner.mode==='probe'?'通行密钥验证':'我的学习记录',userDisplayName:owner.mode==='probe'?'独立验证用记录':'我的学习记录',
  userID:Uint8Array.from(owner.account.match(/../g),v=>parseInt(v,16)),attestationType:'none',supportedAlgorithmIDs:[-7],
  excludeCredentials:keys.results.map(k=>({id:k.id,transports:JSON.parse(k.transports)})),
  authenticatorSelection:{residentKey:'required',requireResidentKey:true,userVerification:'required',authenticatorAttachment:'platform'},preferredAuthenticatorType:'localDevice',timeout:60000});
 return saveChallenge(db,options,'register',owner.mode,owner.account,owner.enrollment);
}
async function registerFinish(db,request,body,cfg){
 const ch=await challenge(db,body.challengeId,'register'),owner=await registrationIdentity(db,request,body);
 if(ch.account!==owner.account||ch.enrollment!==owner.enrollment)throw new ApiError(401,'Unauthorized');
 let verification;
 try{verification=await verifyRegistrationResponse({response:body.credential,expectedChallenge:ch.challenge,expectedOrigin:cfg.origins,expectedRPID:cfg.rpID,requireUserVerification:true,supportedAlgorithmIDs:[-7]})}catch{throw new ApiError(401,'Passkey verification failed')}
 if(!verification.verified||!verification.registrationInfo)throw new ApiError(401,'Passkey verification failed');
 const {credential}=verification.registrationInfo;const claim=randomHex(),session=sessionData(ch.account),now=Date.now();
 const active=ch.enrollment?' AND EXISTS(SELECT 1 FROM passkey_enrollments WHERE hash=? AND used=0 AND expires>?)':'';
 const args=[claim,ch.id,now,...(ch.enrollment?[ch.enrollment,now]:[])];
 const operations=[
  db.prepare('UPDATE passkey_challenges SET consumed=1,claim=? WHERE id=? AND consumed=0 AND expires>?'+active).bind(...args),
  db.prepare('INSERT INTO passkeys(id,account,public_key,counter,transports,created,last_used) SELECT ?,?,?,?,?,?,? WHERE EXISTS(SELECT 1 FROM passkey_challenges WHERE id=? AND claim=?)').bind(credential.id,ch.account,encode(credential.publicKey),credential.counter,JSON.stringify(credential.transports||[]),now,now,ch.id,claim),
  db.prepare('INSERT INTO study_sessions(hash,account,expires) SELECT ?,?,? WHERE EXISTS(SELECT 1 FROM passkey_challenges WHERE id=? AND claim=?)').bind(await sha(session.token),ch.account,session.expires,ch.id,claim)
 ];
 if(ch.enrollment)operations.push(db.prepare('UPDATE passkey_enrollments SET used=1 WHERE hash=? AND EXISTS(SELECT 1 FROM passkey_challenges WHERE id=? AND claim=?)').bind(ch.enrollment,ch.id,claim));
 const result=await db.batch(operations);if(result[0].meta.changes!==1)throw new ApiError(401,'Setup link unavailable');
 return session;
}
async function authOptions(db,body,cfg){
 const mode=body.mode==='probe'?'probe':'owner';
 const keys=await db.prepare('SELECT k.id,k.transports FROM passkeys k JOIN passkey_profiles p ON p.account=k.account WHERE p.mode=? ORDER BY k.created LIMIT 32').bind(mode).all();
 if(!keys.results.length)throw new ApiError(404,'No saved passkey');
 const options=await generateAuthenticationOptions({rpID:cfg.rpID,userVerification:'required',timeout:60000,allowCredentials:keys.results.map(k=>({id:k.id,transports:JSON.parse(k.transports)}))});
 return saveChallenge(db,options,'authenticate',mode);
}
async function authFinish(db,body,cfg){
 const ch=await challenge(db,body.challengeId,'authenticate');
 const key=await db.prepare('SELECT k.* FROM passkeys k JOIN passkey_profiles p ON p.account=k.account WHERE k.id=? AND p.mode=?').bind(body.credential?.id||'',ch.mode).first();
 if(!key)throw new ApiError(401,'Unknown passkey');
 let verification;
 try{verification=await verifyAuthenticationResponse({response:body.credential,expectedChallenge:ch.challenge,expectedOrigin:cfg.origins,expectedRPID:cfg.rpID,requireUserVerification:true,credential:{id:key.id,publicKey:decode(key.public_key),counter:key.counter,transports:JSON.parse(key.transports)}})}catch{throw new ApiError(401,'Passkey verification failed')}
 if(!verification.verified)throw new ApiError(401,'Passkey verification failed');
 const claim=randomHex(),session=sessionData(key.account),now=Date.now();
 const result=await db.batch([
  db.prepare('UPDATE passkey_challenges SET consumed=1,claim=? WHERE id=? AND consumed=0 AND expires>?').bind(claim,ch.id,now),
  db.prepare('UPDATE passkeys SET counter=MAX(counter,?),last_used=? WHERE id=? AND EXISTS(SELECT 1 FROM passkey_challenges WHERE id=? AND claim=?)').bind(verification.authenticationInfo.newCounter,now,key.id,ch.id,claim),
  db.prepare('INSERT INTO study_sessions(hash,account,expires) SELECT ?,?,? WHERE EXISTS(SELECT 1 FROM passkey_challenges WHERE id=? AND claim=?)').bind(await sha(session.token),key.account,session.expires,ch.id,claim)
 ]);
 if(result[0].meta.changes!==1)throw new ApiError(401,'Challenge expired');return session;
}
export async function handlePasskey({request,env}){
 if(env.SINGLE_USER_STUDY==='true')return reply({error:'Passkey setup retired'},410);
 if(request.method!=='POST')return reply({error:'Method not allowed'},405);
 try{
  const body=await readBody(request),db=env.STUDY_DB,cfg=config(env),path=new URL(request.url).pathname.replace(/^\/api\/passkey\//,'');
  const result=path==='register/options'?await registerOptions(db,request,body,cfg):path==='register/finish'?await registerFinish(db,request,body,cfg):path==='authenticate/options'?await authOptions(db,body,cfg):path==='authenticate/finish'?await authFinish(db,body,cfg):null;
  if(!result)return reply({error:'Not found'},404);return reply(result);
 }catch(error){return reply({error:error instanceof ApiError?error.message:'Service unavailable'},error instanceof ApiError?error.status:503)}
}
