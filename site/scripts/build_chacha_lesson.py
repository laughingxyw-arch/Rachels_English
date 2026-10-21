"""Build only the Cha Cha Real Smooth dialogue from 2JnnSu53QS0.

Input: downloads/2JnnSu53QS0-opening.wav (48kHz stereo).
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
ID = '2JnnSu53QS0'
RATE = 48000
ORIGIN = 60.60
with wave.open(str(ROOT / 'downloads' / f'{ID}-opening.wav')) as wav:
    assert (wav.getframerate(), wav.getnchannels(), wav.getsampwidth()) == (RATE, 2, 2)
    original = np.frombuffer(wav.readframes(wav.getnframes()), np.int16).reshape(-1, 2)

# Natural meaning groups, rather than every fragment of the teacher's analysis.
# Each longer sentence gets its phrases, then the complete sentence, three times.
spec = [
    (60.60, 61.87, [(60.60, 61.87, 'Why do you want this job?')], '你为什么想做这份工作？', ['why、want 重读；do you 轻快连读；want 的 t 收住']),
    (63.62, 67.96, [(63.62, 67.96, 'Well, uh, Hope Loves a Friend.')], '嗯，呃……Hope Loves a Friend。', ['well 为 dark L；uh 保留思考的语气；hope、friend 重读']),
    (69.65, 72.67, [
        (69.65, 70.70, 'Hope Loves a Friend'),
        (70.70, 72.67, 'is the name of our nonprofit, yeah.')
    ], 'Hope Loves a Friend 是我们公益组织的名字，没错。', ['loves a、friend is 连读；our 可读得像 are；nonprofit 的 t 收住']),
    (73.00, 76.30, [
        (73.00, 74.64, 'But why do you want to join the team'),
        (74.64, 76.30, 'as a communications associate?')
    ], '但你为什么想加入团队，担任传播助理呢？', ['want to → wanna；team as a 连读；associate 中 ci 为 /ʃ/，末尾 t 收住']),
    (76.40, 77.44, [(76.40, 77.44, 'Yeah, sorry.')], '啊，对，不好意思。', ['yeah、sorry 都有自然的重音起落']),
    (78.20, 86.40, [
        (78.20, 80.98, 'I studied marketing at Tulane,'),
        (81.16, 83.65, 'and I have, uh, a lot of experience'),
        (83.65, 86.40, 'with social media, design.')
    ], '我在杜兰大学学市场营销，在社交媒体、设计方面有很多经验。', ['marketing 的 t 轻拍；a lot of 的 t 轻拍；保留原片迟疑和升调']),
    (87.60, 91.215, [
        (87.60, 90.01, 'And a lot of experience with CRM tools'),
        (90.01, 91.215, 'like Buffer and Mailchimp.')
    ], '也有很多使用 CRM 工具的经验，比如 Buffer 和 Mailchimp。', ['CRM 重读最后的 M；Buffer and Mailchimp 中 and 很弱、d 省略；p 收住']),
    (91.215, 92.15, [(91.215, 92.15, 'A lot of experience there.')], '这方面我很有经验。', ['开头 a 极弱；a lot of 连读；原片 there 采用升调']),
    (92.15, 92.82, [(92.15, 92.82, 'Mm-hm.')], '嗯。（示意继续说）', ['升调回应，有“继续说”的意思'])
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
catalog.insert(0, dict(id=ID, title='Cha Cha Real Smooth', label='电影对白',
                       videoTitle='Learn English with Movies! | Free Lesson on Speaking English with an American Accent',
                       excerpt='Why do you want this job?', added='2026-10-21',
                       sourceSeconds=round(spec[-1][1]-ORIGIN, 3), sourceStartSeconds=ORIGIN,
                       groupCount=len(groups), drillSeconds=round(cursor, 3),
                       cover=f'covers/{ID}-scene.webp'))
catalog_file.write_text('window.COURSES = ' + json.dumps(catalog, ensure_ascii=False, indent=2) + ';\n')
print(json.dumps(dict(groups=len(groups), blocks=len(drill), defaultPlays=len(drill)*3,
                      sourceSeconds=catalog[0]['sourceSeconds'], drillSeconds=round(cursor, 3))))
