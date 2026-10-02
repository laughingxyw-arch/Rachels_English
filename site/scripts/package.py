from pathlib import Path
import zipfile,json
root=Path(__file__).resolve().parents[2];dist=root/'site/dist'
# Package only referenced clips and the common page files, avoiding obsolete cuts.
assets=set()
for file in (dist/'lessons').glob('*.js'):
 data=json.loads(file.read_text().split('=',1)[1].strip().rstrip(';'))
 assets.update(item['audioFile'] for item in data['groups']+data['drill'])
assets.update(p.relative_to(dist).as_posix() for p in dist.glob('*.html'))
courses=json.loads((dist/'courses.js').read_text().split('=',1)[1].strip().rstrip(';'))
assets.update(c['cover'] for c in courses)
assets.update(['app.js','style.css','courses.js','home.js','lesson-loader.js','motion.css','motion.js','icon.svg'])
assets.update(p.relative_to(dist).as_posix() for p in (dist/'lessons').glob('*.js'))
with zipfile.ZipFile(root/'listening-practice.zip','w',zipfile.ZIP_DEFLATED) as z:
 for asset in sorted(assets):z.write(dist/asset,asset)
print(f'Packaged {len(assets)} files')
