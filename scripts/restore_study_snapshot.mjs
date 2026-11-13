import {readFileSync,writeFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
const [input,output]=process.argv.slice(2);if(!input||!output)throw Error('Usage: node scripts/restore_study_snapshot.mjs private-snapshot.json private-output.sql');
const record=JSON.parse(readFileSync(input)),payload=typeof record.payload==='string'?record.payload:JSON.stringify(record),s=JSON.parse(payload);
if(record.sha256&&createHash('sha256').update(payload).digest('hex')!==record.sha256)throw Error('Snapshot checksum mismatch');
if(s.schemaVersion!==1||!Number.isFinite(Date.parse(s.exportedAt))||!Array.isArray(s.rows))throw Error('Invalid snapshot');
const seen=new Set();for(const r of s.rows){
 if(!/^[a-f0-9]{32}$/.test(r.device)||!/^\d{4}-\d{2}-\d{2}$/.test(r.day)||new Date(r.day+'T00:00:00Z').toISOString().slice(0,10)!==r.day||!(/^[\w-]{11}$/).test(r.course)||!['audioMs','shadowMs'].every(k=>Number.isSafeInteger(r[k])&&r[k]>=0&&r[k]<=86400000))throw Error('Invalid snapshot row');
 const k=[r.device,r.day,r.course].join('|');if(seen.has(k))throw Error('Duplicate snapshot row');seen.add(k);
}
const data=JSON.stringify(s.rows).replaceAll("'","''");
writeFileSync(output,"DELETE FROM study WHERE account=(SELECT account FROM single_study_profile WHERE id=1);\nINSERT INTO study(account,device,day,course,audio,shadow) SELECT p.account,json_extract(j.value,'$.device'),json_extract(j.value,'$.day'),json_extract(j.value,'$.course'),json_extract(j.value,'$.audioMs'),json_extract(j.value,'$.shadowMs') FROM single_study_profile p,json_each('"+data+"') j WHERE p.id=1;\n",{mode:0o600});
console.log('Validated restore SQL prepared; take a fresh database backup before applying.');
