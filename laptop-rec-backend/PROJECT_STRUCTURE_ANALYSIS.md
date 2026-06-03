# 后端项目结构详细分析

这份文档是给后端初学者看的。目标不是只告诉你“文件在哪里”，而是解释每个文件为什么存在、它在一次接口请求中负责什么、以后你要改功能时应该从哪里下手。

当前后端项目只做一件事：

```text
从 MySQL 的 laptoprecommender 数据库中查询笔记本电脑数据，并通过 HTTP API 返回给前端或测试工具。
```

它暂时不负责爬虫，也不负责推荐算法。爬虫已经在根目录 `crawler/` 中完成；推荐算法是后续阶段。

## 先建立整体概念

一个 Spring Boot 后端可以粗略理解成几层：

```text
浏览器 / 前端 / 测试工具
        |
        | HTTP 请求
        v
Controller 控制器
        |
        | 调用 Java 方法
        v
Service 业务层
        |
        | 调用数据库查询方法
        v
Mapper 数据访问层
        |
        | 执行 SQL
        v
MySQL 数据库
```

当前项目中对应关系是：

```text
LaptopController
        |
        v
LaptopService / LaptopServiceImpl
        |
        v
LaptopMapper.java / LaptopMapper.xml
        |
        v
laptop、brand、cpu_spec、price_record 等 MySQL 表
```

你以后看代码时，不要从所有文件一起看。推荐顺序是：

1. 先看 `controller/`，知道有哪些接口。
2. 再看 `service/`，知道接口背后做了什么业务判断。
3. 再看 `mapper/` 和 `mapper/*.xml`，知道具体 SQL 怎么查数据库。
4. 最后看 `dto/`、`vo/`、`entity/`，理解输入参数、输出结果、数据库表结构分别用什么类表示。

## 项目目录总览

```text
laptop-rec-backend/
  .gitignore
  pom.xml
  README_BACKEND.md
  PROJECT_STRUCTURE_ANALYSIS.md
  src/
    main/
      java/
        com/
          example/
            laptoprec/
              LaptopRecBackendApplication.java
              common/
                GlobalExceptionHandler.java
                PageResult.java
                Result.java
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
                impl/
                  LaptopServiceImpl.java
              vo/
                LaptopDetailVO.java
                LaptopListItemVO.java
      resources/
        application.yml
        mapper/
          LaptopMapper.xml
```

下面按文件解释。

## 根目录文件

### `.gitignore`

位置：

```text
laptop-rec-backend/.gitignore
```

作用：告诉 Git 哪些文件不要纳入版本管理。

当前忽略：

```text
target/
.idea/
*.iml
*.log
%SystemDrive%/
```

含义：

- `target/`：Maven 构建产物，比如 jar 包、编译后的 class 文件。它们可以重新生成，不应该提交。
- `.idea/`、`*.iml`：IntelliJ IDEA 的本地配置。
- `*.log`：运行日志。
- `%SystemDrive%/`：Java 在当前 Windows 环境里可能生成的异常缓存目录，不属于项目源码。

### `pom.xml`

位置：

```text
laptop-rec-backend/pom.xml
```

作用：Maven 项目的核心配置文件。你可以把它理解为“后端项目说明书”，它告诉 Maven：

- 这个项目叫什么。
- 使用哪个 Java 版本。
- 依赖哪些第三方库。
- 怎么打包。

关键配置：

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.0</version>
</parent>
```

这表示项目基于 Spring Boot 3.5.0。

```xml
<java.version>17</java.version>
```

这表示用 Java 17 作为编译目标。本机 Java 21 也能运行，因为 Java 21 高于 Java 17。

主要依赖：

```text
spring-boot-starter-web
```

提供 Web 后端能力，包括：

- 启动内置 Tomcat
- 处理 HTTP 请求
- 支持 `@RestController`
- 返回 JSON

```text
spring-boot-starter-validation
```

提供参数校验能力。当前代码暂时没有大量使用，但后续推荐接口可以用它校验请求参数。

```text
mybatis-plus-spring-boot3-starter
```

把 MyBatis-Plus 接入 Spring Boot 3。当前项目主要用它来管理 Mapper。

```text
mysql-connector-j
```

MySQL JDBC 驱动。没有它，Java 程序不能连接 MySQL。

```text
spring-boot-starter-test
```

测试依赖。当前还没有写自动化测试，但后续可以用它写接口测试或单元测试。

### `README_BACKEND.md`

位置：

```text
laptop-rec-backend/README_BACKEND.md
```

作用：说明后端的技术栈、接口、运行方式和已经验证过的结果。

这份 `PROJECT_STRUCTURE_ANALYSIS.md` 会比它更细，更偏向“新手读代码”。

## `src/main/resources`

`resources` 目录放的是运行时配置文件和非 Java 源码资源。

### `application.yml`

位置：

```text
laptop-rec-backend/src/main/resources/application.yml
```

作用：Spring Boot 的配置文件。程序启动时会自动读取。

当前配置分三块。

#### server

```yaml
server:
  port: ${SERVER_PORT:8080}
