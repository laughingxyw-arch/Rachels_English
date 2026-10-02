import wave,json,numpy as np
from scipy.signal import correlate
from pathlib import Path
def load(p):
 with wave.open(p) as w:return np.frombuffer(w.readframes(w.getnframes()),np.int16).astype(float)/32768
import subprocess,tempfile
ROOT=Path(__file__).resolve().parents[2]
import os
os.chdir(ROOT)
TEMP=Path(tempfile.mkdtemp(prefix='rachels-audit-'))
for target,src in [('opening','downloads/epfQlb_Tgco-opening.wav'),('full','downloads/epfQlb_Tgco-source.webm')]:
 subprocess.run(['ffmpeg','-v','error','-y','-i',src,'-ac','1','-ar','16000',str(TEMP/(target+'.wav'))],check=True)
x0=load(TEMP/'opening.wav');y0=load(TEMP/'full.wav');r=16000
checks=[('but',5.55,5.78,615,624),('got',1.78,2.02,76,83),('said',3.48,3.70,265,275),('everything',5.03,5.22,456,464),('Bridge',6.22,6.50,815,826),('explosives',7.50,7.80,841,849),('foundation',8.45,8.60,845,894),('courier',9.5,9.8,1119,1128),('package',11.92,12.06,1159,1169),('it',12.82,13.01,1418,1425),('listen',13.60,13.80,1455,1461),('finished',14.51,14.75,1504,1511),('embassy',15.90,16.13,1738,1745),('him',17.25,17.42,1762,1770),('Jack',18.60,18.82,1843,1851)]
out=[]
for name,a,b,lo,hi in checks:
 x=x0[round(a*r):round(b*r)].copy();x-=x.mean();y=y0[round(lo*r):round(hi*r)];n=len(x);cs=np.r_[0,np.cumsum(y*y)];s=correlate(y,x,'valid',method='fft')/np.sqrt((cs[n:]-cs[:-n])*(x*x).sum()+1e-20);i=int(np.argmax(s));off=lo+i/r-a;entry=dict(word=name,offset=off,correlation=float(s[i]),teacherTime=a+off);out.append(entry);print(name,round(s[i],4),round(off,5),flush=True)
Path('downloads/epfQlb_Tgco-waveform-audit.json').write_text(json.dumps(out,indent=2))

import shutil
shutil.rmtree(TEMP)
