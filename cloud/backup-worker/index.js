export const checksum=async text=>Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(text))),b=>b.toString(16).padStart(2,'0')).join('');
export async function backupStudy(env,now=Date.now()){
 const profile=await env.STUDY_DB.prepare('SELECT account FROM single_study_profile WHERE id=1').first();
 if(!profile)throw Error('Study profile unavailable');
 const rows=(await env.STUDY_DB.prepare('SELECT device,day,course,audio AS audioMs,shadow AS shadowMs FROM study WHERE account=? ORDER BY device,day,course').bind(profile.account).all()).results;
 const payload=JSON.stringify({schemaVersion:1,exportedAt:new Date(now).toISOString(),rows}),day=new Date(now+8*3600000).toISOString().slice(0,10);
 // Preserve the first snapshot of the day even if a later request changes the live counters.
 await env.STUDY_DB.prepare('INSERT OR IGNORE INTO study_backups(day,created,payload,sha256) VALUES(?,?,?,?)').bind(day,now,payload,await checksum(payload)).run();
 await env.STUDY_DB.prepare('DELETE FROM study_backups WHERE created<?').bind(now-90*86400000).run();
}
export default {
 async scheduled(event,env){await backupStudy(env,event.scheduledTime);},
 fetch(){return new Response('Not found',{status:404});}
};
