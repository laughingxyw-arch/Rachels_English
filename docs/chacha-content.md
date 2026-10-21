# Cha Cha Real Smooth 教材

来源：[Learn English with Movies!](https://www.youtube.com/watch?v=2JnnSu53QS0)，并参考[老师发布的文字稿与发音讲解](https://rachelsenglish.com/learn-english-with-movies/)。只取开场求职面试的原片对白，约 60.60–92.82 秒，共 32.22 秒。封面从原片中应聘者的画面选取，裁去教学品牌水印和黑边，未使用 YouTube 缩略图。

9 个句子组，18 个独立练习切片及一份连续原音。以下长句按完整意群分段，每段后再重复原句：

- Hope Loves a Friend / is the name of our nonprofit, yeah.
- But why do you want to join the team / as a communications associate?
- I studied marketing at Tulane, / and I have, uh, a lot of experience / with social media, design.
- And a lot of experience with CRM tools / like Buffer and Mailchimp.

保留 Well、uh、停顿、迟疑和原片升调。短句保留完整，不按老师分析时的单音、单词细切。默认各段和完整句三遍，共 54 次、约 189.44 秒；兼容现有客户端 2–5 遍、循环及留白跟读。中文默认关闭，发音提示保持简短，原片升调用于观察与跟读，不将其写成面试表达建议。

自动字幕将 Tulane 识别为 Tlane；Whisper 还将 Mailchimp 误识别，两个识别器均未输出最后的非词回应 Mm-hm。文字依据原片、老师讲解与公开文字稿核对，音频边界以 Whisper、Wav2Vec2、局部能量及老师独立重放交叉检查。额外提取约 17:34、25:43、27:02、28:37 后的重放，核对 Tulane、tools、Mailchimp 以及 Mm-hm；比较 tools 和 Mailchimp 时另排除包含后续短语的重放，避免相同整段造成错误判断。局部相关峰值约 0.99。Mm-hm 早在约 92.15 秒进入，不能按前一句转写时长或声量猜测，切片边界已相应前移。

生成脚本：[build_chacha_lesson.py](../site/scripts/build_chacha_lesson.py)。输入母音频为从原视频零点起的 48kHz、16-bit、双声道 WAV。原始下载、字幕、识别、对齐模型和波形审计均不提交。独立切片沿用 80ms 前留白、120ms 后留白及 3ms 边缘渐变；连续原音直接导出母音频对应 PCM，保留全部原有停顿，不拼接练习切片。连续 PCM 已逐字节与母音频对应区间比较。

新课程 ZIP 为 8,654,523 字节（约 8.65 MB）。六课课程包、音频引用、翻译、校验和及连续时长检查通过，原有五课 SHA256 均未改变。手机宽度浏览器验证封面加载、9 句、全部 19 个 WAV 解码、三段后完整句的 12 次复读顺序、次数切换及中文默认关闭／开启显示，无脚本错误或横向溢出。

只增加教材，不修改 Android 应用、播放逻辑、统计或动画，无需更新 APK。小米 15 / Android 16 的实际分段听感与 120Hz 真机帧时间未验证。
