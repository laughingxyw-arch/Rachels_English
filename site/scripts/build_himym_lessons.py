"""Build the two approved S01E01 selections from user-provided local audio.

Dialogue, meaning groups and aligned bounds are reviewable in site/content.
Continuous AAC is copied unchanged. Independent practice cuts use AAC 128 kb/s
with the same 80/120 ms padding and 3 ms edge fades as the older WAV lessons.
Raw sources and alignment models remain outside version control.
"""
from pathlib import Path
import json,wave,hashlib,subprocess,shutil,tempfile
from concurrent.futures import ThreadPoolExecutor
import numpy as np
ROOT=Path(__file__).resolve().parents[2];DIST=ROOT/'site/dist';RAW=ROOT/'downloads/himym-local';RATE=48000
spec=json.loads((ROOT/'site/content/himym-s01e01.json').read_text())
def duration(path):return float(subprocess.check_output(['ffprobe','-v','error','-show_entries','format=duration','-of','default=nw=1:nk=1',str(path)]))
def subtitle_time(t):
 ms=round(t*1000);h,ms=divmod(ms,3600000);m,ms=divmod(ms,60000);s,ms=divmod(ms,1000);return f'{h:02}:{m:02}:{s:02},{ms:03}'
new=[]
for c in spec['courses']:
 ID=c['id'];source=RAW/'review'/c['sourceFile'];audio=DIST/'audio'/ID;audio.mkdir(parents=True,exist_ok=True)
 for stale in audio.glob('*.m4a'):stale.unlink()
 continuous=audio/'continuous.m4a';shutil.copyfile(source,continuous);cd=duration(continuous)
 wav=RAW/f"course-{c['number']}-48k.wav"
 subprocess.run(['ffmpeg','-v','error','-y','-i',str(source),'-ar',str(RATE),'-ac','2',str(wav)],check=True)
 with wave.open(str(wav)) as w:pcm=np.frombuffer(w.readframes(w.getnframes()),np.int16).reshape(-1,2)
 tasks=[];groups=[];blocks=[];cursor=0.
 def encode(task):
  a,b,name=task;samples=pcm[round(a*RATE):round(b*RATE)].astype(np.float64).copy();assert len(samples)>300
  fade=min(round(.003*RATE),len(samples)//2);samples[:fade]*=np.linspace(0,1,fade)[:,None];samples[-fade:]*=np.linspace(1,0,fade)[:,None]
  samples=np.vstack([np.zeros((round(.08*RATE),2)),samples,np.zeros((round(.12*RATE),2))]);samples=np.clip(np.rint(samples),-32768,32767).astype(np.int16)
  dest=audio/f'{name}.m4a'
  with tempfile.NamedTemporaryFile(suffix='.wav') as f:
   with wave.open(f.name,'wb') as w:w.setnchannels(2);w.setsampwidth(2);w.setframerate(RATE);w.writeframes(samples.tobytes())
   subprocess.run(['ffmpeg','-v','error','-y','-i',f.name,'-map_metadata','-1','-c:a','aac','-b:a','128k','-movflags','+faststart',str(dest)],check=True)
  d=duration(dest);assert abs(d-len(samples)/RATE)<.005
  return name,(f'audio/{ID}/{name}.m4a',d)
 for i,r in enumerate(c['rows']):
  name=f'g{i+1:03}';tasks.append((r['start'],r['end'],name))
  if len(r['phrases'])>1:
   for j,p in enumerate(r['phrases']):tasks.append((p['start'],p['end'],f'{name}-p{j+1}'))
 with ThreadPoolExecutor(max_workers=4) as pool:exports=dict(pool.map(encode,tasks))
 for i,r in enumerate(c['rows']):
  name=f'g{i+1:03}';file,d=exports[name];phrases=[dict(p) for p in r['phrases']]
  end=cd if i==len(c['rows'])-1 else r['end']
  if i==len(c['rows'])-1:phrases[-1]['end']=end
  groups.append(dict(id=i,start=r['start'],end=end,zh=r['zh'],phrases=phrases,audioFile=file,lead=.08,duration=d))
  targets=[]
  if len(phrases)>1:
   for j,p in enumerate(phrases):
    pf,pd=exports[f'{name}-p{j+1}'];targets.append((p['start'],p['end'],p['text'],pf,pd,False))
  targets.append((r['start'],end,' '.join(r['parts']),file,d,True))
  for a,b,text,pf,pd,whole in targets:
   if blocks:cursor+=1.2
   block=dict(group=i,whole=whole,text=text,audioFile=pf,lead=.08,sourceStart=a,sourceEnd=b,duration_seconds=pd,repeats=3,start_seconds=round(cursor,5),plays=[])
   for repeat in range(3):
    if repeat:cursor+=.7
    block['plays'].append(dict(start_seconds=round(cursor,5),duration_seconds=pd));cursor+=pd
   block['end_seconds']=round(cursor,5);blocks.append(block)
 lesson=dict(groups=groups,drill=blocks,cues=[r['cues'] for r in c['rows']],continuous=dict(audioFile=f'audio/{ID}/continuous.m4a',sourceStart=0,duration=cd,sha256=hashlib.sha256(continuous.read_bytes()).hexdigest()))
 if c.get('artwork'):lesson['artwork']=c['artwork']
 (DIST/'lessons'/f'{ID}.js').write_text('window.LESSON = '+json.dumps(lesson,ensure_ascii=False)+';\n')
 srt='\n\n'.join(f"{i+1}\n{subtitle_time(g['start'])} --> {subtitle_time(g['end'])}\n"+' '.join(p['text'] for p in g['phrases']) for i,g in enumerate(groups))+'\n';(RAW/'review'/f'{ID}.en.srt').write_text(srt)
 new.append(dict(id=ID,title=c['title'],label='S01E01 · 剧集对白',videoTitle='How I Met Your Mother · S01E01 · Pilot',excerpt=' '.join(c['rows'][0]['parts']),added='2026-11-04',sourceSeconds=cd,sourceStartSeconds=c['start'],sourceUrl=c['sourceUrl'],groupCount=len(groups),drillSeconds=round(cursor,3),cover=f'covers/{ID}-scene.webp'))
 print(ID,len(groups),'groups;',len(blocks),'blocks;',round(cursor,2),'seconds Drill;',len(tasks)+1,'audio files',flush=True)
path=DIST/'courses.js';catalog=json.loads(path.read_text().split('=',1)[1].strip().rstrip(';'));catalog=new+[c for c in catalog if c['id'] not in {c['id'] for c in new}];path.write_text('window.COURSES = '+json.dumps(catalog,ensure_ascii=False,indent=2)+';\n')
