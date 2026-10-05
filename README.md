# 原声练习 · Rachel’s English

极简原声跟读。课程封面来自原对白，点句子连续听原声，Drill 分段和整句默认各三遍，可在设置中调整为 2–5 遍；提供留白跟读、循环、默认关闭的中文翻译。

在线网页：https://rachels-english.pages.dev/

## 安卓安装

可直接下载 https://rachels-english.pages.dev/rachels-english.apk ，GitHub 的 **Releases** 页面也提供带版本号的正式 APK 及 SHA-256 校验文件。允许手机浏览器安装此来源的应用后安装。支持 Android 8+，目标平台 Android 16。

界面与首批课程内置 APK。启动先显示本地课程，后台同步云端课程列表；首页右上角可手动检查更新。点击新增或修订课程后下载并校验课程包，成功后安装到私有目录。断网仍可学习已下载课程。2.1.9 起支持后台及熄屏播放，系统媒体卡片可控制同一练习任务；2.1.11 提供课程封面、任务时长和拖动定位。

Android 2.0 起使用 Kotlin + Jetpack Compose 原生界面和 Media3 播放器。课程来自静态 JSON，不加载远程程序代码。网页版独立保留。

2.1 默认从所点句子连续播放至结束，循环则留在当前组。紧凑课程列表、蓝色声波图标和 48dp 播放控制统一视觉与触控体验；耳机连接、静音及低 / 高系统音量只作短暂提示，不测量实际声压。

## 编译与签名

`.github/workflows/android.yml` 使用标准 Ubuntu 运行器编译，不需要电脑安装 Android SDK。正式版本标签触发编译与发布，也可在 Actions 手动运行验证构建。

APK 使用固定的私有签名密钥，密钥和密码通过 GitHub Secrets 注入，不提交仓库。后续版本可直接覆盖安装。Android versionCode 在版本文件中明确维护并严格递增。签名密钥本地备份位于工作区 `.local-signing/`（已忽略），请保留安全备份。

## 新增课程与部署

2026-10-04 新增 Booksmart：来自 `hLgMIwFeE88` 的 02:11:08 章节，约 17 秒原对白、7 句；默认 Drill 约 1 分 47 秒。课程通过云端目录同步，应用版本保持 2.1.11。[整理与验证记录](docs/booksmart-content.md)。

1. 将课程 JS、干净音频和原对白封面加入 `site/dist`，更新 `courses.js`。
2. 从完整对白母 WAV 导出连续音源，再生成单句与 Drill 切片；生成器复用 [连续音源制作流程](docs/continuous-audio.md)，不拼接切片还原原音。`python3 scripts/build_content.py` 生成课程包与 JSON 目录，`python3 scripts/check_content.py` 校验连续音源、时间轴与课程包。只有需要更新 APK 内置课程时才加 `--bundle-android`。包名含校验值，内容相同生成相同版本。
3. `wrangler pages deploy cloud/dist --project-name rachels-english --branch main` 发布免费静态课程服务。
4. 手机检查新课后下载；无须重新安装 APK。日常课程发布不会改动内置安卓资源，也不会触发安卓编译。

Cloudflare 使用 Pages 静态资源；不使用 R2、数据库或付费 Worker。发布脚本检查 20,000 文件与单文件 25 MiB 免费限制。云端内容公开可访问，GitHub 中不保存原始长视频、凭据或私有签名密钥。

可在安卓编译时用 `-PcontentBaseUrl=https://你的课程地址/` 替换服务地址。

## 课程自动发布

内容工作流独立检查课程包，不触发 APK 编译。本地已登录 Wrangler 可直接发布。若需要每次 GitHub 提交自动发布，另在仓库 Secrets 设置只限此账户的 `CLOUDFLARE_API_TOKEN`（Cloudflare Pages Edit），在 Secrets 设置 `CLOUDFLARE_ACCOUNT_ID`。未配置时自动部署步骤跳过，校验仍运行。不要把本地 OAuth 登录令牌当成永久部署密钥上传。

## 版本与发布

应用版本统一定义在 `release/version.properties`：版本名称采用主版本.次版本.修订版本，Android 版本代码每次发布严格递增。新增课程只更新内容目录，不改变应用版本。

每个版本在 `release/v版本号.md` 维护更新、兼容性、验证和限制说明。推送与版本名称一致的 `v版本号` 标签后，GitHub Actions 执行 Release 编译、Lint、签名验证并发布带版本号的 APK 与 SHA-256 文件。手动运行工作流只生成构建产物，不创建 Release；同一版本不重复发布。签名保持不变，可覆盖安装。

界面遵循共享元素的空间连续性及可打断的弹簧反馈，尊重减少动态效果。Android 自适应图标保持背景、前景和单色层；玻璃质感由绘制层实现，不依赖 Apple 平台的系统材质。

动画和交互以小米 15 / Android 16 的 120Hz 体验为性能目标，遵循[性能验收标准](docs/performance.md)。模拟器功能通过不代表真机稳定 120Hz；未测量的性能明确标记为未验证。

## Android 原生客户端（2.0 起）

Android 使用 Kotlin、Jetpack Compose 与 Media3，不再通过 WebView 展示界面。课程模型、下载校验和本地缓存独立于 UI；Cloudflare 内容格式保持兼容。界面使用 Compose 共享元素转场、弹簧反馈、可拖动设置面板及跟随进度的返回手势。

`python3 scripts/build_content.py --bundle-android` 会生成全部当前课程的内置 JSON、封面与音频，只有少量离线示例才适合内置；课程增加时保持按需下载，并核对 APK 大小预算。Android 不打包 HTML/JavaScript 界面。普通内容更新继续只需运行不带该选项的构建与部署。

GitHub 构建还运行复读队列测试及 Android 云端模拟器测试，检查课程打开、实际音频进度、模式、翻译开关和返回操作。开发电脑无需安装 Android SDK。
