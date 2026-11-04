# 《老爸老妈浪漫史》S01E02：审核通过的第 1、2 段

用户审核第二集的四段试听后，明确选中 ① 自然邀约、② 派对寒暄与再次邀约。仅发布这两段；第 3、4 段和整集合集均不发布。

| 课程 | 本地第二集窗口范围 | 连续原音 | 句子组 | 分段长句 | 默认 Drill |
| --- | --- | --- | --- | --- | --- |
| 寻妈记 · 自然邀约 | 01:48.35–05:58.75 | 250.404 秒 | 65 | 17 | 约 19:25 |
| 寻妈记 · 派对寒暄 | 06:04.65–09:49.15 | 224.516 秒 | 63 | 12 | 约 16:49 |

第二集窗口在用户原合集中的起点为 22:19；课程原文件起点分别为 24:07.35、28:23.65。连续原音直接复制审核通过的两个 AAC 文件，SHA256 逐字节相同，不重编码、不拼接、不加入句间静音。独立句子和意群切片使用 AAC 128 kb/s、48kHz 双声道，保留现有 80ms 前留白、120ms 后留白与 3ms 边缘渐变。每个意群默认三遍，然后完整组再三遍；兼容现有 2–5 遍、留白跟读及循环。中文默认关闭。本集没有教师教学音轨，发音提示不伪造，字段留空。

英文、中文、意群、切点和截图来源保存在 [himym-s01e02.json](../site/content/himym-s01e02.json)。Whisper base / small.en 的本地识别与公开参考字幕交叉核对，字幕时间轴分别重新拟合；Wav2Vec2 对连续多组共同强制对齐，结合识别时间修正不可靠的首尾。反复出现的 the roof、电话前的停顿、字幕中的 Scherbatsky 拼写等均有修正。Crazy monkey style 与 That's not the plan、Barney 的搜索打断、论文抱怨后的快速回应等相邻说话人的紧密交接保留在同一组，避免强行切开叠音。此为自动识别、对齐及文本复核，不宣称完整逐句人工听审；小米 15 切点听感未验证。

真实截图均对应选定范围：自然邀约使用 [Apple TV 第二集页面](https://tv.apple.com/ca/episode/purple-giraffe/umc.cmc.6igeypwuvbkw8n2yl78zcdqoj) 的超市抓娃娃机报道画面；派对寒暄使用 [Business Insider 第一季细节回顾](https://www.businessinsider.com/how-i-met-your-mother-details-you-missed-season-one) 的第一场派对画面（原文说明 Marshall 称 Ted 为 Gatsby，图片标注 CBS）。只裁切与压缩，不生成或叠加标题。图片 SHA256、原图地址与裁切范围进入 lesson.artwork，参与版本计算。

构建：

```sh
python3 site/scripts/build_himym_lessons.py --spec site/content/himym-s01e02.json --raw downloads/himym-local/s01e02
python3 scripts/build_content.py
python3 scripts/check_content.py
```

原始音频、模型、ASR 草稿、参考字幕和八个首尾复核窗口仅保存在被忽略的 downloads 目录；生成的两份英文 SRT 同样保存在本地 review 目录。通用构建脚本默认仍为第一集，新增参数指定本集，不重建原有课程。

十课包检查通过，原有八课包摘要不变；两课全部 190 个音频可在浏览器解码，AAC 时长最大差约 1.02ms。390px 宽度下验证 65/63 组、意群后完整句的三遍顺序、复读次数切换、中文默认关闭与开关、B 站来源、真实截图加载，无脚本错误或横向溢出。两课课程包大小分别为 7,005,970 和 6,097,913 字节，均保留原有 25 MB 下载预算。

本次只更新云端内容，未更改 Android、UI、动画、统计或玻璃材质，不编译 APK。现有 2.1.16 支持来源地址及 AAC；小米 15 / Android 16 的 120Hz 真机帧时间未验证。