```

含义：

- 默认端口是 `8080`。
- 如果你设置了环境变量 `SERVER_PORT`，就使用环境变量的值。

例如：

```powershell
$env:SERVER_PORT="18080"
java -jar target/laptop-rec-backend-0.0.1-SNAPSHOT.jar
```

这样后端会运行在 `http://localhost:18080`。

#### spring.datasource

```yamld
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

这是数据库连接配置。

默认连接：

```text
host: localhost
port: 3306
database: laptoprecommender
username: <db-username>
password: <db-password>
```

这和当前你的 MySQL 环境一致。

`url` 后面的参数含义：

- `useUnicode=true`：支持 Unicode 字符。
- `characterEncoding=utf8`：使用 UTF-8 编码，避免中文乱码。
- `useSSL=false`：本地开发不用 SSL。
- `serverTimezone=Asia/Shanghai`：指定时区。
- `allowPublicKeyRetrieval=true`：允许 MySQL 8 某些认证方式下取公钥，本地开发常用。

#### mybatis-plus

```yaml
mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true
```

含义：

- `mapper-locations`：告诉 MyBatis 去哪里找 XML SQL 文件。
- `map-underscore-to-camel-case: true`：数据库字段的下划线命名会自动映射到 Java 的驼峰命名。

例子：

```text
数据库字段: brand_name
Java 字段: brandName
```

这就是为什么 SQL 里写：

```sql
b.name AS brand_name
```

可以映射到 Java 的：

```java
private String brandName;
```

### `mapper/LaptopMapper.xml`

位置：

```text
laptop-rec-backend/src/main/resources/mapper/LaptopMapper.xml
```

作用：写真正执行的 SQL。

它和 Java 接口 `LaptopMapper.java` 是一一对应的。

开头：

```xml
<mapper namespace="com.example.laptoprec.mapper.LaptopMapper">
```

意思是：这个 XML 文件属于 `LaptopMapper` 这个 Java Mapper 接口。

#### `LaptopJoins`

```xml
<sql id="LaptopJoins">
```

这是一个可复用 SQL 片段，负责把主表和各个规格表连接起来。

它从 `laptop l` 开始：

```sql
FROM laptop l
JOIN brand b ON b.id = l.brand_id
```

然后连接 CPU、GPU、内存、硬盘、屏幕、电池、无线规格：

```sql
LEFT JOIN cpu_spec c ON c.id = l.cpu_id
LEFT JOIN gpu_spec g ON g.id = l.gpu_id
LEFT JOIN memory_spec m ON m.id = l.memory_id
LEFT JOIN storage_spec st ON st.id = l.storage_id
LEFT JOIN screen_spec sc ON sc.id = l.screen_id
LEFT JOIN battery_spec ba ON ba.id = l.battery_id
LEFT JOIN wireless_spec wi ON wi.id = l.wireless_id
```

为什么大部分是 `LEFT JOIN`？

因为有些笔记本可能缺少某类规格。比如某台电脑没有解析到电池信息，如果用普通 `JOIN`，这台电脑会被过滤掉；用 `LEFT JOIN`，主表里的电脑仍然能返回，只是电池字段为 `null`。

#### 最新价格查询

同一台笔记本以后可能有多条价格记录，所以接口不能随便取一条价格，而要取最新价格：

```sql
LEFT JOIN price_record pr ON pr.id = (
    SELECT pr2.id
    FROM price_record pr2
    WHERE pr2.laptop_id = l.id
    ORDER BY pr2.crawled_at DESC, pr2.id DESC
    LIMIT 1
)
```

理解方式：

1. 对当前笔记本 `l.id`，去 `price_record` 表找它的价格记录。
2. 按 `crawled_at` 从新到旧排序。
3. 如果时间相同，再按 `id` 从大到小排序。
4. 只取第一条。

这样爬虫以后不断新增价格记录时，接口会自动显示最新价格。

#### `LaptopFilters`

```xml
<sql id="LaptopFilters">
```

这是列表查询的动态筛选条件。

`<where>` 是 MyBatis 标签，会自动生成 SQL 的 `WHERE`，并处理多余的 `AND`。

例子：

```xml
<if test="query.brand != null">
    AND b.name = #{query.brand}
