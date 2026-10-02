from pathlib import Path
import json,hashlib,zipfile,io
ROOT=Path(__file__).resolve().parents[1];DIST=ROOT/'cloud/dist'
catalog=json.loads((DIST/'catalog.json').read_text());assert catalog['schemaVersion']==1
for c in catalog['courses']:
 b=(DIST/c['bundle']).read_bytes();assert hashlib.sha256(b).hexdigest()==c['sha256']
 with zipfile.ZipFile(io.BytesIO(b)) as z:
  assert z.testzip() is None
  names=set(z.namelist());lesson=json.loads(z.read('lesson.json'))
  for i,g in enumerate(lesson['groups']):
   assert g['id']==i and g['audioFile'] in names and g.get('zh')
  for block in lesson['drill']:
   assert block['audioFile'] in names and len(block['plays'])==block['repeats']
  assert all(not n.startswith('/') and '..' not in n and '\\' not in n for n in names)
 print(c['id'],'bundle, translations, audio references and checksums passed')
