"""Build only the Friends dialogue from robx0RPxyd4.

Input: downloads/robx0RPxyd4-opening.wav (48kHz stereo).
The master starts at the beginning of the source video. Cuts use local word
alignment and energy inspection, not caption boundaries alone. Raw source media
stays untracked.
"""
from pathlib import Path
import json
import wave
import numpy as np

ROOT = Path(__file__).resolve().parents[2]
DIST = ROOT / 'site/dist'
ID = 'robx0RPxyd4'
RATE = 48000
ORIGIN = 12.52
with wave.open(str(ROOT / 'downloads' / f'{ID}-opening.wav')) as wav:
    assert (wav.getframerate(), wav.getnchannels(), wav.getsampwidth()) == (RATE, 2, 2)
    original = np.frombuffer(wav.readframes(wav.getnframes()), np.int16).reshape(-1, 2)

# Natural meaning groups, rather than every fragment of the teacher's analysis.
# Each longer sentence gets its phrases, then the complete sentence, three times.
spec = [
    (12.52, 13.33, [(12.52, 13.33, 'Thank you!')], '谢谢！', ['thank 与 you 顺畅连接']),
    (13.34, 14.49, [(13.34, 14.49, 'Happy holidays!')], '节日快乐！', ['happy、holidays 首音节重读']),
    (14.565, 15.295, [(14.565, 15.295, 'And— wait.')], '还有……等等。', ['末尾 t 收住，不释放']),
    (15.52, 16.94, [(15.52, 16.94, 'You can’t take the money out.')], '你不能把钱拿走。', ['can’t take 共用一个 t；money out 连读']),
    (17.19, 17.925, [(17.19, 17.925, 'I’m making change.')], '我在换零钱。', ['I’m making 共用 m；making 末尾为 n']),
    (17.93, 18.88, [(17.93, 18.88, 'I need change for the bus.')], '我坐公交需要零钱。', ['for → /fər/；for the 轻快连读']),
    (19.14, 20.72, [(19.14, 20.72, 'But can’t you leave the dollar?')], '但你不能把那一美元留下吗？', ['can’t 的 nt 收住；问句末尾升调']),
    (20.727, 21.82, [(20.727, 21.82, 'This money’s for the poor.')], '这钱是捐给穷人的。', ['money’s 的 s 为 /z/；for 弱读']),
    (22.06, 22.6575, [(22.06, 22.6575, 'I’m poor.')], '我就是穷人。', ['I’m 短、poor 长，形成对比']),
    (22.6575, 23.79, [(22.6575, 23.79, 'I got to take the bus.')], '我得坐公交。', ['got to → gotta，t 轻拍']),
    (25.15, 27.56, [
        (25.15, 26.42, 'Okay. Season’s greetings'),
        (26.42, 27.56, 'and everything, but still.')
    ], '好吧，祝你节日愉快什么的，可这还是不行。', ['and 不读 d；greetings 的 t 轻拍；still 末尾 dark L']),
    (27.60, 28.78, [(27.60, 28.78, 'Bite me, blondie.')], '少来这套，金发妞。', ['bite 的 t 收住；带挑衅的语气']),
    (29.02, 29.37, [(29.02, 29.37, 'Oh!')], '啊！（惊讶）', ['单个感叹词也有音高起落'])
]

audio_dir = DIST / 'audio' / ID
audio_dir.mkdir(parents=True, exist_ok=True)
# Remove superseded phrase exports when the lesson plan is revised.
for stale in audio_dir.glob('*.wav'):
    stale.unlink()

def crop(a, b, name):
    samples = original[round(a * RATE):round(b * RATE)].astype(np.float64).copy()
    fade = round(.003 * RATE)
    samples[:fade] *= np.linspace(0, 1, fade)[:, None]
    samples[-fade:] *= np.linspace(1, 0, fade)[:, None]
    samples = np.vstack([np.zeros((round(.08 * RATE), 2)), samples,
                         np.zeros((round(.12 * RATE), 2))])
    samples = np.clip(np.rint(samples), -32768, 32767).astype(np.int16)
    file = f'audio/{ID}/{name}.wav'
    with wave.open(str(DIST / file), 'w') as wav:
        wav.setnchannels(2)
        wav.setsampwidth(2)
        wav.setframerate(RATE)
        wav.writeframes(samples.tobytes())
    return file, len(samples) / RATE

groups = []
drill = []
cursor = 0.0
for i, (a, b, parts, zh, cues) in enumerate(spec):
    name = f'g{i+1:02}'
    file, duration = crop(a, b, name)
    groups.append(dict(id=i, start=round(a-ORIGIN, 5), end=round(b-ORIGIN, 5), zh=zh,
                       phrases=[dict(start=round(p-ORIGIN, 5), end=round(q-ORIGIN, 5), text=t)
                                for p, q, t in parts],
                       audioFile=file, lead=.08, duration=duration))
    targets = []
    if len(parts) > 1:
        for j, (p, q, text) in enumerate(parts):
            pf, pd = crop(p, q, f'{name}-p{j+1}')
            targets.append((p, q, text, pf, pd, False))
    targets.append((a, b, ' '.join(t for _, _, t in parts), file, duration, True))
    for p, q, text, pf, pd, whole in targets:
        if drill:
            cursor += 1.2
        block = dict(group=i, whole=whole, text=text, audioFile=pf, lead=.08,
                     sourceStart=round(p-ORIGIN, 5), sourceEnd=round(q-ORIGIN, 5),
                     duration_seconds=pd, repeats=3, start_seconds=round(cursor, 5), plays=[])
        for repeat in range(3):
            if repeat:
                cursor += .7
            block['plays'].append(dict(start_seconds=round(cursor, 5), duration_seconds=pd))
            cursor += pd
        block['end_seconds'] = round(cursor, 5)
        drill.append(block)

lesson = dict(groups=groups, drill=drill, cues=[row[4] for row in spec])
from export_continuous import export
export(DIST,lesson,ID,ROOT/'downloads'/f'{ID}-opening.wav',ORIGIN)
(DIST / 'lessons' / f'{ID}.js').write_text(
    'window.LESSON = ' + json.dumps(lesson, ensure_ascii=False) + ';\n')
catalog_file = DIST / 'courses.js'
catalog = json.loads(catalog_file.read_text().split('=', 1)[1].strip().rstrip(';'))
catalog = [c for c in catalog if c['id'] != ID]
catalog.insert(0, dict(id=ID, title='Friends · 募捐', label='剧集对白',
                       videoTitle='Fast English: The TV Show Friends Can Help!',
                       excerpt='Thank you! Happy holidays!', added='2026-10-05',
                       sourceSeconds=round(spec[-1][1]-ORIGIN, 3), sourceStartSeconds=ORIGIN,
                       groupCount=len(groups), drillSeconds=round(cursor, 3),
                       cover=f'covers/{ID}-scene.webp'))
catalog_file.write_text('window.COURSES = ' + json.dumps(catalog, ensure_ascii=False, indent=2) + ';\n')
print(json.dumps(dict(groups=len(groups), blocks=len(drill), defaultPlays=len(drill)*3,
                      sourceSeconds=catalog[0]['sourceSeconds'], drillSeconds=round(cursor, 3))))