</if>
```

意思是：

- 如果请求里传了 `brand`，就加品牌筛选。
- 如果没传，就不加这段 SQL。

`#{query.brand}` 是参数占位符。MyBatis 会用安全方式把 Java 参数放进 SQL，避免直接拼字符串带来的 SQL 注入问题。

当前支持的筛选：

```text
keyword
brand
cpuKeyword
gpuKeyword
minPrice
maxPrice
minMemoryGb
minStorageGb
minScreenSize
maxWeightKg
```

#### `selectListItems`

```xml
<select id="selectListItems" resultType="com.example.laptoprec.vo.LaptopListItemVO">
```

对应 Java 方法：

```java
List<LaptopListItemVO> selectListItems(@Param("query") LaptopQueryDTO query);
```

作用：查询列表页需要显示的数据。

它返回的是 `LaptopListItemVO`，不是数据库实体 `Laptop`。原因是列表页需要的是“组合后的展示数据”，比如品牌名、CPU 型号、最新价格，而不是只有 `laptop` 表里的外键 id。

#### `countListItems`

```xml
<select id="countListItems" resultType="long">
```

对应 Java 方法：

```java
long countListItems(@Param("query") LaptopQueryDTO query);
```

作用：计算符合筛选条件的总数量。

分页接口必须有总数，否则前端不知道一共有多少页。

当前列表查询流程是：

```text
先 countListItems 查总数
再 selectListItems 查当前页数据
```

#### `selectDetailById`

```xml
<select id="selectDetailById" resultType="com.example.laptoprec.vo.LaptopDetailVO">
```

对应 Java 方法：

```java
LaptopDetailVO selectDetailById(@Param("id") Long id);
```

作用：根据笔记本 id 查询详情。

详情比列表多返回很多字段，比如：

- CPU 核心数、线程数
- GPU 显存
- 内存频率
- 屏幕亮度、色域、是否触控
- 电池容量
- Wi-Fi 和蓝牙
- 接口汇总

接口汇总通过子查询生成：

```sql
GROUP_CONCAT(
    CONCAT(ps.port_name, ' x', lp.port_count)
    ORDER BY ps.port_name
    SEPARATOR '；'
) AS port_summary
```

它会把多行接口记录合并成一个字符串，方便详情页直接展示。

## `src/main/java`

Java 源码都在这里。

包名是：

```text
com.example.laptoprec
```

包名就是 Java 的命名空间，避免类名冲突。真实项目中一般会用公司域名倒序，这里用 `com.example` 是 demo 项目的常见写法。

## 启动入口

### `LaptopRecBackendApplication.java`

位置：

```text
laptop-rec-backend/src/main/java/com/example/laptoprec/LaptopRecBackendApplication.java
```

作用：整个 Spring Boot 程序的启动入口。

关键注解：

```java
@SpringBootApplication
```

它表示这是一个 Spring Boot 应用。它会做几件事：

- 扫描当前包及子包下的 Spring 组件。
- 读取 `application.yml`。
- 启动内置 Tomcat。
- 创建 Controller、Service、Mapper 等对象。

```java
@MapperScan("com.example.laptoprec.mapper")
```

它告诉 MyBatis：

```text
去 com.example.laptoprec.mapper 这个包下面找 Mapper 接口。
```

没有这个配置，Spring 可能找不到 `LaptopMapper`，Service 就无法注入它。

主方法：

```java
public static void main(String[] args) {
    SpringApplication.run(LaptopRecBackendApplication.class, args);
}
```

这行代码启动整个后端。

## common 公共返回和异常处理

### `Result.java`

位置：

```text
src/main/java/com/example/laptoprec/common/Result.java
```

作用：统一接口返回格式。

没有统一格式时，不同接口可能返回完全不同的 JSON，前端处理会很乱。现在所有接口都返回：

```json
{
  "success": true,
  "code": 200,
  "message": "ok",
  "data": {}
}
```

成功时使用：

```java
Result.ok(data)
```

失败时使用：

```java
Result.fail(code, message)
```

### `PageResult.java`

位置：

```text
src/main/java/com/example/laptoprec/common/PageResult.java
```

作用：统一分页返回格式。

列表接口返回的 `data` 里面不是一个单纯数组，而是：

```json
{
  "total": 50,
  "page": 1,
  "size": 20,
  "records": []
}
```

字段含义：

- `total`：符合条件的总数量。
- `page`：当前页码。
- `size`：每页数量。
- `records`：当前页的数据列表。

### `GlobalExceptionHandler.java`

位置：

```text
src/main/java/com/example/laptoprec/common/GlobalExceptionHandler.java
```

作用：统一处理异常。

关键注解：

```java
@RestControllerAdvice
```

它表示这个类专门拦截 Controller 抛出的异常，并返回 JSON。

