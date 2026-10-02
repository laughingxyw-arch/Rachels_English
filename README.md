# 原声练习 · Rachel’s English

极简原声跟读。课程封面来自原对白，点句子听完整原声，Drill 分段和整句各三遍；提供留白跟读、循环、默认关闭的中文翻译。

在线网页：https://rachels-english.pages.dev/

## 安卓安装

可直接下载 https://rachels-english.pages.dev/rachels-english.apk ，GitHub 的 **Releases** 页面也提供 `rachels-english.apk` 预览包。允许手机浏览器安装此来源的应用后安装。支持 Android 8+，目标平台 Android 16。

界面与首批课程内置 APK。启动先显示本地课程，后台同步云端课程列表；首页右上角可手动检查更新。点击新增或修订课程后下载并校验课程包，成功后安装到私有目录。断网仍可学习已下载课程。离开 App 暂停播放，第一版不包含后台音频服务。

首版是本地界面加安卓容器，课程来自静态 JSON，不加载远程程序代码。借此保留已完成的播放器交互，后续可逐步替换成原生界面。

## 编译与签名

`.github/workflows/android.yml` 使用标准 Ubuntu 运行器编译，不需要电脑安装 Android SDK。仅安卓文件变更触发编译，也可在 Actions 手动运行。

APK 使用固定的私有签名密钥，密钥和密码通过 GitHub Secrets 注入，不提交仓库。后续版本可直接覆盖安装。构建编号作为递增的 Android versionCode。签名密钥本地备份位于工作区 `.local-signing/`（已忽略），请保留安全备份。

## 新增课程与部署

1. 将课程 JS、干净音频和原对白封面加入 `site/dist`，更新 `courses.js`。
2. `python3 scripts/build_content.py` 生成课程包与 JSON 目录。只有需要更新 APK 内置课程时才加 `--bundle-android`。包名含校验值，内容相同生成相同版本。
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
