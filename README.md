# LaptopRecommender

一个本地运行的笔记本电脑数据采集、筛选与对话式推荐系统。系统从 ZOL 笔记本排行榜和参数页采集数据，写入 MySQL；Spring Boot 提供查询、购物车与 DeepSeek 推荐接口；React 前端提供筛选、详情、购物车和推荐对话页面。

## 当前功能

- 采集 ZOL 榜单和允许访问的参数页，清洗为结构化笔记本规格。
- 初始化 MySQL 表结构，并生成或执行安全的在线更新 SQL。
- 按品牌、用途、CPU/GPU、价格、内存、硬盘、屏幕、重量等条件筛选和排序。
- 查看机型完整规格、最新采集价格和接口汇总。
- 使用 DeepSeek 的工具调用查询本地数据库，输出带机型详情的推荐与追问。
- 将筛选结果加入一个本地数据库级的购物车，支持批量删除和清空。

系统没有用户认证或多用户隔离；`shopping_cart` 是所有访问者共享的一份购物车。

## 仓库结构

```text
.
├─ crawler/                       # ZOL 采集、规格规范化、SQL 生成与在线更新
│  ├─ sources/zol.py              # 榜单/参数页解析及 robots.txt 检查
│  ├─ normalizer.py               # 规格字段清洗、单位和型号解析
│  ├─ sql_writer.py               # 普通 upsert 与安全在线更新 SQL 写入器
│  ├─ cli.py                      # 只采集并生成 JSON/SQL 的命令行入口
│  └─ online_update.py            # 可初始化数据库、生成并执行安全更新 SQL 的入口
├─ sql/schema.sql                 # 当前完整 MySQL 表结构，包含 shopping_cart
├─ laptop-rec-backend/            # Spring Boot + MyBatis-Plus 后端
│  ├─ src/main/java/              # Controller、Service、Mapper、DTO、VO 等
│  ├─ src/main/resources/mapper/  # 笔记本和购物车查询 SQL
│  ├─ application-local.example.yml
│  └─ .env.local.example
├─ laptop-rec-frontend/           # Vite + React + TypeScript 单页前端
│  └─ src/                        # 首页、筛选、详情弹窗、推荐和购物车界面
├─ tests/                         # 爬虫规范化及在线更新行为测试
├─ README_CRAWLER.md              # 爬虫模块补充说明
└─ README.md                      # 本文件
```

`data/crawl_output/`、前端 `node_modules/`/`dist/`、后端 `target/` 和本机密钥配置均为忽略文件，不会提交到 Git。

## 环境要求

- Java 17 或更高版本（项目编译目标为 Java 17）与 Maven
- Node.js 22+ 与 npm
- MySQL 8.x，并且 `mysql` 命令可在命令行中使用
- Python 3.11（本仓库可用环境：`~/.conda/envs/t/python.exe`）
- Python 包：`requests`；安装 `tqdm` 后采集时会显示进度条；运行爬虫测试还需要 `pytest`

Windows PowerShell 如遇到执行策略限制，请使用 `npm.cmd`，而不是 `npm`。

## 1. 配置数据库和 DeepSeek

数据库配置是后端与在线更新的必要条件；DeepSeek API Key 只在使用对话推荐时必需。

在后端目录创建本机配置：

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

也可在 `laptop-rec-backend/.env.local` 中使用以下同名环境变量：

```properties
DB_URL=jdbc:mysql://<db-host>:<db-port>/<db-name>?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
DB_USERNAME=<db-username>
DB_PASSWORD=<db-password>
DEEPSEEK_API_KEY=<deepseek-api-key>
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_MODEL=deepseek-v4-flash
```

后端从其当前工作目录加载 `application-local.yml` 或 `.env.local`；因此启动后端前应先进入 `laptop-rec-backend/`。

## 2. 初始化数据和后续更新

首次初始化时，在仓库根目录执行：

```powershell
& $env:USERPROFILE\.conda\envs\t\python.exe -B -m crawler.online_update --init-schema --execute
```

该命令会采集 ZOL 数据，创建配置中指定的数据库（如不存在），导入 `sql/schema.sql`，生成安全更新 SQL 并通过本机 `mysql` 客户端执行。`schema.sql` 已包含笔记本、规格、价格记录、端口、爬取日志和 `shopping_cart` 表。

后续更新价格和新增机型：

```powershell
& $env:USERPROFILE\.conda\envs\t\python.exe -B -m crawler.online_update --execute
```

