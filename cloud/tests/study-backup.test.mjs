import test from 'node:test';import assert from 'node:assert/strict';
import {database} from './fixture-db.mjs';import {backupStudy,checksum} from '../backup-worker/index.js';
test('daily snapshot preserves old records, stays immutable for the day and retains 90 days',async()=>{
 const db=database(),account='a'.repeat(64),now=Date.parse('2026-11-13T19:20:00Z');
 try{
  await db.prepare('INSERT INTO accounts VALUES(?,?)').bind(account,now).run();await db.prepare('INSERT INTO single_study_profile VALUES(1,?)').bind(account).run();
  await db.prepare('INSERT INTO study VALUES(?,?,?,?,?,?)').bind(account,'b'.repeat(32),'2026-11-13','HIMYM010102',60000,0).run();
  await db.prepare('INSERT INTO study_backups VALUES(?,?,?,?)').bind('2026-01-01',now-91*86400000,'{}','old').run();
  await backupStudy({STUDY_DB:db},now);
  const first=await db.prepare('SELECT * FROM study_backups WHERE day=?').bind('2026-11-14').first();assert.equal(first.sha256,await checksum(first.payload));assert.equal(JSON.parse(first.payload).rows[0].audioMs,60000);
  await db.prepare('UPDATE study SET audio=86400000').run();await backupStudy({STUDY_DB:db},now+10000);
  assert.deepEqual(await db.prepare('SELECT * FROM study_backups WHERE day=?').bind('2026-11-14').first(),first);
  assert.equal((await db.prepare('SELECT COUNT(*) AS n FROM study_backups').first()).n,1);
 }finally{db.sql.close();}
});
