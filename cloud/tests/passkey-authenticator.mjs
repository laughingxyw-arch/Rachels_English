import {isoCBOR} from '@simplewebauthn/server/helpers';
const b64=v=>Buffer.from(v).toString('base64url');
const digest=async v=>new Uint8Array(await crypto.subtle.digest('SHA-256',v));
const utf=text=>new TextEncoder().encode(text);
const join=(...parts)=>Buffer.concat(parts.map(p=>Buffer.from(p)));
function der(signature){
 const bytes=new Uint8Array(signature);const int=v=>{let p=0;while(p<v.length-1&&!v[p])p++;let r=v.slice(p);if(r[0]&128)r=join([0],r);return join([2,r.length],r)};
 const body=join(int(bytes.slice(0,32)),int(bytes.slice(32)));return join([48,body.length],body);
}
export async function authenticator(){
 const key=await crypto.subtle.generateKey({name:'ECDSA',namedCurve:'P-256'},true,['sign','verify']);
 const pub=await crypto.subtle.exportKey('jwk',key.publicKey),id=crypto.getRandomValues(new Uint8Array(32));
 const cose=isoCBOR.encode(new Map([[1,2],[3,-7],[-1,1],[-2,new Uint8Array(Buffer.from(pub.x,'base64url'))],[-3,new Uint8Array(Buffer.from(pub.y,'base64url'))]]));
 return {id:b64(id),async register(options,{origin='https://rachels-english.pages.dev',uv=true,rpID=options.rp.id}={}){
  const client=utf(JSON.stringify({type:'webauthn.create',challenge:options.challenge,origin,crossOrigin:false}));
  const auth=join(await digest(utf(rpID)),[1|(uv?4:0)|64],[0,0,0,0],new Uint8Array(16),[0,id.length],id,cose);
  const att=isoCBOR.encode(new Map([['fmt','none'],['attStmt',new Map()],['authData',auth]]));
  return {id:b64(id),rawId:b64(id),type:'public-key',clientExtensionResults:{},authenticatorAttachment:'platform',response:{clientDataJSON:b64(client),attestationObject:b64(att),transports:['internal']}};
 },async login(options,{origin='https://rachels-english.pages.dev',uv=true,rpID=options.rpId,counter=1}={}){
  const client=utf(JSON.stringify({type:'webauthn.get',challenge:options.challenge,origin,crossOrigin:false}));
  const count=new Uint8Array(4);new DataView(count.buffer).setUint32(0,counter);
  const auth=join(await digest(utf(rpID)),[1|(uv?4:0)],count);
  const signature=await crypto.subtle.sign({name:'ECDSA',hash:'SHA-256'},key.privateKey,join(auth,await digest(client)));
  return {id:b64(id),rawId:b64(id),type:'public-key',clientExtensionResults:{},response:{clientDataJSON:b64(client),authenticatorData:b64(auth),signature:b64(der(signature)),userHandle:null}};
 }};
}