当前处理两类异常。

#### 参数错误

```java
@ExceptionHandler(IllegalArgumentException.class)
@ResponseStatus(HttpStatus.BAD_REQUEST)
```

比如详情接口传了 `id <= 0`，Service 会抛 `IllegalArgumentException`。这里会把它转成 HTTP 400：

```json
{
  "success": false,
  "code": 400,
  "message": "笔记本 id 必须是正整数",
  "data": null
}
```

#### 其他错误

```java
@ExceptionHandler(Exception.class)
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
```

没被专门处理的异常会返回 HTTP 500。

这对新手调试很有用，因为你能看到错误信息，而不是只看到空白页面。

## config 配置类

### `CorsConfig.java`

位置：

```text
src/main/java/com/example/laptoprec/config/CorsConfig.java
```

作用：配置跨域。

跨域问题通常发生在：

```text
前端运行在 http://localhost:5173
后端运行在 http://localhost:8080
```

虽然都是本机，但端口不同，浏览器会认为它们是不同来源。没有 CORS 配置时，前端可能无法调用后端接口。

当前配置允许本机前端访问 `/api/**` 下面的接口。

## controller 控制器层

### `LaptopController.java`

位置：

```text
src/main/java/com/example/laptoprec/controller/LaptopController.java
```

作用：定义 HTTP API。

关键注解：

```java
@RestController
```

表示这是一个 REST 控制器。方法返回的 Java 对象会自动转成 JSON。

```java
@RequestMapping("/api/laptops")
```

表示这个类下面的接口都以 `/api/laptops` 开头。

当前有两个接口。

#### 列表接口

```java
@GetMapping
public Result<PageResult<LaptopListItemVO>> list(LaptopQueryDTO query) {
    return Result.ok(laptopService.queryLaptops(query));
}
```

完整路径：

```http
GET /api/laptops
```

Spring 会自动把 URL 参数填入 `LaptopQueryDTO`。

例如请求：

```http
GET /api/laptops?brand=联想&minPrice=5000&page=1&size=5
```

Spring 会自动等价于：

```java
query.setBrand("联想");
query.setMinPrice(new BigDecimal("5000"));
query.setPage(1);
query.setSize(5);
```

然后 Controller 调用：

```java
laptopService.queryLaptops(query)
```

#### 详情接口

```java
@GetMapping("/{id}")
public Result<LaptopDetailVO> detail(@PathVariable Long id) {
    return Result.ok(laptopService.getLaptopDetail(id));
}
```

完整路径：

```http
GET /api/laptops/{id}
```

例如：

```http
GET /api/laptops/50
```

`@PathVariable` 会把路径里的 `50` 取出来，放进 `Long id`。

## dto 请求参数对象

### `LaptopQueryDTO.java`

位置：

```text
src/main/java/com/example/laptoprec/dto/LaptopQueryDTO.java
```

DTO 的意思是 Data Transfer Object，也就是“数据传输对象”。

在当前项目中，`LaptopQueryDTO` 专门表示列表查询的请求参数。

字段分三类。

#### 关键词筛选

```java
private String keyword;
private String brand;
private String cpuKeyword;
private String gpuKeyword;
```

含义：

- `keyword`：通用关键词，在型号、原始标题、品牌名中搜索。
- `brand`：品牌精确匹配。
- `cpuKeyword`：CPU 型号模糊搜索。
- `gpuKeyword`：GPU 型号模糊搜索。

#### 数值筛选

```java
private BigDecimal minPrice;
private BigDecimal maxPrice;
private Integer minMemoryGb;
private Integer minStorageGb;
private BigDecimal minScreenSize;
private BigDecimal maxWeightKg;
```

为什么价格、重量、屏幕尺寸用 `BigDecimal`？

因为这些值可能带小数。Java 里处理金额和精确小数时，`BigDecimal` 比 `double` 更稳。

#### 分页参数

```java
private Integer page = 1;
private Integer size = 20;
private Integer offset = 0;
```

`page` 是页码，`size` 是每页数量。

MySQL 的分页语法需要的是：

```sql
LIMIT offset, size
```

所以 DTO 里额外算了 `offset`：

```java
offset = (page - 1) * size;
```

比如：

```text
page = 1, size = 20, offset = 0
page = 2, size = 20, offset = 20
page = 3, size = 20, offset = 40
```

#### normalize 方法

```java
public void normalize()
```

这是参数清洗方法。Service 查询前会先调用它。

它做几件事：

- 页码为空或小于 1 时，改为 1。
- 每页数量为空或小于 1 时，改为 20。
- 每页数量大于 100 时，限制为 100。
- 计算 offset。
- 把空字符串参数转成 null。

为什么要把空字符串转成 null？

