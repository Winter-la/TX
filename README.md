# TX — 驻马店区域内中药材质量安全追溯体系信息平台

基于太赫兹（THz）光谱技术的中药材质量安全追溯系统（黄淮学院项目），支持产品追溯码查询、光谱图谱展示与数据下载、内容发布、追溯流向、意见反馈等功能。

## 技术栈

- 后端：Spring Boot 3.2 + MySQL + Spring Data JPA + JWT + BCrypt
- 前端：原生 HTML/CSS/JS + jQuery + Chart.js（纯静态）
- 存储：本地磁盘 / Cloudflare R2（S3 兼容）

## 快速开始（本地）

1. 建库：`mysql -u root -p < data/schema.sql`（可选 `data/migrate.sql` 导入演示数据）
2. 修改 `backend/src/main/resources/application.properties` 中的数据库密码
3. 同步前端到后端：运行项目根目录的 `sync.bat`
4. 启动：`cd backend && mvn spring-boot:run`，访问 http://localhost:8080
5. 默认管理员：`admin / admin`（登录后请在「会员管理 → 修改密码」中立即修改）

## 部署

- 单 jar 部署：见 [部署说明.md](部署说明.md)
- 前后端分离（Cloudflare Pages + Railway + R2）：见 [部署-Cloudflare-Railway.md](部署-Cloudflare-Railway.md)

## 测试

```bash
bash scripts/smoke-test.sh http://localhost:8080
```

覆盖全部页面、公开接口、登录与 RBAC、产品/图谱上传、光谱文件管理、内容发布、追溯录入、意见反馈限流、错误处理与安全响应头。
