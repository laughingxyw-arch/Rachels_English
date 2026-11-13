import {DatabaseSync} from 'node:sqlite';
import {readFileSync} from 'node:fs';
export function database(){
 const sql=new DatabaseSync(':memory:');
 for(const file of ['0001_study.sql','0002_passkeys.sql','0003_private_installs.sql'])sql.exec(readFileSync(new URL('../migrations/'+file,import.meta.url),'utf8'));
 const db={sql,prepare(query){const values=[];const wrapped={bind(...v){values.splice(0,values.length,...v);return wrapped},first:async()=>sql.prepare(query).get(...values)??null,all:async()=>({results:sql.prepare(query).all(...values)}),run:async()=>{const r=sql.prepare(query).run(...values);return {meta:{changes:Number(r.changes)}}}};return wrapped},async batch(items){sql.exec('BEGIN');try{const out=[];for(const item of items)out.push(await item.run());sql.exec('COMMIT');return out}catch(e){sql.exec('ROLLBACK');throw e}}};
 return db;
}