因为 XML SQL 里判断的是：

```xml
<if test="query.brand != null">
```

如果前端传了 `brand=`，它不是 `null`，而是空字符串。如果不清洗，就会执行：

```sql
AND b.name = ''
```

结果会查不到任何数据。

## entity 数据库实体

### `Laptop.java`

位置：

```text
src/main/java/com/example/laptoprec/entity/Laptop.java
```

Entity 的意思是“实体类”，通常用于表示数据库表。

`Laptop.java` 对应的是 MySQL 的 `laptop` 表。

它的字段大致对应：

```text
laptop.id
laptop.brand_id
laptop.cpu_id
laptop.gpu_id
laptop.memory_id
laptop.storage_id
laptop.screen_id
laptop.battery_id
laptop.wireless_id
laptop.model
laptop.weight_kg
...
```

关键注解：

```java
@TableName("laptop")
```

告诉 MyBatis-Plus：这个类对应数据库里的 `laptop` 表。

```java
@TableId(type = IdType.AUTO)
```

告诉 MyBatis-Plus：主键是自增 id。

当前列表和详情查询没有直接返回 `Laptop`，而是返回 VO。`Laptop` 主要是为后续可能的基础 CRUD 或单表操作准备的。

## mapper 数据访问层

### `LaptopMapper.java`

位置：

```text
src/main/java/com/example/laptoprec/mapper/LaptopMapper.java
```

作用：定义“可以执行哪些数据库查询”。

它是 Java 接口，不直接写 SQL：

```java
public interface LaptopMapper extends BaseMapper<Laptop>
```

`BaseMapper<Laptop>` 来自 MyBatis-Plus，提供一些基础单表方法。当前复杂查询主要靠 XML。

当前定义三个方法：

```java
List<LaptopListItemVO> selectListItems(@Param("query") LaptopQueryDTO query);
```

查询列表数据。

```java
long countListItems(@Param("query") LaptopQueryDTO query);
```

查询列表总数。

```java
LaptopDetailVO selectDetailById(@Param("id") Long id);
```

查询详情数据。

`@Param("query")` 的作用是给 XML 里的参数起名字。因为 XML 里写的是：

```xml
#{query.brand}
```

所以 Java 方法参数必须有：

```java
@Param("query")
```

## service 业务层

### `LaptopService.java`

位置：

```text
src/main/java/com/example/laptoprec/service/LaptopService.java
```

作用：定义业务能力。

它是接口，只写“能做什么”，不写“怎么做”。

当前能力：

```java
PageResult<LaptopListItemVO> queryLaptops(LaptopQueryDTO query);
LaptopDetailVO getLaptopDetail(Long id);
```

为什么要有接口？

因为 Controller 只依赖接口，不直接依赖实现类。以后如果推荐逻辑复杂了，或者要换实现方式，可以更容易替换。

### `LaptopServiceImpl.java`

位置：

```text
src/main/java/com/example/laptoprec/service/impl/LaptopServiceImpl.java
```

作用：实现 `LaptopService`。

关键注解：

```java
@Service
```

表示这是一个 Service 组件。Spring 启动时会自动创建它的对象。

构造函数：

```java
public LaptopServiceImpl(LaptopMapper laptopMapper) {
    this.laptopMapper = laptopMapper;
}
```

这是构造器注入。Spring 会自动把 `LaptopMapper` 传进来。

#### 列表查询逻辑

```java
query.normalize();
long total = laptopMapper.countListItems(query);
List<LaptopListItemVO> records = total == 0 ? List.of() : laptopMapper.selectListItems(query);
return new PageResult<>(total, query.getPage(), query.getSize(), records);
```

理解顺序：

1. 先清洗参数。
2. 查总数。
3. 如果总数是 0，就不查列表，直接返回空数组。
4. 如果总数大于 0，再查当前页数据。
5. 包装成分页对象返回。

#### 详情查询逻辑

```java
if (id == null || id <= 0) {
    throw new IllegalArgumentException("笔记本 id 必须是正整数");
}
```

先检查 id 合法性。

```java
LaptopDetailVO detail = laptopMapper.selectDetailById(id);
```

查数据库。

```java
if (detail == null) {
    throw new IllegalArgumentException("笔记本不存在，id=" + id);
}
```

查不到就抛异常。异常会被 `GlobalExceptionHandler` 变成统一 JSON 返回。

## vo 返回对象

VO 的意思是 View Object，也就是“给前端看的对象”。

不要把 VO 和 Entity 混在一起。

```text
Entity: 更接近数据库表
DTO: 更接近请求参数
VO: 更接近接口返回结果
```

### `LaptopListItemVO.java`

位置：

