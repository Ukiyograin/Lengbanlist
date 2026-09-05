# Lengbanlist 重构任务清单

## 进度概览
| # | 任务 | 状态 |
|---|---|---|
| A2 | 移除测试死断言 | ✅ 已完成 |
| A4 | POJO 转 Java 17 record | 🔄 进行中 |
| A1 | 测试覆盖率补全 | ⏳ 待办 |
| A3 | 公共 API 暴露 | ⏳ 待办 |
| A5 | 依赖升级（HikariCP/Gson/mysql-connector） | ⏳ 待办 |
| A6 | HttpURLConnection → java.net.http.HttpClient | ⏳ 待办 |
| A7 | 数据库 schema 版本化 | ⏳ 待办 |
| A8 | 占位符扩展 | ⏳ 待办 |
| A9 | Web 管理面板增强 | ⏳ 待办 |
| D1 | WebServer 拆分成 controllers | ⏳ 待办 |
| D2 | Folia 真原生支持 | ⏳ 待办 |
| D3 | 异步 PlayerProfile 查询 | ⏳ 待办 |
| Theme | Web 主题（背景+按钮显隐） | ⏳ 待办 |

## 执行策略
- 用户授权：批量写代码，最后统一测试
- 每个任务单独 commit，遵循仓库 `fix:` / `feat:` 规范
- A4 → A3 → A1 优先（基础重构 → API → 测试保护）
- D1 → D2 → D3 次之（Web 现代化）
- A5 → A6 → A7 → A8 → A9 最后（依赖/HTTP/Schema/Placeholder/Web增强）
- Theme 在 D1 之后做（需要新的 ThemeController）

## 关联文档
- `.github/workflows/maven-ci.yml` 已建立 CI 守卫
- pom.xml Java target 1.8 → 17 已升
- `Lengbanlist.java:219` getVers typo 已修