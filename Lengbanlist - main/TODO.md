# Lengbanlist 重构任务清单

## 进度概览（13/13 已完成 ✅）
| # | 任务 | 状态 |
|---|---|---|
| A1 | 测试覆盖率补全 | ✅ 已完成 |
| A2 | 移除测试死断言 | ✅ 已完成 |
| A3 | 公共 API 暴露 | ✅ 已完成 |
| A4 | POJO 转 Java 17 record | ✅ 已完成 |
| A5 | 依赖升级（HikariCP/Gson/mysql-connector） | ✅ 已完成 |
| A6 | HttpURLConnection → java.net.http.HttpClient | ✅ 已完成 |
| A7 | 数据库 schema 版本化 | ✅ 已完成 |
| A8 | 占位符扩展 | ✅ 已完成 |
| A9 | Web 管理面板增强 | ✅ 已完成 |
| D1 | WebServer 拆分成 controllers | ✅ 已完成 |
| D2 | Folia 真原生支持 | ✅ 已完成 |
| D3 | 异步 PlayerProfile 查询 | ✅ 已完成 |
| Theme | Web 主题（背景+按钮显隐） | ✅ 已完成 |

## Web 管理面板 API（11 个 controller / 25 个端点）

| Controller | 端点 |
|---|---|
| AuthController | POST /api/login、POST /api/logout |
| BanController | POST /api/ban、POST /api/unban、GET /api/bans、GET /api/ipbans |
| MuteController | POST /api/mute、POST /api/unmute、GET /api/mutes |
| WarnController | POST /api/warn |
| PlayerController | GET /api/players、GET /api/online、POST /api/kick、GET /api/history |
| AuditController | GET /api/audit?actor=&action=&limit= |
| ReportController | GET /api/reports、POST /api/report/action |
| ThemeController | GET/POST /api/theme、POST /api/theme/upload、GET /api/theme/file/<filename> |
| AdminController | POST /api/reload、POST /api/broadcast |
| ExportController | GET /api/exports/{bans,ipbans,mutes}.csv |
| WebServer | GET / (静态资源) |

## 关联文档
- `.github/workflows/maven-ci.yml` 已建立 CI 守卫
- pom.xml Java target 1.8 → 17 已升
- `Lengbanlist.java:219` getVers typo 已修

---

## 下一阶段：可执行任务（基于代码现状识别）

> 来源说明：每项任务标注了查证路径（文件:行），方便确认必要性。

### E1 命令层拆分——`LengbanlistCommand` 高复杂度治理
**问题**：LengbanlistCommand.java 共 **1605 行**，其中 `execute()` 用单一 switch-case 处理 **27 个子命令**（toggle / a / list / reload / add / remove / help / open / getip / model / mute / unmute / list-mute / warn / unwarn / report / admin / check / info / tp / history / audit / handle / alts / sync / rollback）。

**目标**：参考 D1 WebServer controller 拆分的思路，落地 `SubCommand` 接口 + 独立 handler 类。
- 新增 `commands/sub/` 目录，每个子命令一个类（如 `BanSubCommand.java` `MuteSubCommand.java`）
- 顶层 `LengbanlistCommand` 收缩为路由器（注册表 + 分发）
- Tab 补全逻辑随各 handler 各自实现
- 预计 `LengbanlistCommand.java` 1605 行 → ~300 行（仅留分发器 + GUI）

### E2 `getIPLocation` 不一致——A6 收尾遗漏
**问题**（证据）：
- `GetIPCommand.java:89` 已用 `HttpHelper` 调用 `ip-api.com` ✅
- `LengbanlistCommand.java:1150` 仍然用 **HttpURLConnection** 调用 `ipapi.co` ❌

**目标**：统一为 `HttpHelper` 调用，统一 IP 归属地数据源，并把工具方法下沉到 `utils/IpGeoLookup.java`。

### E3 5 处遗留 `printStackTrace`
**位置**（grep 已确认）：
- `Lengbanlist.java:110` — 数据库初始化失败
- `Lengbanlist.java:533` — `saveBroadcastConfig()` IOException
- `ModelManager.java:110`
- `utils/AutoUpdateManager.java:73`
- `utils/GitHubUpdateChecker.java:146`

