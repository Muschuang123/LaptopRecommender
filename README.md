# 笔记本电脑推荐系统

本项目是一个本地运行的笔记本推荐系统：

- `crawler/`：爬取并规范化笔记本数据，生成 SQL。
- `sql/`、`data/crawl_output/`：数据库表结构和爬虫输出。
- `laptop-rec-backend/`：Spring Boot 后端，提供列表筛选、详情和 DeepSeek 推荐接口。
- `laptop-rec-frontend/`：Vite React 前端，提供首页、条件筛选页和推荐聊天页。

## 环境要求

- Java 17+
- Maven
- Node.js 22+
- MySQL 8.x

Windows PowerShell 如果无法运行 `npm`，使用 `npm.cmd`。

## 1. 配置本地密钥和数据库

真实数据库信息和 API Key 不提交到 Git。推荐复制后端本地配置示例：

```powershell
cd laptop-rec-backend
Copy-Item application-local.example.yml application-local.yml
```

编辑 `laptop-rec-backend/application-local.yml`：

```yaml
spring:
  datasource:
    url: "jdbc:mysql://<db-host>:<db-port>/<db-name>?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
    username: "<db-username>"
    password: "<db-password>"

deepseek:
  api-key: "<deepseek-api-key>"
  base-url: "https://api.deepseek.com"
  model: "deepseek-v4-flash"
```

`application-local.yml` 已被 `.gitignore` 忽略。也可以使用 `laptop-rec-backend/.env.local`：

```properties
DB_URL=jdbc:mysql://<db-host>:<db-port>/<db-name>?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
DB_USERNAME=<db-username>
DB_PASSWORD=<db-password>
DEEPSEEK_API_KEY=<deepseek-api-key>
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_MODEL=deepseek-v4-flash
```

后端会从当前工作目录读取：

```yaml
optional:file:./application-local.yml
optional:file:./.env.local[.properties]
```

所以启动后端前请先进入 `laptop-rec-backend/`。

## 2. 初始化数据库和数据

完成 `laptop-rec-backend/application-local.yml` 后，回到项目根目录执行：

```powershell
& $env:USERPROFILE\.conda\envs\t\python.exe -B -m crawler.online_update --init-schema --execute
```

该命令会读取 `application-local.yml`，自动创建数据库、导入 `sql/schema.sql`，爬取 ZOL 笔记本排行榜并写入初始数据。

## 3. 在线更新笔记本数据

后续更新数据时继续在项目根目录执行：

```powershell
& $env:USERPROFILE\.conda\envs\t\python.exe -B -m crawler.online_update --execute
```

该命令的写库策略：

- 已存在的机型：不覆盖 CPU、GPU、内存、硬盘、屏幕、重量、颜色等属性。
- 已存在的机型：只追加一条新的价格记录。
- 新出现的机型：插入规格、机型、接口和第一条价格。
- 不执行 `DROP`、`TRUNCATE`、`ALTER`、`DELETE`。

如果只想爬取并生成安全 SQL，不立刻写入数据库，去掉 `--execute`：

```powershell
& $env:USERPROFILE\.conda\envs\t\python.exe -B -m crawler.online_update
```

生成文件会写入 `data/crawl_output/`，其中 `laptops_safe_online_update_*.sql` 是安全更新 SQL。
调试时可使用 `--max-details N` 限制本次最多抓取的参数页数量。

## 4. 启动服务

启动后端：

```powershell
cd laptop-rec-backend
mvn spring-boot:run
```

后端默认地址：

```text
http://localhost:8080
```

验证后端：

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/laptops?size=3"
Invoke-RestMethod -Uri "http://localhost:8080/api/laptops/options"
Invoke-RestMethod -Uri "http://localhost:8080/api/laptops/1"
```

启动前端，另开一个 PowerShell：

```powershell
cd laptop-rec-frontend
npm.cmd install
npm.cmd run dev
```

前端默认地址：

```text
http://127.0.0.1:5173/
```

Vite 会把 `/api` 代理到 `http://localhost:8080`，所以本地使用时需要同时启动前端和后端。

如果 8080 被占用：

```powershell
$env:SERVER_PORT="18080"
mvn spring-boot:run
```

此时也要同步调整前端代理配置或接口地址。

前端默认通过 Vite 代理访问后端：

```text
laptop-rec-frontend/vite.config.ts -> /api -> http://localhost:8080
```

如果后端不在 8080 端口，开发模式可修改 `vite.config.ts` 的 `target`；生产部署可在前端构建前设置接口前缀：

```powershell
cd laptop-rec-frontend
$env:VITE_API_BASE_URL="http://localhost:18080"
npm.cmd run build
```

## 5. 功能和接口

首页包含两个入口：

- `按条件筛选`
- `DeepSeek推荐`

### 条件筛选

筛选页先读取数据库中的可选项：

```http
GET /api/laptops/options
```

返回品牌、产品类型、用途定位、内存容量、硬盘容量、屏幕尺寸、GPU 类型、价格范围和重量范围。

筛选列表接口：

```http
GET /api/laptops
```

支持参数：

```text
keyword, brand, productType, usageKeyword, cpuKeyword, gpuKeyword, gpuType,
minPrice, maxPrice, minMemoryGb, minStorageGb, minScreenSize, maxWeightKg,
sort, page, size
```

`sort` 支持：

```text
latest, priceAsc, priceDesc, weightAsc, screenDesc
```

详情接口：

```http
GET /api/laptops/{id}
```

### DeepSeek 推荐

推荐页调用：

```http
POST /api/recommend/chat
```

请求示例：

```json
{
  "messages": [
    {
      "role": "user",
      "content": "预算 7000，主要写代码和轻度游戏，希望轻一点"
    }
  ]
}
```

后端使用白名单 Tool Loop：

- `search_laptops`：按安全条件查询数据库。
- `get_laptop_detail`：按 id 查询详情。

每次对话开启时使用的 Prompt 写死在：

```text
laptop-rec-backend\src\main\java\com\example\laptoprec\service\impl\RecommendServiceImpl.java
```

DeepSeek 不能直接执行 SQL，只能通过后端允许的工具查询数据库。

## 6. 构建和检查

后端测试：

```powershell
cd laptop-rec-backend
mvn -q test
```

前端生产构建：

```powershell
cd laptop-rec-frontend
npm.cmd run build
```

前端构建产物在 `laptop-rec-frontend/dist/`，该目录不会提交到 Git。

爬虫规范化测试：

```powershell
& $env:USERPROFILE\.conda\envs\t\python.exe -m pytest -q
```

## 7. 常见问题

前端没有数据：

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/laptops?size=1"
mysql -u<db-username> -p -P<db-port> -h<db-host> -D <db-name> -e "SELECT COUNT(*) FROM laptop;"
```

DeepSeek 推荐提示未配置 API Key：填写 `laptop-rec-backend/application-local.yml` 或 `laptop-rec-backend/.env.local`，然后重启后端。

PowerShell 中文显示乱码：通常只是终端编码问题，浏览器和接口 JSON 使用 UTF-8。

`npm.ps1 cannot be loaded`：改用 `npm.cmd install` 和 `npm.cmd run dev`。
