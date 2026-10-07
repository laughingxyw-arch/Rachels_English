"""Build only the Friends dialogue from kwz6Z1rsX9A.

Input: downloads/kwz6Z1rsX9A-opening.wav (48kHz stereo).
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
ID = 'kwz6Z1rsX9A'
RATE = 48000
ORIGIN = 53.16
with wave.open(str(ROOT / 'downloads' / f'{ID}-opening.wav')) as wav:
    assert (wav.getframerate(), wav.getnchannels(), wav.getsampwidth()) == (RATE, 2, 2)
    original = np.frombuffer(wav.readframes(wav.getnframes()), np.int16).reshape(-1, 2)

# Natural meaning groups, rather than every fragment of the teacher's analysis.
# Each longer sentence gets its phrases, then the complete sentence, three times.
spec = [
    (53.16, 54.38, [(53.16, 54.38, 'Oh, Joey.')], '噢，乔伊。', ['Joey 首音节重读，句尾降调']),
    (54.62, 56.36, [(54.62, 56.36, 'I have such a problem.')], '我遇到了一个大难题。', ['I have 快速弱读；such a 连读；problem 重读']),
    (56.50, 58.32, [(56.50, 58.32, 'Oh, well, your timing couldn’t be better.')], '那你来得正是时候。', ['your 弱读；couldn’t 的 t 省略；better 的 t 轻拍']),
    (58.32, 60.43, [
        (58.32, 59.35, 'I am putting out fires'),
        (59.35, 60.43, 'all over the place today.')
    ], '我今天到处都在救急。', ['I am 不缩写，强调 I；putting 末尾为 n；fires all、all over 连读']),
    (61.83, 62.96, [(61.83, 62.96, 'Okay, okay.')], '好，好。', ['okay 第二音节重读，保留音高起落']),
    (63.00, 66.05, [(63.00, 66.05, 'Joey, I have got to tell you something.')], '乔伊，我必须告诉你一件事。', ['got 重读；got to 共用一个 t，to 弱读；tell、something 重读']),
    (66.05, 67.145, [(66.05, 67.145, 'What? What? What is it? What is it?')], '什么？什么？什么事？什么事？', ['独立 what 的 t 收住；what is it 连读，is 重读']),
    (67.145, 68.535, [(67.145, 68.535, 'Oh my God, it’s so huge.')], '天啊，这事可太重大了。', ['God、huge 重读，情绪带动音高变化']),
    (68.535, 71.10, [
        (68.535, 69.555, 'But you just have to promise me—'),
        (69.555, 71.10, 'you cannot tell anyone.')
    ], '但你必须答应我，不能告诉任何人。', ['but you → /bətʃə/；just 的 t 省略；have to → /hæftə/；cannot 不缩写']),
    (71.10, 72.82, [(71.10, 72.82, 'Oh, no, no, no, no, no, no. I don’t want to know.')], '噢，不不不不不不，我不想知道。', ['连续 no 保持节奏；don’t、want to 中的 t 省略'])
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
catalog.insert(0, dict(id=ID, title='Friends · 秘密', label='剧集对白',
                       videoTitle='THIS IS WHY IT’S SO DIFFICULT: How to Speak American English | Learn English with FRIENDS',
                       excerpt='You cannot tell anyone.', added='2026-10-07',
                       sourceSeconds=round(spec[-1][1]-ORIGIN, 3), sourceStartSeconds=ORIGIN,
                       groupCount=len(groups), drillSeconds=round(cursor, 3),
                       cover=f'covers/{ID}-scene.webp'))
catalog_file.write_text('window.COURSES = ' + json.dumps(catalog, ensure_ascii=False, indent=2) + ';\n')
print(json.dumps(dict(groups=len(groups), blocks=len(drill), defaultPlays=len(drill)*3,
                      sourceSeconds=catalog[0]['sourceSeconds'], drillSeconds=round(cursor, 3))))
