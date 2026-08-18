# 前后端分离部署指南（Cloudflare Pages 前端 + Railway 后端）

架构：

```
浏览器 ──► Cloudflare Pages（前端静态站，含 config.js）
        └─► Railway（Spring Boot 后端 + MySQL）
                └─► Cloudflare R2（上传文件/图片/附件）
```

- 前端（公众端 7 个页面）部署到 Cloudflare Pages，免费、自带 HTTPS/CDN
- 管理后台（admin/index.html）仍随后端 jar 一起部署在 Railway 上，走同源，无需改动
- 上传的文件存到 Cloudflare R2，避免 Railway 临时文件系统重启丢数据

---

## 一、准备：需要的账户与参数

| 项 | 说明 |
|---|---|
| Cloudflare 账号 | https://dash.cloudflare.com/sign-up（免费） |
| Railway 账号 | https://railway.app 用 GitHub 登录（$5 试用额度，长期需 Hobby 计划） |
| 域名（可选） | 前端可绑自定义域名；不绑就用 `xxx.pages.dev` |

---

## 二、前端部署（Cloudflare Pages）

1. 打开 `frontend/js/config.js`，把 `API_BASE` 改成 Railway 后端地址（先部署后端拿到地址再回填，或先用临时值）：
   ```js
   window.APP_CONFIG = { API_BASE: 'https://你的项目.up.railway.app' };
   ```
2. Cloudflare 控制台 → **Workers & Pages** → **Create** → **Pages** → **Upload assets**（Direct Upload，无需 git）
3. 项目名自定义 → 把 `frontend/` 整个目录拖进去 → Deploy
4. 部署完成后得到 `https://项目名.pages.dev`，访问确认首页能打开

> 后续每次改前端：改完 `config.js` 或页面 → 重新 Upload assets 覆盖即可。

---

## 三、对象存储（Cloudflare R2）

1. Cloudflare 控制台 → **R2** → **Create bucket**，名字如 `thz-uploads`
2. 记下你的 **Account ID**（在 R2 页面右侧）
3. 开启公开访问：进入 bucket → **Settings** → **R2.dev subdomain** → **Allow Access**（开启后得到公网访问域名，形如 `https://pub-xxxxxxxx.r2.dev`）
4. 生成 API Token：R2 → **Manage R2 API Tokens** → **Create API Token**，权限选「对象读写」，记下 **Access Key ID** 和 **Secret Access Key**（Secret 只显示一次）

之后用到的参数：

| 环境变量 | 值 |
|---|---|
| R2_ENDPOINT | `https://<ACCOUNT_ID>.r2.cloudflarestorage.com` |
| R2_ACCESS_KEY_ID | 上一步的 Access Key ID |
| R2_SECRET_ACCESS_KEY | 上一步的 Secret Access Key |
| R2_BUCKET | `thz-uploads` |
| R2_PUBLIC_BASE_URL | `https://pub-xxxxxxxx.r2.dev` |

---

## 四、后端部署（Railway）

1. 把项目推到一个 GitHub 仓库（Railway 默认从 GitHub 导入；根目录已放好 `Dockerfile` + `railway.json`）
2. Railway → **New Project** → **Deploy from GitHub repo** → 选你的仓库
3. 在项目里 **Add Database** → 选 **MySQL**（会创建一个独立的 MySQL 服务）
4. **把 MySQL 的连接变量引用到后端服务**（关键步骤，漏了会启动失败报 `Failed to parse the host:port pair '${MYSQLHOST}:${MYSQLPORT}'`）：
   进入你的后端 Service → **Variables** → **New Variable**，逐个添加并引用 MySQL 服务：

   | 变量 | 引用值 |
   |---|---|
   | MYSQLHOST | `${{MySQL.MYSQLHOST}}` |
   | MYSQLPORT | `${{MySQL.MYSQLPORT}}` |
   | MYSQLUSER | `${{MySQL.MYSQLUSER}}` |
   | MYSQLPASSWORD | `${{MySQL.MYSQLPASSWORD}}` |
   | MYSQLDATABASE | `${{MySQL.MYSQLDATABASE}}` |

   > 上面的 `MySQL` 是你数据库服务的名字；Railway 的 Variables 输入框里可直接点「引用」选择对应变量，不必手敲。
   > `PORT` 由 Railway 自动注入，无需手动设置。

5. 继续在同一 Variables 页面添加其余变量：

   | 变量 | 值 |
   |---|---|
   | JWT_SECRET | 随机强密钥（`openssl rand -base64 48`） |
   | APP_ADMIN_PASSWORD | 首次启动自动创建 admin 的初始密码 |
   | CORS_ORIGINS | 前端地址，如 `https://项目名.pages.dev` |
   | STORAGE_TYPE | `s3` |
   | R2_ENDPOINT / R2_ACCESS_KEY_ID / R2_SECRET_ACCESS_KEY / R2_BUCKET / R2_PUBLIC_BASE_URL | 见第三节 |

6. **初始化数据库**：Railway MySQL 卡片 → 打开其 **Variables** 里的 `MYSQLURL`/连接信息 → 用数据库客户端或 Railway 的 **Connect** 连进去，执行建表语句：
   ```sql
   -- 用 data/schema.sql，但去掉开头的 CREATE DATABASE / USE 两行
   ```
   简化做法：在本地执行
   `sed '/CREATE DATABASE/d; /^USE /d' data/schema.sql > schema-cloud.sql`
   再把 `schema-cloud.sql` 的内容粘到 Railway 的 SQL 连接里执行。

6. 部署完成后，Railway 会给出域名 `https://xxx.up.railway.app`，这就是后端地址。

---

## 五、联调

1. 把第二节里 `frontend/js/config.js` 的 `API_BASE` 改成 Railway 实际域名，重新 Upload assets
2. 打开 `https://项目名.pages.dev`：
   - 首页产品码查询（如 `001`）能出结果 → 说明 CORS + API 打通
   - 「数据上传」→ admin/admin（或你设的 APP_ADMIN_PASSWORD）→ 能进管理后台
   - 后台传一张图谱 → 图片能在结果页显示 → 说明 R2 存储打通（图片地址应是 `https://pub-...r2.dev/...`）

---

## 六、验收

后端跑通后，用自动化脚本做一轮验收（需要 python + curl，脚本会创建并清理临时数据）：

```bash
bash scripts/smoke-test.sh https://xxx.up.railway.app
```

重点确认：公开接口 200、登录鉴权、越权拦截、上传→预览→下载、反馈限流、错误返回 JSON、健康检查 `/api/health` 返回 UP。

---

## 七、常见问题

| 现象 | 处理 |
|---|---|
| 前端请求报 CORS 错误 | 确认 Railway 的 `CORS_ORIGINS` 填了 Pages 域名（不含结尾斜杠） |
| 上传后图片 404 / 不显示 | 检查 R2 bucket 是否开启了 r2.dev 公开访问、`R2_PUBLIC_BASE_URL` 是否正确 |
| 后端启动失败"members 表为空且未配置初始密码" | 已设置 `APP_ADMIN_PASSWORD` 后重新部署 |
| 数据库表不存在 | 第四节第 5 步的建表 SQL 没执行，补执行 |
| 上传超 10MB 报 413 | 默认单文件 10MB，可在 `application.properties` 调整 `spring.servlet.multipart.max-file-size` |
| 后端日志查看 | Railway 服务详情 → Deployments → 查看日志 |

---

## 八、成本

- Cloudflare Pages + R2：免费额度内不花钱
- Railway：$5 试用额度；MySQL 插件 + 长期运行一般需 Hobby 计划（$5/月），需绑卡
