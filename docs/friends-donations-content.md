# Friends · 募捐教材

来源：[Fast English: The TV Show Friends Can Help!](https://www.youtube.com/watch?v=robx0RPxyd4)。仅导入开场 Friends 原片对白，源视频约 12.52–29.37 秒，连续原音 16.85 秒。封面选 Phoebe 戴圣诞帽的原剧画面，裁去老师的装饰边框，未使用 YouTube 缩略图。

13 个句子组，15 个练习切片，另有一份连续原音。较短的完整句保留整句；较长且弱读密集的节日祝福按以下两个意群练习，再复读整句：

- Okay. Season’s greetings
- and everything, but still.

这是参考老师对 greetings、and everything、but still 的讲解后采用的较粗分段，不逐一复制发音分析中的小片段。默认每段及整句三遍，共 45 次、约 96.44 秒；兼容现有客户端的 2–5 遍和留白跟读。中文翻译默认关闭，每句只保留简短的连读、弱读或重音提示。

自动字幕漏掉短词 Wait 与末尾 Oh，并将部分发音写成错误词。文字按原片、老师讲解和两套识别交叉核对；保留开场 And— wait 的起句。Whisper 的高音尾音对齐偏早，不能直接作为剪辑边界。采用 Wav2Vec2 辅助定位，并用老师独立重放的局部波形确认 Wait 和 greetings → and 的边界：Wait 参考窗口相关约 0.991；greetings 尾部约 0.990，and everything 约 0.994–0.998。making change 与 I’m poor 的尾部另以独立重放辅助核对；poor 尾部局部相关约 0.975–0.996。剪辑不是仅按字幕时间截取。

生成脚本：[build_friends_lesson.py](../site/scripts/build_friends_lesson.py)。原始媒体、字幕、对齐和波形审计保存在不提交的 downloads，脚本从源 WAV 导出。切片保留现有 80ms 前留白、120ms 后留白与 3ms 边缘渐变；连续原音直接取母音频对应区间，不拼接切片、不增添句间静音。

四课包校验、翻译、音频引用及连续文件时长通过检查。浏览器成功解码新课全部 16 个 WAV，时长误差小于 1ms；手机宽度页面验证 13 句、封面加载、分段后整句的复读顺序、次数设置、中文默认关闭及开启显示，无脚本错误和横向溢出。普通播放器、统计同步和原生动画均未修改；无需更新 APK。小米 15 / Android 16 的实际分段听感与 120Hz 真机帧时间未验证。