```text
src/main/java/com/example/laptoprec/vo/LaptopListItemVO.java
```

作用：列表页返回的一条笔记本数据。

典型字段：

```text
id
brandName
model
latestPrice
cpuModel
gpuModel
memoryCapacityGb
storageCapacityGb
screenSizeInch
weightKg
```

列表页通常不需要全部详情，只需要能让用户快速扫一眼。

### `LaptopDetailVO.java`

位置：

```text
src/main/java/com/example/laptoprec/vo/LaptopDetailVO.java
```

作用：详情页返回的一台笔记本完整信息。

它比列表 VO 多很多字段：

```text
cpuCoreCount
cpuThreadCount
gpuVramGb
memoryType
storageInterfaceType
screenBrightnessNit
batteryCapacityWh
wifiVersion
bluetoothVersion
portSummary
```

详情页需要更完整的数据，所以单独建一个 VO。

## 一次列表请求的完整流程

假设你访问：

```http
GET http://localhost:8080/api/laptops?brand=联想&minPrice=5000&size=5
```

完整流程：

```text
1. 浏览器或测试工具发送 HTTP GET 请求。

2. Spring Boot 内置 Tomcat 收到请求。

3. Spring 根据路径 /api/laptops 找到 LaptopController.list。

4. Spring 自动创建 LaptopQueryDTO，并把 URL 参数填进去：
   brand = 联想
   minPrice = 5000
   size = 5

5. Controller 调用 LaptopService.queryLaptops(query)。

6. LaptopServiceImpl 调用 query.normalize() 清洗参数。

7. LaptopServiceImpl 调用 laptopMapper.countListItems(query) 查总数。

8. MyBatis 根据 LaptopMapper.java 找到 LaptopMapper.xml 中的 countListItems SQL。

9. MyBatis 拼出带筛选条件的 SQL 并查询 MySQL。

10. Service 如果发现 total > 0，就继续调用 selectListItems(query)。

11. MyBatis 执行列表 SQL，连接 laptop、brand、cpu_spec、gpu_spec 等表。

12. MyBatis 把 SQL 结果映射成 List<LaptopListItemVO>。

13. Service 把 total、page、size、records 包装成 PageResult。

14. Controller 用 Result.ok(...) 包装统一响应。

15. Spring Boot 把 Java 对象转成 JSON 返回。
```

## 一次详情请求的完整流程

假设你访问：

```http
GET http://localhost:8080/api/laptops/50
```

完整流程：

```text
1. Spring 根据 /api/laptops/50 找到 LaptopController.detail。

2. @PathVariable 把路径里的 50 转成 Long id。

3. Controller 调用 laptopService.getLaptopDetail(50)。

4. Service 检查 id 是否为空、是否大于 0。

5. Service 调用 laptopMapper.selectDetailById(50)。

6. MyBatis 执行 XML 中的 selectDetailById SQL。

7. SQL 查询 laptop 主表、规格表、最新价格、接口汇总。

8. MyBatis 把结果映射为 LaptopDetailVO。

9. Service 如果查不到数据，就抛 IllegalArgumentException。

10. 如果查到了，Controller 返回 Result.ok(detail)。
```

## DTO、Entity、VO 的区别

这是新手最容易混的地方。

### DTO

DTO 代表“请求进来的数据”。

当前例子：

```text
LaptopQueryDTO
```

它关心的是：

```text
用户传了哪些筛选条件？
页码是多少？
每页多少条？
```

### Entity

Entity 代表“数据库表结构”。

当前例子：

```text
Laptop
```

它关心的是：

```text
laptop 表有哪些字段？
主键是什么？
外键 id 是什么？
```

### VO

VO 代表“接口返回给前端看的数据”。

当前例子：

```text
LaptopListItemVO
LaptopDetailVO
```

它关心的是：

```text
前端展示需要哪些字段？
字段名是否好理解？
是否已经把外键 id 转成可读文字？
```

## 为什么不用一个类解决所有问题

技术上可以只用一个类，但项目变复杂后会很乱。

比如 `Laptop` 表里只有：

```text
brand_id
cpu_id
gpu_id
```

但前端想看到的是：

```text
brandName
cpuModel
gpuModel
latestPrice
```

这些字段并不全在 `laptop` 表里，而是来自多表连接和价格子查询。用 VO 表示返回结果会更清楚。

## 当前 API 和文件的对应关系

### `GET /api/laptops`

涉及文件：

```text
LaptopController.java
LaptopService.java
LaptopServiceImpl.java
LaptopMapper.java
LaptopMapper.xml
LaptopQueryDTO.java
LaptopListItemVO.java
PageResult.java
Result.java
```

### `GET /api/laptops/{id}`

涉及文件：

