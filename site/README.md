# 原声练习

直接用浏览器打开 `dist/index.html` 即可使用。需要完整保留页面旁的 JS、CSS、lessons 和音频目录。

首页列出课程；进入后点击句子听完整原句，或切换 Drill。多段句按每段三遍、整句三遍播放，短句整句三遍。设置可开启循环和跟读留白。

## 添加课程

1. 下载视频的原声与字幕，核对老师讲解中的原文。
2. 按意思确定适度的短语，保留较短句子完整。
3. 核对字词边界与老师播放的原声片段，导出独立 WAV。不要只依赖自动字幕时间或在浏览器实时截断。
4. 新建 `dist/lessons/VIDEO_ID.js`，沿用已有课程的 `window.LESSON` 格式。group id 连续从零开始，phrase 时间与 sourceStart 使用同一时间基准。
5. 封面使用原对白中的人物或场景截图，不使用老师的 YouTube 教学封面。在 `dist/courses.js` 的列表前面添加课程元数据，音频放入独立课程目录。课程达到六门后首页显示搜索。
6. 验证整句、Drill、暂停、切换课程及手机布局，再运行 `python3 site/scripts/package.py` 打包。

Tower Bridge 课程的生成脚本为 `scripts/build_epf_lesson.py`；审计脚本为 `scripts/audit_epf.py`。从仓库根目录运行；依赖本地 ffmpeg、numpy，审计另需 scipy。原声位于 downloads，审计与分段清单也保存于 downloads。波形核对和识别检查帮助发现问题，不替代逐段听感验收。
