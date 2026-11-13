"""Export immutable verified lesson bundles and a catalog for offline clients."""
from pathlib import Path
import json,hashlib,zipfile,shutil
ROOT=Path(__file__).resolve().parents[1];SITE=ROOT/'site/dist';OUT=ROOT/'cloud/dist';APP=ROOT/'android/app/src/main/assets/site'
def data(path):return json.loads(path.read_text().split('=',1)[1].strip().rstrip(';'))
import argparse
parser=argparse.ArgumentParser();parser.add_argument('--bundle-android',action='store_true');parser.add_argument('--include-private-setup',action='store_true');args=parser.parse_args()
if OUT.exists():shutil.rmtree(OUT)
OUT.mkdir(parents=True,exist_ok=True)
if args.bundle_android and APP.exists():shutil.rmtree(APP)
bases=[OUT,APP] if args.bundle_android else [OUT]
courses=data(SITE/'courses.js')
used={'passkey-enroll.js','.well-known/assetlinks.json','passkey-setup/index.html','passkey-probe/index.html','index.html','lesson.html','app.js','style.css','courses.js','home.js','lesson-loader.js','motion.css','motion.js','reading-trace.js','icon.svg'}
for c in courses:
 lesson=data(SITE/'lessons'/f'{c["id"]}.js');files={g['audioFile'] for g in lesson['groups']+lesson['drill']};files.update([lesson['continuous']['audioFile']] if lesson.get('continuous') else []);used.update(files);used.add(c['cover']);used.add('lessons/'+c['id']+'.js')
 buf=ROOT/'cloud'/f'{c["id"]}.zip'
 with zipfile.ZipFile(buf,'w',zipfile.ZIP_DEFLATED) as z:
  def add(name,bytes):
   info=zipfile.ZipInfo(name,date_time=(2026,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,bytes)
  add('lesson.json',json.dumps(lesson,ensure_ascii=False,separators=(',',':')).encode('utf-8'))
  for f in sorted(files):add(f,(SITE/f).read_bytes())
 digest=hashlib.sha256(buf.read_bytes()).hexdigest();dest=f'packages/{c["id"]}-{digest[:16]}.zip';(OUT/dest).parent.mkdir(exist_ok=True);shutil.move(buf,OUT/dest)
 c.update(version=digest[:16],sha256=digest,bundle=dest,bundleBytes=(OUT/dest).stat().st_size)
for f in used:
 for base in bases:
  if base==APP and not (f.startswith(('audio/','clips-v5/','covers/'))):continue
  (base/f).parent.mkdir(parents=True,exist_ok=True);shutil.copy2(SITE/f,base/f)
if args.bundle_android:
 for c in courses:
  target=APP/'lessons'/f'{c["id"]}.json';target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(data(SITE/'lessons'/f'{c["id"]}.js'),ensure_ascii=False))
catalog=json.dumps(dict(schemaVersion=1,courses=courses),ensure_ascii=False,indent=2)
for base in bases:(base/'catalog.json').write_text(catalog)
(OUT/'_headers').write_text('/passkey-setup/*\n  Cache-Control: no-store\n  Referrer-Policy: no-referrer\n/passkey-probe/*\n  Cache-Control: no-store\n  Referrer-Policy: no-referrer\n/.well-known/assetlinks.json\n  Content-Type: application/json\n  Cache-Control: max-age=300\n/catalog.json\n  Cache-Control: no-cache\n/packages/*\n  Cache-Control: public, max-age=31536000, immutable\n')
apk=ROOT/'downloads/rachels-english.apk'
if apk.exists():shutil.copy2(apk,OUT/'rachels-english.apk')
# A privately provisioned first-use package is never listed in the public catalog.
private_meta=ROOT/'downloads/study-recovery/native-setup-private.json'
private_apk=ROOT/'downloads/study-recovery/native-setup.apk'
if args.include_private_setup and private_meta.exists() and private_apk.exists():
 import time,re
 setup=json.loads(private_meta.read_text())
 if setup['expires']>int(time.time()*1000):
  assert re.fullmatch('[a-f0-9]{64}',setup['download'])
  assert hashlib.sha256(private_apk.read_bytes()).hexdigest()==setup['apkSha256']
  target=OUT/'private-install'/setup['download']/'rachels-english.apk'
  target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(private_apk,target)
  with (OUT/'_headers').open('a') as h:h.write('/private-install/*\n  Cache-Control: no-store\n  Referrer-Policy: no-referrer\n  X-Robots-Tag: noindex, nofollow\n')
assets=list(OUT.rglob('*'));files=[f for f in assets if f.is_file()]
assert len(files)<=19000,'Free static asset file budget exceeded'
assert all(f.stat().st_size<=25*1024*1024 for f in files),'Free static asset size exceeded'
print(f'{len(courses)} courses, {len(files)} public files; all within free asset limits')
