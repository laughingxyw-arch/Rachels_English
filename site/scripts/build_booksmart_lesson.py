"""Build only the Booksmart dialogue from hLgMIwFeE88.

Input: downloads/hLgMIwFeE88-booksmart-opening-long.wav (48kHz stereo).
The remuxed opening includes the preceding cluster: time zero is 02:11:00,
not the requested section boundary. Word alignment and isolated replays were
checked locally before choosing the cuts. Raw source media stays untracked.
"""
from pathlib import Path
import json
import wave
import numpy as np

ROOT = Path(__file__).resolve().parents[2]
DIST = ROOT / 'site/dist'
ID = 'hLgMIwFeE88'
RATE = 48000
ORIGIN = 9.54
with wave.open(str(ROOT / 'downloads' / f'{ID}-booksmart-opening-long.wav')) as wav:
    assert (wav.getframerate(), wav.getnchannels(), wav.getsampwidth()) == (RATE, 2, 2)
    original = np.frombuffer(wav.readframes(wav.getnframes()), np.int16).reshape(-1, 2)

# Natural meaning groups, rather than every fragment of the teacher's analysis.
# Each longer sentence gets its phrases, then the complete sentence, three times.
spec = [
    (9.54, 13.285, [
        (9.54, 10.875, "Our class’s official policy"),
        (10.875, 13.285, 'is to not discuss where anyone is attending next year.')
    ], '我们班的正式规定是，不讨论任何人明年会去哪所学校。',
     ['our：弱读，接 class’s', 'to → /tə/；not 的 t 收住']),
    (13.295, 15.30, [(13.295, 15.30, 'We don’t want them to feel insecure.')],
     '我们不想让他们感到不安。', ['feel、-cure 较长，其余词轻快连接']),
    (15.405, 16.165, [(15.405, 16.165, 'Very thoughtful.')],
     '考虑得真周到。', ['thoughtful：th 清楚，末尾为 dark L']),
    (16.255, 18.825, [(16.255, 18.825,
        'Anyway, I need to go over the end-of-the-year budget numbers we have.')],
     '总之，我需要核对一下我们现有的年终预算数字。',
     ['need to：to 弱读', 'the end：the 连入元音；have 尾音变轻']),
    (18.845, 23.38, [
        (18.845, 20.95, 'Oh, gosh.'),
        (21.32, 23.38, 'Really? Like now?')
    ], '天哪。真的？就现在吗？', ['really：两音节', 'like 很快；now 末尾升调']),
    (23.95, 25.745, [(23.95, 25.745, 'What— I mean, why don’t you do it with Nick?')],
     '什么……我是说，你为什么不跟尼克一起做呢？',
     ['don’t you：t 与 y 融合', 'do it：元音间自然连读；it 的 t 收住']),
    (25.77, 26.87, [(25.77, 26.87, 'You know, please.')],
     '你知道的，拜托了。', ['you：弱读为 /jə/；please 更突出'])
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
(DIST / 'lessons' / f'{ID}.js').write_text(
    'window.LESSON = ' + json.dumps(lesson, ensure_ascii=False) + ';\n')
catalog_file = DIST / 'courses.js'
catalog = json.loads(catalog_file.read_text().split('=', 1)[1].strip().rstrip(';'))
catalog = [c for c in catalog if c['id'] != ID]
catalog.insert(0, dict(id=ID, title='Booksmart', label='电影对白',
                       videoTitle='Speaking English | How to Understand Native Speakers',
                       excerpt='Our class’s official policy…', added='2026-10-04',
                       sourceSeconds=round(spec[-1][1]-ORIGIN, 3), sourceStartSeconds=7869.54,
                       groupCount=len(groups), drillSeconds=round(cursor, 3),
                       cover=f'covers/{ID}-scene.webp'))
catalog_file.write_text('window.COURSES = ' + json.dumps(catalog, ensure_ascii=False, indent=2) + ';\n')
print(json.dumps(dict(groups=len(groups), blocks=len(drill), defaultPlays=len(drill)*3,
                      sourceSeconds=catalog[0]['sourceSeconds'], drillSeconds=round(cursor, 3))))