**目标**：替换为 `plugin.getLogger().warning(...)` 或 `.log(Level.WARNING, ...)`，并在 init/load 类路径加 stack 完整 stack。

### E4 `Lengbanlist` 主类瘦身（555 行）
**问题**：onEnable 单一方法包含 20+ 项初始化（数据库迁移、各 Manager、WebServer、定时任务、命令注册…）。

**目标**：
- 抽出 `initManagers()` / `initListeners()` / `initTasks()` / `initCommands()` 私有方法
- 公开字段（`public BanManager banManager` 等 8 处）改为 `private` + getter——已有 getter，字段可私有化

### E5 测试覆盖继续扩展
**现状**：9 个测试文件，集中在 record/utils/manager 单点。

**目标**（按价值排序）：
- `BanManagerTest` — 封禁/解封/查询主链路（含 Folia 模拟场景）
- `RollbackManagerTest` — 回滚撤销与回滚重做
- `AuditManagerTest` — 哈希链校验 + 导出过滤
- `IpAssociationManagerTest` — 同 IP 提醒去重
- `ReportManagerTest` — 举报受理状态机

### E6 `DatabaseManager` 按职责拆分（1254 行）
**问题**：单文件持有 player_ips / bans / ipbans / mutes / warnings / audits / reports / schema-migration 所有 DAO 方法。

**目标**：参考 A7 schema 版本化思路，拆出 `dao/` 子包：
- `BanDao` / `IpBanDao` / `MuteDao` / `WarnDao` / `AuditDao` / `ReportDao` / `PlayerIpDao`
- `DatabaseManager` 收敛为连接池 + 事务协调
- 配套 SchemaMigrations 独立保留

### E7 Web 前端模块化
**现状**：`resources/web/index.html` **1179 行 / 56 KB** 单文件。

**目标**：
- CSS 拆出 `web/css/app.css` `web/css/themes.css`
- JS 拆出 `web/js/api.js` `web/js/auth.js` `web/js/ban.js` `web/js/mute.js` …按 controller 对齐
- 引入简单的 build-less 模块加载（`<script type="module">`）

### E8 HikariCP 连接池观测
**现状**：A5 已升级到 5.1，但缺 metrics 上报。

**目标**：
- 定时任务（60s）记录 active/idle/total 连接数到 debug 日志
- `AdminController` 或 `/api/info` 暴露 pool 指标
- 可选：bStats 自定义图表

---

## 候选（视需要采纳）

### C1 命令别名规范化
部分子命令在 plugin.yml 里有独立注册（`/ban` `/mute` `/unban` …），`/lban` 子命令又是另一套入口。两套同时存在容易让用户迷惑。
- 现状：plugin.yml 注册了 19 个独立命令 + `/lban` 聚合
- 选项：保留两套（兼容）/ 收敛为单 `/lban` 入口 / 自动注册 `/lban <sub>` 别名

### C2 举报 Web 受理
`ReportController` 已有 `POST /api/report/action`，但只支持 accept/close，没有 view 详情接口。

### C3 玩家面板（Web）自助查询
当前 `/api/players` 列表 + `/api/history` 历史查询已可用，但缺：
- 玩家本人查自己当前的封禁/禁言/警告状态
- 玩家本人提申诉入口

---

## 优先级建议

| 任务 | 工作量 | 影响 | 推荐顺序 |
|---|---|---|---|
| E2 getIPLocation 一致 | 🟢 小 | 中 | ⭐ 第 1 |
| E3 printStackTrace 清理 | 🟢 小 | 低（代码卫生） | ⭐ 第 2 |
| E1 LengbanlistCommand 拆分 | 🟡 中 | 高 | ⭐ 第 3 |
| E4 Lengbanlist 主类瘦身 | 🟡 中 | 中 | 第 4 |
| E5 测试扩展 | 🟡 中 | 高 | ⭐ 第 5 |
| E6 DatabaseManager 拆分 | 🔴 大 | 中 | 第 6 |
| E7 Web 前端模块化 | 🟡 中 | 中 | 第 7 |
| E8 HikariCP 观测 | 🟢 小 | 低 | 第 8 |