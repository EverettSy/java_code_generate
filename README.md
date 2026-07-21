# Skl Java Code Generate 插件使用文档

> 一款 IntelliJ IDEA 插件，基于 MySQL 数据库表结构，一键生成 Java 后端常用代码：
> Entity、VO、BO、Query、Enum、Mapper、Service、Controller 等，减少重复劳动。

- 插件 ID：`com.skl.java.code.generate`
- 插件名称：Skl Java Code Generate
- 版本：2.0.0
- 作者：孙凯伦（376253703@qq.com）

---

## 一、功能特性

- 基于数据库表结构反向生成分层代码，支持 **多表批量生成**（逗号分隔）。
- 生成内容可按需选择，既支持「一键生成全部」，也支持单项生成。
- 基于 **FreeMarker 模板**，模板放在目标工程中，可自由定制生成风格。
- 自动解析字段注释生成 **状态枚举类**。
- 支持 **自定义查询条件**（等于 / 模糊 / 区间 / in / sql 等），并通过 Redis 缓存复用上次配置。
- 耗时操作在后台线程执行，不阻塞 IDE。

---

## 二、环境要求

| 项目 | 要求 |
| --- | --- |
| IntelliJ IDEA | 2023.3（build 233）及以上，社区版/旗舰版均可 |
| JDK | 17 |
| 数据库 | MySQL（生成时需能连通目标库） |
| Redis | 需要一个可用的 Redis（用于缓存自定义查询配置） |
| 目标工程结构 | 标准 Maven/Gradle 结构，含 `src/main/` 目录 |

---

## 三、安装与构建

本项目基于 Gradle + gradle-intellij-plugin 构建。

### 1. 本地运行调试

```bash
./gradlew runIde
```

会启动一个内置沙箱 IDEA，插件已加载，可直接体验。

### 2. 打包插件

```bash
./gradlew buildPlugin
```

产物位于 `build/distributions/*.zip`。

### 3. 安装到 IDEA

`Settings → Plugins → ⚙️ → Install Plugin from Disk...` 选择上面打包的 zip，重启生效。

---

## 四、前置配置（关键，必须先做）

⚠️ **重要**：数据库配置、Redis 配置以及 FreeMarker 模板都读取自 **你的目标工程**（即被生成代码的项目），
而不是插件本身。请在目标工程中按下述结构准备好文件。

### 目录结构

```
<你的项目根目录>/
└── src/main/resources/templates/
    ├── config/
    │   ├── mysql.properties      # 数据库连接配置
    │   └── redis.properties      # Redis 连接配置
    ├── skl_mapper.java.ftl
    ├── skl_entity.java.ftl
    ├── skl_entity_enums.java.ftl
    ├── skl_entity_vo.java.ftl
    ├── skl_entity_bo.java.ftl
    ├── skl_entity_query.java.ftl
    ├── skl_service.java.ftl
    ├── skl_serviceImpl.java.ftl
    ├── skl_service_query.java.ftl
    ├── skl_service_results.java.ftl
    └── skl_controller.java.ftl
```

### 1. `config/mysql.properties`

```properties
# 数据库名（需与 url 中一致）
database=your_db
# 生成代码的基础包名（最终包名为 packageName.模块名）
packageName=com.example.project
# JDBC 连接地址
url=jdbc:mysql://127.0.0.1:3306/your_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
username=root
password=your_password
```

### 2. `config/redis.properties`

```properties
host=127.0.0.1
port=6379
password=your_redis_password
```

### 3. 模板文件（`.ftl`）

上表中的每个 `.ftl` 文件都对应一种要生成的代码。可用的模板变量包括：

| 变量 | 说明 |
| --- | --- |
| `packageName` | 完整包名（小写） |
| `ClassName` / `className` | 类名（首字母大写 / 小写） |
| `author` | 作者 |
| `date` | 生成时间 |
| `functionName` | 表注释（作为功能名） |
| `tableName` | 数据库表名 |
| `list` | 字段列表（`columnName`、`columnNameUpper`、`dataType`、`columnComment` 等） |
| `enumsList` / `enumsEntitiesList` | 枚举相关数据 |
| `beanQuery` / `serviceQueryList` | 自定义查询数据 |

> 如果暂时没有模板，可先创建空白 `.ftl` 或参考你团队已有的代码规范编写。缺少对应模板会导致该项生成失败。

---

## 五、使用步骤

1. 在 IDEA 中打开已完成「前置配置」的目标工程。
2. 在 **Project 视图** 中，右键点击 `src/main` 内部的某个目录（如 `src/main/java`）。
   > 路径解析规则：以选中目录路径中的 `src/main/` 为界，取前半部分作为项目根路径。因此必须右键 `src/main/` 之内的目录。