```text
LaptopController.java
LaptopService.java
LaptopServiceImpl.java
LaptopMapper.java
LaptopMapper.xml
LaptopDetailVO.java
Result.java
GlobalExceptionHandler.java
```

## 如果你想新增一个筛选条件

假设你想增加：

```text
按产品类型 productType 筛选
```

需要改 4 个地方。

### 1. 改 DTO

在 `LaptopQueryDTO.java` 中增加字段：

```java
private String productType;
```

并生成 getter/setter。

在 `normalize()` 中加：

```java
productType = normalizeText(productType);
```

### 2. 改 XML 筛选条件

在 `LaptopMapper.xml` 的 `LaptopFilters` 中增加：

```xml
<if test="query.productType != null">
    AND l.product_type = #{query.productType}
</if>
```

### 3. 不一定需要改 Controller

当前 Controller 的列表方法是：

```java
public Result<PageResult<LaptopListItemVO>> list(LaptopQueryDTO query)
```

Spring 会自动把 URL 参数塞进 DTO。只要 DTO 有字段，就能接收。

### 4. 测试接口

```http
GET /api/laptops?productType=游戏本
```

如果返回正确，说明新增筛选完成。

## 如果你想新增一个返回字段

假设你想在列表里返回：

```text
os
```

需要改 2 个地方。

### 1. 改 VO

在 `LaptopListItemVO.java` 增加：

```java
private String os;
```

并生成 getter/setter。

### 2. 改 SQL

在 `LaptopMapper.xml` 的 `selectListItems` 里增加：

```sql
l.os,
```

因为 `os` 数据库字段和 Java 字段同名，MyBatis 可以直接映射。

如果数据库字段是 `source_url`，Java 字段是 `sourceUrl`，因为配置了下划线转驼峰，也可以自动映射。

## 如果你想新增一个接口

假设你想新增：

```http
GET /api/laptops/brands
```

用来返回所有品牌。

推荐步骤：

1. 在 `vo/` 中建 `BrandVO`。
2. 在 `mapper/LaptopMapper.java` 中加方法。
3. 在 `LaptopMapper.xml` 中写 SQL。
4. 在 `LaptopService.java` 中加业务方法。
5. 在 `LaptopServiceImpl.java` 中实现。
6. 在 `LaptopController.java` 中加 `@GetMapping("/brands")`。

这就是当前项目的标准扩展路径。

## 后续推荐接口应该放哪里

推荐接口建议不要硬塞到 `LaptopController` 里，而是新建一组文件：

```text
controller/RecommendController.java
dto/RecommendRequestDTO.java
vo/RecommendResultVO.java
service/RecommendService.java
service/impl/RecommendServiceImpl.java
```

原因：

- `LaptopController` 负责笔记本基础查询。
- `RecommendController` 负责推荐。
- 两者职责不同，分开更清楚。

推荐逻辑可能会复用 `LaptopMapper` 的查询能力，也可能新建 `RecommendMapper`。如果只是从现有筛选数据上打分，可以先复用 `LaptopMapper`。

## 数据库表在后端中的使用方式

当前后端主要读这些表：

```text
brand
cpu_spec
gpu_spec
memory_spec
storage_spec
screen_spec
battery_spec
wireless_spec
laptop
laptop_port
port_spec
price_record
```

其中：

- `laptop` 是主表。
- `brand` 提供品牌名。
- `cpu_spec`、`gpu_spec`、`memory_spec` 等提供硬件规格。
- `price_record` 提供价格记录。
- `laptop_port` 和 `port_spec` 提供接口信息。

当前后端没有直接使用：

```text
crawl_source
crawl_log
```

这两张表主要给爬虫模块记录数据源和爬取日志用。

## 常见注解解释

### `@SpringBootApplication`

启动整个 Spring Boot 应用。

### `@MapperScan`

扫描 MyBatis Mapper 接口。

### `@RestController`

声明这个类是 HTTP API 控制器，返回值自动转 JSON。

### `@RequestMapping`

给一个 Controller 或方法设置 URL 前缀。

### `@GetMapping`

声明一个 GET 请求接口。

### `@PathVariable`

从 URL 路径里取参数。

### `@Service`

声明业务层组件，让 Spring 自动管理。

### `@RestControllerAdvice`

声明全局异常处理器。

### `@ExceptionHandler`

指定某个方法处理某类异常。

### `@TableName`

MyBatis-Plus 注解，指定实体类对应哪张数据库表。

### `@TableId`

MyBatis-Plus 注解，指定主键字段。

### `@Param`

MyBatis 注解，给 SQL XML 中使用的参数命名。

## 本项目为什么暂时没有 Lombok

很多 Java 项目会用 Lombok 来减少 getter/setter 样板代码，比如：

