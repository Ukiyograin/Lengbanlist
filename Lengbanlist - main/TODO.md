# Lengbanlist 重构任务清单

## 进度概览
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