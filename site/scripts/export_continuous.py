"""Export the uninterrupted dialogue from its verified PCM master.

The source offset maps lesson time zero to the master WAV. Do not concatenate
isolated clips: their padding and repaired tails are specific to drill playback.
"""
from pathlib import Path
import argparse,json,wave,hashlib

def export(dist,lesson,course_id,master,offset=0.0):
    groups=lesson['groups'];start=groups[0]['start'];end=max(g['end'] for g in groups)
    with wave.open(str(master),'rb') as w:
        assert (w.getframerate(),w.getnchannels(),w.getsampwidth())==(48000,2,2)
        first=round((start+offset)*48000);last=round((end+offset)*48000)
        assert 0<=first<last<=w.getnframes(), 'Master does not cover the dialogue'
        w.setpos(first);pcm=w.readframes(last-first)
    file=f'audio/{course_id}/continuous.wav';target=dist/file;target.parent.mkdir(parents=True,exist_ok=True)
    # Keep all native pauses and overlaps, without per-sentence fades or padding.
    with wave.open(str(target),'wb') as w:
        w.setnchannels(2);w.setsampwidth(2);w.setframerate(48000);w.writeframes(pcm)
    lesson['continuous']=dict(audioFile=file,sourceStart=start,duration=(last-first)/48000,
                              sha256=hashlib.sha256(target.read_bytes()).hexdigest())
    return lesson

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('course_id');parser.add_argument('master',type=Path);parser.add_argument('--offset',type=float,default=0)
    args=parser.parse_args();dist=Path(__file__).resolve().parents[1]/'dist';f=dist/'lessons'/f'{args.course_id}.js'
    lesson=json.loads(f.read_text().split('=',1)[1].strip().rstrip(';'))
    export(dist,lesson,args.course_id,args.master,args.offset)
    f.write_text('window.LESSON = '+json.dumps(lesson,ensure_ascii=False)+';\n')
    print(args.course_id,'continuous dialogue:',lesson['continuous']['duration'],'seconds')
