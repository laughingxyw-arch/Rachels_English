"""Build the Tower Bridge lesson from the downloaded original audio.
Run from repository root after downloading epfQlb_Tgco-source.webm.
Audio times are relative to 18 seconds in the source video.
"""
from pathlib import Path
import json,subprocess,wave
import numpy as np
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'site/dist';D=ROOT/'downloads';ID='epfQlb_Tgco';rate=48000
subprocess.run(['ffmpeg','-v','error','-y','-ss','18','-i',str(D/(ID+'-source.webm')),'-t','20','-ar',str(rate),'-ac','2',str(D/(ID+'-opening.wav'))],check=True)
def read(p):
 with wave.open(str(p)) as w:return np.frombuffer(w.readframes(w.getnframes()),np.int16).reshape(-1,2).astype(np.float64)
orig=read(D/(ID+'-opening.wav'))
audit={a['word']:a for a in json.loads((D/(ID+'-waveform-audit.json')).read_text())}
def teacher(a,b,word):
 off=audit[word]['offset'];tmp=D/'epf-teacher-crop.wav'
 subprocess.run(['ffmpeg','-v','error','-y','-ss',str(a+off),'-i',str(D/(ID+'-source.webm')),'-t',str(b-a),'-ar',str(rate),'-ac','2',str(tmp)],check=True)
 x=read(tmp);tmp.unlink();return x
# Every short sentence stays intact; only the longer third sentence is chunked.
spec=[
 (1.10,2.15,[(1.10,2.15,'Jack, what have you got?')],['what’ve：v 不明显','got：t 收住']),
 (2.52,3.88,[(2.52,3.88,'It’s exactly what he said.')],['exactly：t 省略','what he：h 省略，t 为闪音']),
 (4.59,6.80,[(4.59,5.29,'He’s got everything'),(5.235,6.80,'but a model of Tower Bridge.')],['got everything：闪音连接','of：弱读，v 不明显']),
 (7.12,9.06,[(7.12,9.06,'Enough explosives to blow the foundation.')],['to：非常轻','foundation：-tion → /ʃən/']),
 (9.16,10.13,[(9.16,10.13,'He’s a courier.')],['he’s a：连读','courier：重音在首音节']),
 (10.71,12.49,[(10.71,12.49,'He was paid to accept a package.')],['paid to：d 连入 to','was：弱读']),
 (12.57,13.19,[(12.57,13.19,'That’s it.')],['that’s it：连读，末尾 t 收住']),
 (13.29,14.90,[(13.29,14.90,'Okay, listen. The meeting just finished.')],['meeting：闪音 t','just finished：just 的 t 省略']),
 (14.94,16.36,[(14.94,16.36,'I’ll take Wright back to the embassy.')],['Wright：w 不发音','to：弱读']),
 (16.61,17.70,[(16.61,17.70,'I’m riding with him.')],['I’m、him：重读']),
 (18.24,19.07,[(18.24,19.07,'Is that Jack?')],['that：th 省略','问句末尾升调'])]
translations=['杰克，你查到什么了？', '和他说的一模一样。', '除了伦敦塔桥的模型，他什么都备齐了。', '炸药足够炸毁桥的地基。', '他只是个送包裹的。', '有人付钱让他接收一个包裹。', '就这些。', '好，听着。会议刚结束。', '我会把赖特送回大使馆。', '我跟他坐同一辆车。', '那是杰克吗？']
clips=OUT/'audio'/ID;clips.mkdir(parents=True,exist_ok=True)
def clip(a,b,name,word=None,tail=False):
 x=orig[round(a*rate):round(b*rate)].copy()
 if word:
  if tail:
   # The isolated native replay removes the following I'll from finished.
   anchor=14.52;y=teacher(anchor,b,word);pos=round((anchor-a)*rate);n=min(len(y),len(x)-pos);fade=round(.008*rate)
   x[pos:pos+fade]=x[pos:pos+fade]*(1-np.linspace(0,1,fade)[:,None])+y[:fade]*np.linspace(0,1,fade)[:,None];x[pos+fade:pos+n]=y[fade:n]
  else:x=teacher(a,b,word)
 fade=round(.003*rate);x[:fade]*=np.linspace(0,1,fade)[:,None];x[-fade:]*=np.linspace(1,0,fade)[:,None]
 x=np.vstack([np.zeros((round(.08*rate),2)),x,np.zeros((round(.12*rate),2))]);x=np.clip(x,-32768,32767).astype(np.int16)
 filename=f'audio/{ID}/{name}.wav'
 with wave.open(str(OUT/filename),'w') as w:w.setnchannels(2);w.setsampwidth(2);w.setframerate(rate);w.writeframes(x.tobytes())
 return filename,len(x)/rate,x
G=[];B=[];chunks=[];cursor=0
for i,(a,b,parts,cues) in enumerate(spec):
 whole=f'g{i+1:02}';f,d,x=clip(a,b,whole,'finished' if i==7 else None,i==7)
 G.append(dict(id=i,zh=translations[i],start=a,end=b,phrases=[dict(start=p,end=q,text=t) for p,q,t in parts],audioFile=f,lead=.08,duration=d))
 targets=[]
 if len(parts)>1:
  for j,(p,q,t) in enumerate(parts):
   pf,pd,px=clip(p,q,f'{whole}-p{j+1}','everything' if j==0 else 'but');targets.append((p,q,t,pf,pd,px,False))
 targets.append((a,b,' '.join(t for _,_,t in parts),f,d,x,True))
 for p,q,t,pf,pd,px,iswhole in targets:
  if chunks:chunks.append(np.zeros((round(1.2*rate),2),np.int16));cursor+=1.2
  entry=dict(audioFile=pf,sourceStart=p,sourceEnd=q,lead=.08,duration_seconds=pd,text=t,group=i,whole=iswhole,repeats=3,start_seconds=round(cursor,5),plays=[])
  for repeat in range(3):
   if repeat:chunks.append(np.zeros((round(.7*rate),2),np.int16));cursor+=.7
   entry['plays'].append(dict(start_seconds=round(cursor,5),duration_seconds=pd));chunks.append(px);cursor+=pd
  entry['end_seconds']=round(cursor,5);B.append(entry)
lesson=dict(groups=G,drill=B,cues=[s[3] for s in spec]);(OUT/'lessons'/f'{ID}.js').write_text('window.LESSON = '+json.dumps(lesson,ensure_ascii=False)+';\n')
with wave.open(str(D/(ID+'-drill.wav')),'w') as w:w.setnchannels(2);w.setsampwidth(2);w.setframerate(rate);w.writeframes(np.concatenate(chunks).tobytes())
subprocess.run(['ffmpeg','-v','error','-y','-i',str(D/(ID+'-drill.wav')),'-codec:a','libmp3lame','-q:a','2',str(D/(ID+'-listening-drill.mp3'))],check=True)
(D/(ID+'-drill-plan.txt')).write_text('\n\n'.join(f'{n+1:02}. {e["text"]} × 3'+('（整句）' if e['whole'] else '') for n,e in enumerate(B)))
(D/(ID+'-drill.json')).write_text(json.dumps(lesson,ensure_ascii=False,indent=2))
p=OUT/'courses.js';courses=json.loads(p.read_text().split('=',1)[1].strip().rstrip(';'));courses[0].update(sourceSeconds=round(spec[-1][1]-spec[0][0],2),drillSeconds=round(cursor,3));p.write_text('window.COURSES = '+json.dumps(courses,ensure_ascii=False,indent=2)+';\n')
print(json.dumps(dict(groups=len(G),blocks=len(B),plays=len(B)*3,seconds=round(cursor,3))))
