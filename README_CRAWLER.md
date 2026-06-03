# 笔记本数据爬虫模块

该模块抓取 ZOL 移动端笔记本排行榜目录，并根据排行榜链接推导参数页地址，再从参数页提取结构化规格。

当前使用的数据源：

- 排行榜目录 U0：`https://wap.zol.com.cn/top/notebook/`
- 品牌排行榜目录 U1：`https://wap.zol.com.cn/top/notebook/brand/`
- 参数页：形如 `https://detail.zol.com.cn/2166/2165921/param.shtml`

不会访问 `https://detail.zol.com.cn/notebook/index*.shtml` 这类被 robots.txt 禁止的详情页。

## 运行环境

```powershell
& $env:USERPROFILE\.conda\envs\t\python.exe -m pip show requests
```

当前实现需要 `requests`；如果环境中安装了 `tqdm`，抓取参数页时会显示进度条。没有 `tqdm` 时会自动退回到简单文本进度。

## 初始化 MySQL 表

先在目标库执行一次：

```sql
SOURCE sql/schema.sql;
```

## 抓取 ZOL 数据并生成 SQL

```powershell
& $env:USERPROFILE\.conda\envs\t\python.exe -B -m crawler.cli --delay 1.2
```

输出会写入 `data/crawl_output/`：

- `laptops_raw_*.json`：原始抓取结果。
- `laptops_normalized_*.json`：清洗后的结构化结果。
- `laptops_upsert_*.sql`：可导入 MySQL 的 upsert SQL。

## 在线安全更新数据库

如果要“爬取 + 更新数据库”，使用安全在线更新命令：

```powershell
& $env:USERPROFILE\.conda\envs\t\python.exe -B -m crawler.online_update --delay 1.2 --execute
```

默认读取 `laptop-rec-backend/application-local.yml` 中的 `spring.datasource` 配置。

该命令会生成并执行 `laptops_safe_online_update_*.sql`：

- 已存在的 `laptop`：不更新 CPU、GPU、内存、硬盘、屏幕、重量、颜色等属性。
- 已存在的 `laptop`：只追加一条新的 `price_record`。
- 新出现的 `laptop`：插入规格、机型、端口和第一条价格。
- 不会生成 `DROP`、`TRUNCATE`、`ALTER`、`DELETE`。

如果只想生成安全 SQL、暂不导入数据库，去掉 `--execute`。

## 数据源策略

- ZOL 笔记本排行榜目录：`https://wap.zol.com.cn/top/notebook/`
- ZOL 品牌排行榜目录：`https://wap.zol.com.cn/top/notebook/brand/`
- ZOL 参数页：从排行榜链接 `/2166/2165921/index.html` 推导为 `https://detail.zol.com.cn/2166/2165921/param.shtml`
- 运行时会检查对应域名的 `robots.txt`；如果目标 URL 被禁止，会直接中止。

默认请求间隔为 1.2 秒；批量抓取时不要调得过低。
调试时可使用 `--max-details N` 限制本次最多抓取的参数页数量。
