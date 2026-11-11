export class ApiError extends Error {constructor(status,message){super(message);this.status=status}}
export const hex=bytes=>Array.from(bytes,b=>b.toString(16).padStart(2,'0')).join('');
export const randomHex=()=>hex(crypto.getRandomValues(new Uint8Array(32)));
export const sha=async text=>hex(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(text))));
export const tokenFrom=request=>request.headers.get('Authorization')?.match(/^Bearer ((?:session\.)?[a-f0-9]{64})$/)?.[1]??null;
/** Expired session tokens must never become new legacy accounts. */
export async function studyIdentity(db,token){
 if(!token)throw new ApiError(401,'Unauthorized');
 const hash=await sha(token);
 if(token.startsWith('session.')){
  const row=await db.prepare('SELECT account FROM study_sessions WHERE hash=? AND expires>?').bind(hash,Date.now()).first();
  if(!row)throw new ApiError(401,'Session expired');return row.account;
 }
 const alias=await db.prepare('SELECT target FROM study_aliases WHERE source=?').bind(hash).first();
 return alias?.target||hash;
}
export async function existingIdentity(db,request){
 const account=await studyIdentity(db,tokenFrom(request));
 if(!await db.prepare('SELECT id FROM accounts WHERE id=?').bind(account).first())throw new ApiError(401,'Unauthorized');
 return account;
}
export async function readBody(request){
 if(Number(request.headers.get('Content-Length')||0)>65536)throw new ApiError(413,'Too large');
 if(!request.body)return {};
 const reader=request.body.getReader();let size=0;const chunks=[];
 try {while(true){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>65536){await reader.cancel();throw new ApiError(413,'Too large')}chunks.push(value)}}finally{reader.releaseLock()}
 const bytes=new Uint8Array(size);let at=0;for(const part of chunks){bytes.set(part,at);at+=part.length}
 try{return JSON.parse(new TextDecoder().decode(bytes))}catch{throw new ApiError(400,'Invalid data')}
}
