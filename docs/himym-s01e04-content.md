# 《老爸老妈浪漫史》S01E04：重新联系与道歉

用户审核第四集四段试听后，仅选中第 ② 段。只发布“寻妈记 · 重新联系与道歉”（HIMYM010402）；本集其余三段和未获选的第三集片段均不发布。

本地第四集窗口范围 05:25.50–11:17.75，原合集范围 01:11:10.50–01:17:02.75。审核通过的连续 AAC 文件为 352.254 秒，逐字节复制到 continuous.m4a，不重编码、不拼接，也不额外加入句间静音。原剧穿插的 Robin 新闻及老板谈话完整保留。

课程有 84 组对白、17 组分段长句和 120 个 Drill 音频块。先按完整意群复读，再复读完整组；默认三遍，兼容已有 2–5 遍、留白、后台播放与四种循环范围。默认不留白的静态 Drill 时间约 23:51。英文、中文、分段、切点和真实封面来源保存在 [himym-s01e04.json](../site/content/himym-s01e04.json)。中文仍默认关闭；这一集没有教师讲解音轨，不编造发音提示。

使用本地 Whisper base 全集草稿和 small.en 选段识别，与公开英文参考字幕交叉核对；参考字幕重新拟合时间轴，不直接作为最终切点。Wav2Vec2 对连续多组联合强制对齐，并通过九个独立短窗口复核异常边界。修正电话铃被误识为 Hello 的开始、Uh-oh 的过早起点、重复生日留言中的 You're awesome / I'm just / But you're awesome，以及 Yeah 被延长到下一句等问题。生日责备等叠音保留整组；长篇新闻句按语义拆三段，其他长句避免机械逐词切分。此为识别、对齐及文本复核，不宣称完成逐句人工听审；真机切点听感未验证。

封面使用 [Apple TV 第四集页面](https://tv.apple.com/us/episode/return-of-the-shirt/umc.cmc.6d8i1jhgd765fjs4cx559hgm0) 的真实剧照：Ted 抱着袜子猴在 Natalie 门口道歉，属于本次选段。完整保留原图画面，只缩放和压缩，不生成画面或叠字；原图及输出 SHA256、来源链接和完整裁切范围均写入 artwork。

构建命令：

```sh
python3 site/scripts/build_himym_lessons.py --spec site/content/himym-s01e04.json --raw downloads/himym-local/s01e04
python3 scripts/build_content.py
python3 scripts/check_content.py
```

十一课包的翻译、音频引用、连续原音、SHA256 和下载预算检查通过，原有十课包摘要不变。新课 121 个音频在浏览器全部解码，AAC 时长最大差约 1.01ms；390px 宽度下验证 84 组、意群后完整句的三遍顺序、复读次数切换、中文默认关闭及开关、B 站来源和真实截图，无脚本错误及横向溢出。原始用户音频、参考字幕、模型、识别草稿和生成的英文 SRT 只保留在被忽略的 downloads 中。

本次只更新云端内容，不修改 Android、动画、布局、播放器或统计口径，不重新编译 APK。现有 2.1.17 可直接使用；小米 15 / Android 16 的 120Hz 真机帧时间未验证。
