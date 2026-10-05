const headers = {'Content-Type':'application/json', 'Cache-Control':'no-store'};
const reply=(value,status=200)=>new Response(JSON.stringify(value),{status,headers});
export function validate(body) {
 if(!body || !/^[a-f0-9]{32}$/.test(body.device) || !Array.isArray(body.rows) || body.rows.length>100) throw Error('Invalid data');
 const keys=new Set();
 for(const r of body.rows){
  if(!/^\d{4}-\d{2}-\d{2}$/.test(r.day)||new Date(r.day+'T00:00:00Z').toISOString().slice(0,10)!==r.day || !/^[\w-]{11}$/.test(r.course))throw Error('Invalid row');
  for(const k of ['audioMs','shadowMs'])if(!Number.isSafeInteger(r[k])||r[k]<0||r[k]>86400000)throw Error('Invalid duration');
  const key=r.day+'|'+r.course;if(keys.has(key))throw Error('Duplicate row');keys.add(key);
 }
 return body;
}
export async function onRequest({request,env}) {
 const token=request.headers.get('Authorization')?.match(/^Bearer ([a-f0-9]{64})$/)?.[1];
 if(!token)return reply({error:'Unauthorized'},401);
 const digest=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(token));
 const account=Array.from(new Uint8Array(digest),b=>b.toString(16).padStart(2,'0')).join('');
 try {
  if(request.method==='PUT'){
   if(Number(request.headers.get('Content-Length')||0)>65536)return reply({error:'Too large'},413);
   const raw=await request.text();if(raw.length>65536)return reply({error:'Too large'},413);
   let body;try{body=validate(JSON.parse(raw))}catch{return reply({error:'Invalid data'},400)}
   const sql='INSERT INTO study(account,device,day,course,audio,shadow) VALUES(?,?,?,?,?,?) ON CONFLICT(account,device,day,course) DO UPDATE SET audio=MAX(audio,excluded.audio),shadow=MAX(shadow,excluded.shadow)';
   await env.STUDY_DB.batch([
    env.STUDY_DB.prepare('INSERT OR IGNORE INTO accounts(id,created) VALUES(?,?)').bind(account,Date.now()),
    ...body.rows.map(r=>env.STUDY_DB.prepare(sql).bind(account,body.device,r.day,r.course,r.audioMs,r.shadowMs))
   ]);
   return reply({ok:true});
  }
  if(request.method!=='GET')return reply({error:'Method not allowed'},405);
  const exists=await env.STUDY_DB.prepare('SELECT id FROM accounts WHERE id=?').bind(account).first();
  if(!exists)return reply({error:'Not found'},404);
  // Bound each response; clients fetch older pages when records grow.
  const url=new URL(request.url);const offset=Number(url.searchParams.get('offset')||0);
  if(!Number.isSafeInteger(offset)||offset<0)return reply({error:'Invalid offset'},400);
  const result=await env.STUDY_DB.prepare('SELECT device,day,course,audio AS audioMs,shadow AS shadowMs FROM study WHERE account=? ORDER BY device,day,course LIMIT 1000 OFFSET ?').bind(account,offset).all();
  return reply({rows:result.results,next:result.results.length===1000?offset+1000:null});
 }catch{return reply({error:'Sync unavailable'},503)}
}
