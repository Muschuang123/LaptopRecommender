# Spring Boot 后端设计说明

这个子项目是笔记本推荐系统的第一版后端，目标很明确：把 MySQL 里的笔记本数据稳定地通过 HTTP API 查出来，为后续推荐算法和前端页面提供基础数据接口。

当前阶段只做只读查询，不在后端里写入爬虫数据。数据写入仍由根目录下的 `crawler/` 生成 SQL，再导入 MySQL。

## 技术栈

- Java 17 编译目标
- Spring Boot 3.5.0
- MyBatis-Plus 3.5.16
- MySQL Connector/J
- Maven

本机实际验证时使用的是 Java 21 运行，但 `pom.xml` 中设置的编译目标是 Java 17，因此 Java 17 及以上都可以运行。

## 目录结构

```text
laptop-rec-backend/
  pom.xml
  src/main/java/com/example/laptoprec/
    LaptopRecBackendApplication.java
    common/
      Result.java
      PageResult.java
      GlobalExceptionHandler.java
    config/
      CorsConfig.java
    controller/
      LaptopController.java
    dto/
      LaptopQueryDTO.java
    entity/
      Laptop.java
    mapper/
      LaptopMapper.java
    service/
      LaptopService.java
      impl/LaptopServiceImpl.java
    vo/
      LaptopListItemVO.java
      LaptopDetailVO.java
  src/main/resources/
    application.yml
    mapper/LaptopMapper.xml
```

## 分层设计

### controller

`LaptopController` 对外暴露 HTTP 接口：

- `GET /api/laptops`
- `GET /api/laptops/{id}`

Controller 只负责接收参数、调用 service、包装统一返回结构，不直接写 SQL。

### dto

`LaptopQueryDTO` 表示列表查询条件，包括分页和筛选字段。它内部有 `normalize()` 方法，用来处理：

- `page` 小于 1 时自动变成 1
- `size` 小于 1 时自动变成 20
- `size` 最大限制为 100
- 空字符串筛选条件自动转成 `null`
- 计算 MySQL `LIMIT offset, size` 需要的 `offset`

### service

`LaptopService` 定义业务接口，`LaptopServiceImpl` 实现实际逻辑：

- 列表查询先统计总数，再查询当前页数据
- 详情查询会校验 id，查不到时抛出明确异常

### mapper

`LaptopMapper` 是 MyBatis Mapper 接口，复杂查询写在 `LaptopMapper.xml` 里。这里没有直接使用 MyBatis-Plus 的自动 CRUD，因为列表接口需要连接多张规格表，并且要取最新价格记录。

### vo

VO 是返回给前端的数据结构：

- `LaptopListItemVO`：列表页需要的简要字段
- `LaptopDetailVO`：详情页需要的完整字段

### common

`Result<T>` 是统一响应格式：

```json
{
  "success": true,
  "code": 200,
  "message": "ok",
  "data": {}
}
```

`PageResult<T>` 是分页数据格式：

```json
{
  "total": 50,
  "page": 1,
  "size": 20,
  "records": []
}
```

`GlobalExceptionHandler` 统一处理参数错误和未捕获异常，避免接口直接返回 Spring Boot 默认错误页。

## 数据库连接

默认连接配置在 `src/main/resources/application.yml`：

