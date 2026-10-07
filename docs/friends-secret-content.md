# Friends · 秘密教材

来源：[THIS IS WHY IT’S SO DIFFICULT](https://www.youtube.com/watch?v=kwz6Z1rsX9A)。仅导入开场 Rachel 和 Joey 的原剧对白，源视频约 53.16–72.82 秒，连续原音 19.66 秒。封面取 Rachel 穿绿色上衣说话的原剧画面，裁去老师和教学文字，未使用 YouTube 缩略图。

10 个句子组、14 个独立练习切片及一份连续原音。以下两个较长、快速的句子按意群分段，再复读完整句：

- I am putting out fires / all over the place today.
- But you just have to promise me— / you cannot tell anyone.

其余保留完整话语，不把重复的 okay、what、no 拆成单词练习。默认每段及整句三遍，共 42 次、约 111.15 秒；兼容客户端现有的 2–5 遍、循环和留白跟读。中文默认关闭，发音提示仅保留简短的老师讲解要点。cannot 保留完整形式：老师在约 28:53 特意纠正最初写成 can't 的笔记，解释 Rachel 为强调而不缩写。

生成脚本：[build_friends_secret_lesson.py](../site/scripts/build_friends_secret_lesson.py)。原始媒体、字幕、识别对齐和波形审计留在不提交的 downloads。Whisper 与 Wav2Vec2 辅助定位，不能直接采用自动字幕或强制对齐的句尾：识别漏掉 today 和部分快速问句，强制对齐也出现零时长单词。额外读取老师在约 13:29、15:49、22:29、26:06 后的独立重放，比较局部波形。快速问句中段相关约 0.99；promise me / you 边界约 0.999；anyone 尾部约 0.998，进入下一句话后相关明显下降。today 尾部结合其单独讲解与局部波形定位，将 Drill 尾部停在约 60.43 秒，省去随后的观众笑声尾段。原声中与对白重叠的观众声音保留。

切片沿用 80ms 前留白、120ms 后留白及 3ms 边缘渐变；连续原音直接取母音频对应 PCM，保留原片节奏及原有笑声，不拼接切片、不人为添加句间静音。连续文件已逐字节比对母音频对应区间。新课程 ZIP 约 5.56 MB。

五课课程包、校验和、音频引用、中文及连续时长检查通过。手机宽度浏览器验证 10 句、封面加载、全部 15 个 WAV 解码时长、分段后完整句的 9 次复读顺序、次数切换、中文默认关闭和开启显示，无脚本错误或横向溢出。未修改 Android 播放器、统计同步或原生动画，无需更新 APK。小米 15 / Android 16 的实际分段听感与 120Hz 真机帧时间未验证。