```java
@Data
```

但当前项目没有使用 Lombok，原因是：

1. 对后端新手来说，显式 getter/setter 更容易看懂。
2. 减少一个编译插件和 IDE 配置点。
3. 先把 Spring Boot、Controller、Service、Mapper、SQL 这些主线学清楚。

后续项目稳定后，如果你觉得 getter/setter 太多，可以再引入 Lombok。

## 运行和调试顺序

### 1. 确认数据库

```powershell
mysql -u<db-username> -p -P3306 -D laptoprecommender -e "SELECT COUNT(*) FROM laptop;"
```

如果这里失败，后端启动后也无法查询数据。

### 2. 构建项目

```powershell
cd laptop-rec-backend
mvn -q -DskipTests package
```

构建成功会生成：

```text
target/laptop-rec-backend-0.0.1-SNAPSHOT.jar
```

`target/` 是生成目录，不需要提交。

### 3. 启动项目

```powershell
java -jar target/laptop-rec-backend-0.0.1-SNAPSHOT.jar
```

看到类似下面内容表示启动成功：

```text
Tomcat started on port 8080
Started LaptopRecBackendApplication
```

### 4. 测试接口

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/laptops?size=3"
```

详情：

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/laptops/50"
```

## 常见问题排查

### 端口被占用

现象：启动时报 8080 端口已被占用。

解决：

```powershell
$env:SERVER_PORT="18080"
java -jar target/laptop-rec-backend-0.0.1-SNAPSHOT.jar
```

然后访问：

```text
http://localhost:18080/api/laptops
```

### 数据库连接失败

检查：

```text
MySQL 是否启动？
数据库名是不是 laptoprecommender？
root 密码是不是 123456？
端口是不是 3306？
```

也可以用环境变量覆盖：

```powershell
$env:DB_NAME="laptoprecommender"
$env:DB_USERNAME="<db-username>"
$env:DB_PASSWORD="<db-password>"
```

### 接口返回 400

通常是业务参数不合法，比如：

```http
GET /api/laptops/0
```

Service 会认为 id 必须是正整数。

### 接口返回 500

通常是代码错误、SQL 错误或数据库连接错误。

优先看控制台日志，找到异常栈里最靠前的项目文件，例如：

```text
LaptopMapper.xml
LaptopServiceImpl.java
```

### 返回字段是 null

可能原因：

1. 数据库本身没有这个字段的数据。
2. SQL 没有 select 这个字段。
3. SQL alias 和 VO 字段名对不上。
4. VO 没有对应 getter/setter。

例如数据库字段：

```sql
latest_price
```

Java 字段：

```java
private BigDecimal latestPrice;
```

因为启用了下划线转驼峰，所以可以映射。

## 你读代码时可以这样定位问题

### 想知道接口路径

看：

```text
controller/LaptopController.java
```

### 想知道接口参数

看：

```text
dto/LaptopQueryDTO.java
```

### 想知道返回 JSON 有哪些字段

看：

```text
vo/LaptopListItemVO.java
vo/LaptopDetailVO.java
```

### 想知道业务判断在哪里

看：

```text
service/impl/LaptopServiceImpl.java
```

### 想知道 SQL 怎么写

看：

```text
src/main/resources/mapper/LaptopMapper.xml
```

### 想知道数据库怎么连接

看：

```text
src/main/resources/application.yml
```

### 想知道依赖版本

看：

```text
pom.xml
```

## 当前项目边界

当前后端已经完成：

```text
笔记本列表查询
笔记本详情查询
多条件筛选
分页
最新价格返回
统一响应
异常处理
本地跨域配置
```

当前后端还没有做：

```text
用户登录
管理员后台
新增、修改、删除笔记本
推荐算法
自动触发爬虫
定时任务
缓存
自动化测试
前端页面
```

这些不是遗漏，而是下一阶段功能。

## 作为新手，建议你掌握的最小路线

第一轮只掌握这些：

```text
Controller 是接口入口。
Service 写业务逻辑。
Mapper 调数据库。
DTO 接收请求参数。
VO 返回响应数据。
application.yml 配数据库和端口。
pom.xml 管依赖。
```

第二轮再掌握：

```text
MyBatis XML 动态 SQL。
多表 JOIN。
下划线转驼峰映射。
异常统一处理。
分页 total 和 records 的关系。
```

第三轮再做：

```text
新增接口。
新增筛选条件。
新增推荐算法。
写测试。
接前端。
```

不要一开始就试图把 Spring 全部学完。先抓住当前项目里的主线，能顺着一次请求从 Controller 追到 SQL，再从 SQL 返回 VO，就已经能掌控这个后端的第一版了。