3. 在右键菜单中选择 **`Skl Code Generate`** 子菜单。
4. 选择需要的生成项（见下表）。
5. 依次在弹窗中输入：
   - **作者名称**
   - **模块名称**（会拼在包名后，如 `com.example.project.user`）
   - **表名称**（单表直接填；多表用英文逗号分隔，如 `t_user,t_order`）
6. 若涉及 Query，会弹出「是否自定义查询变量 / 查询条件」的交互（可跳过）。
7. 生成完成后会有气泡通知，**刷新目录** 即可看到生成的文件。

---

## 六、右键菜单项说明

| 菜单项 | 说明 | 生成内容 |
| --- | --- | --- |
| **Generate All Code** | 一键生成全部 | Controller、Entity、BO、VO、Query、Enum、Service、ServiceImpl、ServiceQuery、ServiceResults、Mapper |
| **Generate Query Classes** | 生成查询相关类 | EntityQuery + ServiceQuery |
| **Generate Entity** | 生成实体类 | Entity |
| **Generate Entity Query** | 生成实体查询类 | EntityQuery |
| **Generate Entity BO** | 生成业务对象 | BO |
| **Generate Entity VO** | 生成视图对象 | VO |
| **Generate Enum** | 生成枚举类 | 状态枚举（依据字段注释） |
| **Generate Controller** | 生成控制层 | Controller |
| **Generate Service** | 生成服务层 | Service + ServiceImpl |
| **Generate Service Query** | 生成服务查询层 | ServiceQuery |
| **Generate Service Results** | 生成服务返回包装类 | ServiceResults |
| **Generate Mapper** | 生成 Mapper | Mapper 接口 |

---

## 七、生成目录结构

以 `packageName=com.example.project`、`moduleName=user` 为例，生成结果如下：

```
src/main/java/com/example/project/user/
├── controller/    XxxController.java
├── entity/        Xxx.java
│   ├── enums/     XxxStatusEnum.java
│   ├── vo/        XxxVO.java
│   ├── bo/        XxxBO.java
│   └── query/     XxxQuery.java
├── mapper/        XxxMapper.java
└── service/       XxxService.java
    ├── impl/      XxxServiceImpl.java
    ├── query/     XxxServiceQuery.java
    └── results/   XxxServiceResults.java
```

> 类名由表名转驼峰得到（`t_user` → `TUser`）。若已存在同名文件，会被 **覆盖生成**。

---

## 八、枚举生成规则

插件会解析字段注释自动生成状态枚举。注释需满足如下格式（使用中文括号与中文冒号、逗号）：

```
类型（0：首页，1：百万年薪，2：报价方案，3：工保百科）
```

解析后会为该字段生成对应的枚举类（含 `state` 与 `note`）。
执行「Generate Enum」时可输入指定字段名，只为该字段生成枚举；留空则对所有符合格式的字段生成。

---

## 九、自定义查询说明

当生成项包含 Query / ServiceQuery 时，会出现交互式弹窗：

- **自定义查询变量（beanQuery）**：为 Query 实体添加自定义字段，可选类型
  `String / Integer / Double / BigDecimal / LocalDateTime / Date / Long / byte[]`。
- **自定义查询条件（serviceQuery）**：为服务层查询添加条件，支持
  `等于 / 模糊查询 / 为空 / 不为空 / in / notIn / inSql / notInSql / 区间 / 非区间`。

上述配置会按「表名」缓存进 Redis，下次对同一张表生成时自动复用，避免重复输入。

---

## 十、常见问题

| 现象 | 排查方向 |
| --- | --- |
| 提示「未选中目标目录」 | 需在 Project 视图中右键一个目录（而非文件） |
| 提示「生成路径解析失败」 | 必须右键 `src/main/` 之内的目录 |
| 生成失败 / 连接异常 | 检查 `mysql.properties` 的 url、账号密码、库是否连通 |
| 自定义查询报错 | 检查 `redis.properties`，确认 Redis 可连接 |
| 某类没生成 | 确认对应 `.ftl` 模板已存在于 `templates/` 目录 |
| 生成后看不到文件 | 在目标目录上手动 **刷新（Reload from Disk）** |

---

## 十一、技术栈

- IntelliJ Platform Plugin SDK（gradle-intellij-plugin 1.17.4）
- FreeMarker 2.3.29（模板渲染）
- MySQL Connector/J 8.0.19（读取表结构）
- Jedis 3.1.0（缓存自定义查询）
- Hutool、Guava、Commons-Lang3、Lombok