```yaml
server:
  port: ${SERVER_PORT:8080}

spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

默认值匹配当前项目环境：

- 数据库：`laptoprecommender`
- 端口：`3306`
- 用户：`root`
- 密码：`123456`

如果之后换机器或换密码，不需要改代码，可以用环境变量覆盖：

```powershell
$env:DB_HOST="localhost"
$env:DB_PORT="3306"
$env:DB_NAME="laptoprecommender"
$env:DB_USERNAME="<db-username>"
$env:DB_PASSWORD="<db-password>"
$env:SERVER_PORT="8080"
```

## 接口说明

### 查询笔记本列表

```http
GET /api/laptops
```

支持参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `keyword` | string | 在型号、原始标题、品牌名中模糊搜索 |
| `brand` | string | 品牌精确筛选，例如 `联想` |
| `cpuKeyword` | string | CPU 型号模糊筛选，例如 `Ultra` |
| `gpuKeyword` | string | GPU 型号模糊筛选，例如 `RTX` |
| `minPrice` | number | 最低最新价格 |
| `maxPrice` | number | 最高最新价格 |
| `minMemoryGb` | integer | 最低内存容量，单位 GB |
| `minStorageGb` | integer | 最低硬盘容量，单位 GB |
| `minScreenSize` | number | 最低屏幕尺寸，单位英寸 |
| `maxWeightKg` | number | 最高重量，单位 kg |
| `page` | integer | 页码，默认 1 |
| `size` | integer | 每页数量，默认 20，最大 100 |

示例：

```http
GET /api/laptops?brand=联想&minPrice=5000&maxPrice=12000&minMemoryGb=16&size=5
```

返回字段重点：

- `id`
- `brandName`
- `model`
- `latestPrice`
- `cpuModel`
- `gpuModel`
- `memoryCapacityGb`
- `storageCapacityGb`
- `screenSizeInch`
- `weightKg`

### 查询笔记本详情

```http
GET /api/laptops/{id}
```

示例：

```http
GET /api/laptops/50
```

详情接口返回列表字段之外，还会返回：

- CPU 核心数、线程数、基础功耗
- GPU 显存、显卡类型
- 内存类型和频率
- 硬盘类型和接口
- 屏幕分辨率、刷新率、面板、亮度、色域、触控支持
- 电池容量和充电功率
- Wi-Fi、蓝牙
- 接口汇总 `portSummary`

## SQL 查询设计

核心 SQL 在 `src/main/resources/mapper/LaptopMapper.xml`。

### 多表连接

列表和详情都从 `laptop` 主表出发，连接这些规格表：

- `brand`
- `cpu_spec`
- `gpu_spec`
- `memory_spec`
- `storage_spec`
- `screen_spec`
- `battery_spec`
- `wireless_spec`

这样 API 返回的数据不是一堆外键 id，而是前端可直接展示的规格字段。

### latest_price 的实现

价格记录在 `price_record` 表中，理论上同一台电脑会随着爬虫多次运行产生多条价格记录。接口需要返回最新价格，所以 SQL 里用子查询取每台电脑最新的一条价格：

```sql
LEFT JOIN price_record pr ON pr.id = (
    SELECT pr2.id
    FROM price_record pr2
    WHERE pr2.laptop_id = l.id
    ORDER BY pr2.crawled_at DESC, pr2.id DESC
    LIMIT 1
)
```

这样后续爬虫持续更新价格后，接口不需要改代码，仍然会拿到最新价格。

### 端口汇总

接口信息存在 `laptop_port` 和 `port_spec` 两张表里。详情接口使用 `GROUP_CONCAT` 聚合为一个字符串：

```sql
USB-A x2；HDMI x1；Thunderbolt x1
```

这一版先返回字符串，方便前端直接展示。后续如果前端需要更细粒度的接口列表，可以再新增一个结构化字段。

## 启动方式

先确保数据库已经创建并导入数据：

```powershell
mysql -u<db-username> -p -P3306 -D laptoprecommender -e "SELECT COUNT(*) FROM laptop;"
```

构建后端：

```powershell
cd laptop-rec-backend
mvn -q -DskipTests package
```

运行后端：

```powershell
java -jar target/laptop-rec-backend-0.0.1-SNAPSHOT.jar
```

如果 8080 端口被占用，可以临时换端口：

```powershell
$env:SERVER_PORT="18080"
java -jar target/laptop-rec-backend-0.0.1-SNAPSHOT.jar
```

## 验证命令

列表接口：

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/laptops?size=3"
```

详情接口：

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/laptops/50"
```

筛选接口：

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/laptops?cpuKeyword=Ultra&gpuKeyword=RTX&minMemoryGb=16&size=5"
```

## 本次验证结果

当前环境中已完成这些验证：

- `mvn -q -DskipTests package` 构建通过
- MySQL 中 `laptop` 表有 50 条数据
- MySQL 中 `price_record` 表有 50 条数据
- `GET /api/laptops?size=3` 返回成功，总数 50
- `GET /api/laptops/{id}` 返回成功，并包含 `latestPrice`
- `brand=联想` 筛选返回总数 13
- `cpuKeyword=Ultra` 筛选返回总数 15
- `gpuKeyword=RTX` 筛选返回总数 21
- 多个硬条件组合筛选能正常返回结果

## 下一步推荐接口如何接入

后续推荐算法可以在当前结构上继续增加：

```text
controller/RecommendController.java
dto/RecommendRequestDTO.java
vo/RecommendResultVO.java
service/RecommendService.java
service/impl/RecommendServiceImpl.java
```

推荐接口建议从：

```http
POST /api/recommend
```

开始。第一版推荐逻辑可以复用现在的列表筛选能力：

1. 预算、品牌、重量、内存、硬盘、屏幕尺寸先作为硬条件过滤。
2. 对过滤后的电脑按用户用途加权评分。
3. 返回分数、推荐理由和对应的笔记本简要信息。

这样推荐接口不需要重新写一套数据查询基础，只需要在当前查询结果上增加排序、评分和理由生成。

