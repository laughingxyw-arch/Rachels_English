import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
const source=await readFile(new URL('../functions/api/study.js',import.meta.url),'utf8');
const {validate,onRequest}=await import('data:text/javascript;base64,'+Buffer.from(source).toString('base64'));
const body={device:'a'.repeat(32),rows:[{day:'2026-10-05',course:'hLgMIwFeE88',audioMs:60000,shadowMs:0}]};
test('strict contribution validation',()=>{
 assert.equal(validate(body),body);
 for(const change of [{day:'2026-02-30'},{audioMs:-1},{audioMs:1.5},{audioMs:86400001},{course:'../private'}])assert.throws(()=>validate({...body,rows:[{...body.rows[0],...change}]}));
 assert.throws(()=>validate({...body,device:'invalid'}));
 assert.throws(()=>validate({...body,rows:Array(101).fill(body.rows[0])}));
 assert.throws(()=>validate({...body,rows:[body.rows[0],body.rows[0]]}));
});
test('unauthenticated requests cannot touch database',async()=>{
 const response=await onRequest({request:new Request('https://example.com/api/study'),env:{}});
 assert.equal(response.status,401);assert.equal(response.headers.get('Cache-Control'),'no-store');
});
test('recovery identities scope reads independently',async()=>{
 const keys=[];const env={STUDY_DB:{prepare:()=>({bind:key=>({first:async()=>{keys.push(key);return null}})})}};
 for(const token of ['a'.repeat(64),'b'.repeat(64)])assert.equal((await onRequest({request:new Request('https://example.com/api/study',{headers:{Authorization:'Bearer '+token}}),env})).status,404);
 assert.notEqual(keys[0],keys[1]);assert.ok(keys.every(x=>x.length===64));
});