默认的安全更新策略如下：

- 已存在机型不覆盖 CPU、GPU、内存、硬盘、屏幕、重量、颜色等已有规格。
- 已存在机型追加新的价格记录；接口查询时会按采集时间和记录 ID 取最新价格。
- 新机型才插入关联的规格和端口数据。
- 生成的更新 SQL 不包含 `DROP`、`TRUNCATE`、`ALTER` 或 `DELETE`。

若只想检查采集结果和 SQL，不写入数据库，去掉 `--execute`：

```powershell
& $env:USERPROFILE\.conda\envs\t\python.exe -B -m crawler.online_update --max-details 5
```

生成的原始数据、规范化 JSON 和 SQL 位于 `data/crawl_output/`。只生成普通 upsert SQL 可使用：

```powershell
& $env:USERPROFILE\.conda\envs\t\python.exe -B -m crawler.cli --delay 1.2
```

采集器会检查 ZOL 的 `robots.txt`，仅访问当前实现允许的榜单页和参数页；请保持合理的请求间隔。

## 3. 启动后端和前端

启动后端：

```powershell
cd laptop-rec-backend
mvn spring-boot:run
```

默认地址为 `http://localhost:8080`。若端口冲突，可临时设置：

```powershell
$env:SERVER_PORT="18080"
mvn spring-boot:run
```

启动前端（另开一个 PowerShell）：

```powershell
cd laptop-rec-frontend
npm.cmd ci
npm.cmd run dev
```

前端默认地址为 `http://127.0.0.1:5173/`。开发服务器将 `/api` 代理到 `http://localhost:8080`。后端使用其他端口时，需同步调整 `laptop-rec-frontend/vite.config.ts` 的代理目标；构建部署版本时，也可设置：

```powershell
$env:VITE_API_BASE_URL="http://localhost:18080"
npm.cmd run build
```

前端页面路由为：

- `/`：功能入口
- `/filter`：条件筛选、详情查看和加入购物车
- `/recommend`：DeepSeek 对话推荐
- `/cart`：购物车查看、批量删除和清空

## API 概览

所有接口均使用统一响应包装：

```json
{
  "success": true,
  "code": 200,
  "message": "ok",
  "data": {}
}
```

### 笔记本查询

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| `GET` | `/api/laptops/options` | 返回数据库中的筛选选项和价格/重量范围 |
| `GET` | `/api/laptops` | 分页筛选与排序 |
| `GET` | `/api/laptops/{id}` | 返回完整机型规格 |

列表接口支持参数：

```text
keyword, brand, productType, usageKeyword, cpuKeyword, gpuKeyword, gpuType,
minPrice, maxPrice, minMemoryGb, minStorageGb, minScreenSize, maxWeightKg,
sort, page, size
```

`sort` 可选值为 `latest`、`priceAsc`、`priceDesc`、`weightAsc`、`screenDesc`。`page` 从 1 开始，`size` 默认 20、最大 100。

示例：

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/laptops?brand=联想&minPrice=5000&maxPrice=12000&minMemoryGb=16&size=5"
```

### 对话推荐

```http
POST /api/recommend/chat
Content-Type: application/json
```

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

后端让模型只能调用受控工具 `search_laptops` 和 `get_laptop_detail` 检索本地数据库，最多进行有限轮工具调用。最终响应包含 `reply`、`recommendations`（每项含 `laptopId`、`reason`、`detail`）和 `followUpQuestions`；推荐机型必须能取得对应详情。未配置 API Key 时，该接口会明确返回配置错误。

### 购物车

| 方法 | 路径 | 请求体 |
| --- | --- | --- |
| `GET` | `/api/cart` | 无 |
| `POST` | `/api/cart/items` | `{ "laptopId": 1 }` |
| `DELETE` | `/api/cart/items` | `{ "laptopIds": [1, 2, 3] }` |
| `DELETE` | `/api/cart` | 无 |

购物车仅保存 `laptop_id`，展示时重新关联机型和最新价格，因此价格与规格会随数据库数据更新。

## 构建与测试

后端：

```powershell
cd laptop-rec-backend
mvn -q test
```

前端：

```powershell
cd laptop-rec-frontend
npm.cmd run build
```

爬虫：

```powershell
& $env:USERPROFILE\.conda\envs\t\python.exe -B -m pytest -q
```

本仓库的单元测试不需要已启动的 MySQL、DeepSeek 或外部爬虫请求；运行服务和实际采集则需要完成相应配置。
