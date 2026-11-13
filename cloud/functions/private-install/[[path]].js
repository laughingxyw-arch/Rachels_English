import {sha} from '../../lib/study-auth.js';
const headers={'Cache-Control':'no-store','Referrer-Policy':'no-referrer','X-Robots-Tag':'noindex, nofollow','X-Content-Type-Options':'nosniff'};
export async function onRequest({request,env}){
 const match=new URL(request.url).pathname.match(/^\/private-install\/([a-f0-9]{64})\/rachels-english\.apk$/);
 if(!match||!['GET','HEAD'].includes(request.method))return new Response('Not found',{status:404,headers});
 try{
  const now=Date.now();
  const active=await env.STUDY_DB.prepare('SELECT 1 AS ok FROM private_installs i JOIN passkey_enrollments e ON e.hash=i.enrollment WHERE i.hash=? AND i.expires>? AND e.expires>? AND e.used=0').bind(await sha(match[1]),now,now).first();
  if(!active)return new Response('Download unavailable',{status:410,headers});
  const asset=await env.ASSETS.fetch(request);
  const response=new Response(asset.body,{status:asset.status,headers:asset.headers});
  for(const [key,value] of Object.entries(headers))response.headers.set(key,value);
  return response;
 }catch{return new Response('Download unavailable',{status:503,headers});}
}
