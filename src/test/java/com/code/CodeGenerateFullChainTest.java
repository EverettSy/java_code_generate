package com.code;

import com.code.action.*;
import com.code.config.CodeGenSettingsState;
import com.code.utils.*;
import com.code.utils.entity.*;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Skl Java Code Generate 全方位自动化测试套件（覆盖 P0 ~ P3 所有核心模块、边界条件与多模态模板渲染）
 *
 * @author syrobin
 */
public class CodeGenerateFullChainTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    /**
     * 测试场景 1（P0）：全量数据类型映射（MySQL/PG/Oracle/DM8）、驼峰命名、24h 时间格式与 UTF-8 文件读写
     */
    @Test
    public void test01_P0DataTypesAndFileUtils() throws Exception {
        // 整型与长整型边界（验证 bigint/int8/number -> Long 修复）
        Assert.assertEquals("Long", CodeGenerateUtils.sqlType2JavaType("bigint"));
        Assert.assertEquals("Long", CodeGenerateUtils.sqlType2JavaType("int8"));
        Assert.assertEquals("Long", CodeGenerateUtils.sqlType2JavaType("bigserial"));
        Assert.assertEquals("Long", CodeGenerateUtils.sqlType2JavaType("number"));
        Assert.assertEquals("Integer", CodeGenerateUtils.sqlType2JavaType("int"));
        Assert.assertEquals("Integer", CodeGenerateUtils.sqlType2JavaType("integer"));
        Assert.assertEquals("Integer", CodeGenerateUtils.sqlType2JavaType("tinyint"));
        Assert.assertEquals("Integer", CodeGenerateUtils.sqlType2JavaType("mediumint"));
        Assert.assertEquals("Integer", CodeGenerateUtils.sqlType2JavaType("int2"));
        Assert.assertEquals("Integer", CodeGenerateUtils.sqlType2JavaType("int4"));
        Assert.assertEquals("Integer", CodeGenerateUtils.sqlType2JavaType("serial"));
        Assert.assertEquals("Short", CodeGenerateUtils.sqlType2JavaType("smallint"));

        // 浮点与高精度金额类型
        Assert.assertEquals("Float", CodeGenerateUtils.sqlType2JavaType("float"));
        Assert.assertEquals("Float", CodeGenerateUtils.sqlType2JavaType("float4"));
        Assert.assertEquals("Double", CodeGenerateUtils.sqlType2JavaType("double"));
        Assert.assertEquals("Double", CodeGenerateUtils.sqlType2JavaType("float8"));
        Assert.assertEquals("Double", CodeGenerateUtils.sqlType2JavaType("binary_double"));
        Assert.assertEquals("BigDecimal", CodeGenerateUtils.sqlType2JavaType("decimal"));
        Assert.assertEquals("BigDecimal", CodeGenerateUtils.sqlType2JavaType("numeric"));
        Assert.assertEquals("BigDecimal", CodeGenerateUtils.sqlType2JavaType("real"));
        Assert.assertEquals("BigDecimal", CodeGenerateUtils.sqlType2JavaType("money"));
        Assert.assertEquals("BigDecimal", CodeGenerateUtils.sqlType2JavaType("smallmoney"));

        // 布尔、日期时间、二进制与字符串类型（验证 image -> Blob 修复）
        Assert.assertEquals("Boolean", CodeGenerateUtils.sqlType2JavaType("bit"));
        Assert.assertEquals("Boolean", CodeGenerateUtils.sqlType2JavaType("bool"));
        Assert.assertEquals("Boolean", CodeGenerateUtils.sqlType2JavaType("boolean"));
        Assert.assertEquals("LocalDateTime", CodeGenerateUtils.sqlType2JavaType("datetime"));
        Assert.assertEquals("LocalDateTime", CodeGenerateUtils.sqlType2JavaType("date"));
        Assert.assertEquals("LocalDateTime", CodeGenerateUtils.sqlType2JavaType("timestamp"));
        Assert.assertEquals("Blob", CodeGenerateUtils.sqlType2JavaType("image"));
        Assert.assertEquals("byte[]", CodeGenerateUtils.sqlType2JavaType("blob"));
        Assert.assertEquals("byte[]", CodeGenerateUtils.sqlType2JavaType("longblob"));
        Assert.assertEquals("byte[]", CodeGenerateUtils.sqlType2JavaType("bytea"));
        Assert.assertEquals("byte[]", CodeGenerateUtils.sqlType2JavaType("varbinary"));
        Assert.assertEquals("String", CodeGenerateUtils.sqlType2JavaType("varchar"));
        Assert.assertEquals("String", CodeGenerateUtils.sqlType2JavaType("json"));
        Assert.assertEquals("String", CodeGenerateUtils.sqlType2JavaType("jsonb"));
        Assert.assertEquals("String", CodeGenerateUtils.sqlType2JavaType("text"));
        Assert.assertEquals("String", CodeGenerateUtils.sqlType2JavaType("clob"));
        Assert.assertEquals("String", CodeGenerateUtils.sqlType2JavaType(""));
        Assert.assertEquals("String", CodeGenerateUtils.sqlType2JavaType(null));

        // 驼峰命名转换与空安全
        Assert.assertEquals("UserOrderRecord", CodeGenerateUtils.toUpper("user_order_record"));
        Assert.assertEquals("Id", CodeGenerateUtils.toUpper("id"));
        Assert.assertEquals("", CodeGenerateUtils.toUpper(""));
        Assert.assertEquals("", CodeGenerateUtils.toUpper(null));

        // 24 小时制时间格式校验
        String now = CodeGenerateUtils.getDate();
        Assert.assertTrue(now.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));

        // UTF-8 文件写入与中文防乱码校验
        File utf8File = new File(tempFolder.newFolder("io_test"), "sub/Chinese注释Test.java");
        CodeGenerateUtils.writeFile("// 中文注释 UTF-8 测试：syrobin", utf8File.getAbsolutePath());
        Assert.assertTrue(utf8File.exists());
        Assert.assertEquals("// 中文注释 UTF-8 测试：syrobin", Files.readString(utf8File.toPath(), StandardCharsets.UTF_8));
    }

    /**
     * 测试场景 2（P0）：JsonUtil 序列化/反序列化、PropertiesUtil 隔离读取与 Redis 离线优雅降级
     */
    @Test
    public void test02_P0JsonPropertiesAndRedisFallback() throws Exception {
        // 1. JsonUtil 序列化与反序列化
        BeanQuery bq = new BeanQuery();
        bq.setName("startDate");
        bq.setFormat("LocalDateTime");
        bq.setType("4");
        bq.setDescription("开始时间");

        String json = JsonUtil.json(Collections.singletonList(bq));
        List<BeanQuery> restored = JsonUtil.list(json, BeanQuery.class);
        Assert.assertNotNull(restored);
        Assert.assertEquals(1, restored.size());
        Assert.assertEquals(bq, restored.get(0));
        Assert.assertNull(JsonUtil.bean("", BeanQuery.class));
        Assert.assertNull(JsonUtil.list(null, BeanQuery.class));

        // 2. PropertiesUtil UTF-8 独立实例加载验证
        File projRoot = tempFolder.newFolder("prop_proj");
        File cfgDir = new File(projRoot, "src/main/resources/templates/config");
        Assert.assertTrue(cfgDir.mkdirs());
        Files.writeString(new File(cfgDir, "mysql.properties").toPath(),
                "url=jdbc:mysql://127.0.0.1:3306/test_db\ndatabase=test_db\nusername=syrobin\npassword=secret\npackageName=com.syrobin\n",
                StandardCharsets.UTF_8);
        PropertiesUtil mysqlProps = PropertiesUtil.getMySqlProperties(projRoot.getAbsolutePath() + "/");
        Assert.assertEquals("test_db", mysqlProps.getProperty("database"));
        Assert.assertEquals("com.syrobin", mysqlProps.getProperty("packageName"));

        // 3. RedisUtils 在未配置 redis.properties 时优雅返回 null 且不抛异常
        Assert.assertNull(RedisUtils.getJedis(projRoot.getAbsolutePath() + "/"));
        Assert.assertNull(RedisUtils.hget(null, "beanQuery", "user_order"));
        Assert.assertEquals(RedisUtils.JedisStatus.FAIL_LONG, RedisUtils.hset(null, "beanQuery", "user_order", "[]"));
    }

    /**
     * 测试场景 3（P1）：枚举注释解析器全格式与复杂边界测试
     */
    @Test
    public void test03_P1EnumCommentParserAllFormats() {
        // 1. 全角括号 + 全角冒号 + 全角逗号 + 空白噪点
        List<EnumsEntityContent> fullWidth = EnumCommentParser.parseEnumItems("类型（0：首页，1：百万年薪，2：报价方案，  3  ：  工保百科）");
        Assert.assertEquals(4, fullWidth.size());
        Assert.assertEquals("0", fullWidth.get(0).getState());
        Assert.assertEquals("首页", fullWidth.get(0).getNote());
        Assert.assertEquals("3", fullWidth.get(3).getState());
        Assert.assertEquals("工保百科", fullWidth.get(3).getNote());

        // 2. 半角括号 + 半角冒号 + 逗号/分号混用
        List<EnumsEntityContent> halfWidth = EnumCommentParser.parseEnumItems("订单状态(0:待支付, 1:已支付; 2:已取消)");
        Assert.assertEquals(3, halfWidth.size());
        Assert.assertEquals("2", halfWidth.get(2).getState());
        Assert.assertEquals("已取消", halfWidth.get(2).getNote());

        // 3. 中括号 + 等号 + 顿号
        List<EnumsEntityContent> bracketStyle = EnumCommentParser.parseEnumItems("审核结果[10=通过、20=驳回]");
        Assert.assertEquals(2, bracketStyle.size());

        // 4. 破折号分隔符
        List<EnumsEntityContent> dashStyle = EnumCommentParser.parseEnumItems("发货状态(1-待发货, 2-已发货)");
        Assert.assertEquals(2, dashStyle.size());
        Assert.assertEquals("已发货", dashStyle.get(1).getNote());

        // 5. 多重括号干扰（前置普通括号说明 + 后置枚举括号）
        List<EnumsEntityContent> multiParens = EnumCommentParser.parseEnumItems("结算标识(内部财务专用)（0：未结算，1：已结算）");
        Assert.assertEquals(2, multiParens.size());
        Assert.assertEquals("未结算", multiParens.get(0).getNote());

        // 6. 无枚举的普通注释与空值安全
        Assert.assertTrue(EnumCommentParser.parseEnumItems("普通备注字段(无键值对)").isEmpty());
        Assert.assertTrue(EnumCommentParser.parseEnumItems("").isEmpty());
        Assert.assertTrue(EnumCommentParser.parseEnumItems(null).isEmpty());

        // 7. 格式化预览输出
        String preview = EnumCommentParser.formatPreview("order_status", halfWidth);
        Assert.assertEquals("order_status -> [0:待支付, 1:已支付, 2:已取消]", preview);
    }

    /**
     * 测试场景 4（P1）：全部 13 个 Action 子类的目标模块预勾选契约验证
     */
    @Test
    public void test04_P1All13ActionsTargetConfiguration() throws Exception {
        Method configureMethod = BaseAnAction.class.getDeclaredMethod("configureTargets", Map.class);
        configureMethod.setAccessible(true);
        Method titleMethod = BaseAnAction.class.getDeclaredMethod("getTaskTitle");
        titleMethod.setAccessible(true);
        Method msgMethod = BaseAnAction.class.getDeclaredMethod("getSuccessMessage");
        msgMethod.setAccessible(true);
        Method fixedEnumMethod = BaseAnAction.class.getDeclaredMethod("shouldPromptFixedEnums");
        fixedEnumMethod.setAccessible(true);

        Map<BaseAnAction, String[]> actionExpectedTargets = new LinkedHashMap<>();
        actionExpectedTargets.put(new CodeAllAction(), new String[]{
                "controller", "entity", "entityBO", "entityVO", "entityQuery", "enums", "service", "serviceResults", "serviceQuery", "mapper"
        });
        actionExpectedTargets.put(new DdlFileGenerateAction(), new String[]{
                "controller", "entity", "entityBO", "entityVO", "entityQuery", "enums", "service", "serviceResults", "serviceQuery", "mapper"
        });
        actionExpectedTargets.put(new QueryAllAction(), new String[]{"entityQuery", "serviceQuery"});
        actionExpectedTargets.put(new EntityAction(), new String[]{"entity"});
        actionExpectedTargets.put(new EntityBOAction(), new String[]{"entityBO"});
        actionExpectedTargets.put(new EntityVOAction(), new String[]{"entityVO"});
        actionExpectedTargets.put(new EntityQueryAction(), new String[]{"entityQuery"});
        actionExpectedTargets.put(new EnumsAction(), new String[]{"enums"});
        actionExpectedTargets.put(new ControllerAction(), new String[]{"controller"});
        actionExpectedTargets.put(new ServiceAction(), new String[]{"service"});
        actionExpectedTargets.put(new ServiceQueryAction(), new String[]{"serviceQuery"});
        actionExpectedTargets.put(new ServiceResultsAction(), new String[]{"serviceResults"});
        actionExpectedTargets.put(new MapperAction(), new String[]{"mapper"});

        for (Map.Entry<BaseAnAction, String[]> entry : actionExpectedTargets.entrySet()) {
            BaseAnAction action = entry.getKey();
            Map<String, Object> params = new HashMap<>();
            configureMethod.invoke(action, params);

            for (String expectedKey : entry.getValue()) {
                Assert.assertEquals(action.getClass().getSimpleName() + " 应开启 " + expectedKey,
                        "y", params.get(expectedKey));
            }
            String title = (String) titleMethod.invoke(action);
            String msg = (String) msgMethod.invoke(action);
            Assert.assertFalse(title.isBlank());
            Assert.assertFalse(msg.isBlank());

            boolean promptFixed = (Boolean) fixedEnumMethod.invoke(action);
            Assert.assertEquals(action instanceof EnumsAction, promptFixed);
        }
    }

    /**
     * 测试场景 5（P2）：四大数据库方言识别与 Spring application.yml / properties 数据源自动扫描
     */
    @Test
    public void test05_P2MultiDialectAndSpringScanner() throws Exception {
        Assert.assertEquals(DatabaseDialect.MYSQL, DatabaseDialect.fromJdbcUrl("jdbc:mysql://127.0.0.1:3306/order_db?useSSL=false"));
        Assert.assertEquals(DatabaseDialect.POSTGRESQL, DatabaseDialect.fromJdbcUrl("jdbc:postgresql://127.0.0.1:5432/pg_db"));
        Assert.assertEquals(DatabaseDialect.ORACLE, DatabaseDialect.fromJdbcUrl("jdbc:oracle:thin:@127.0.0.1:1521:orcl"));
        Assert.assertEquals(DatabaseDialect.DM, DatabaseDialect.fromJdbcUrl("jdbc:dm://127.0.0.1:5236/DAMENG"));
        Assert.assertEquals(DatabaseDialect.MYSQL, DatabaseDialect.fromJdbcUrl(null));
        Assert.assertEquals("order_db", DatabaseDialect.extractDatabaseFromJdbcUrl("jdbc:mysql://127.0.0.1:3306/order_db?serverTimezone=UTC"));

        File projRoot = tempFolder.newFolder("spring_scan_proj");
        File resDir = new File(projRoot, "src/main/resources");
        Assert.assertTrue(resDir.mkdirs());
        Files.writeString(new File(resDir, "application.yml").toPath(),
                "spring:\n  datasource:\n    jdbc-url: jdbc:postgresql://192.168.1.10:5432/microservice_core\n    username: pg_admin\n    password: pg_secret\n",
                StandardCharsets.UTF_8);

        Map<String, String> scanned = DatabaseDialect.scanSpringDataSourceConfig(projRoot.getAbsolutePath());
        Assert.assertEquals("jdbc:postgresql://192.168.1.10:5432/microservice_core", scanned.get("url"));
        Assert.assertEquals("microservice_core", scanned.get("database"));
        Assert.assertEquals("pg_admin", scanned.get("username"));
        Assert.assertEquals("pg_secret", scanned.get("password"));
    }

    /**
     * 测试场景 6（P2）：增量代码合并引擎（字段追加、import 补齐、手写方法保留与二次合并幂等性）
     */
    @Test
    public void test06_P2IncrementalCodeMergerAndIdempotency() throws Exception {
        String existingJava =
                "package com.example.order.entity;\n\n" +
                "import lombok.Data;\n\n" +
                "@Data\n" +
                "public class UserOrder {\n" +
                "    private static final long serialVersionUID = 1L;\n\n" +
                "    /**\n" +
                "     * 主键ID\n" +
                "     */\n" +
                "    private Long id;\n\n" +
                "    // 开发者自定义业务方法\n" +
                "    public boolean isValidId() {\n" +
                "        return this.id != null && this.id > 0;\n" +
                "    }\n" +
                "}\n";

        String generatedV2 =
                "package com.example.order.entity;\n\n" +
                "import lombok.Data;\n" +
                "import java.math.BigDecimal;\n" +
                "import java.time.LocalDateTime;\n\n" +
                "@Data\n" +
                "public class UserOrder {\n" +
                "    private static final long serialVersionUID = 1L;\n\n" +
                "    /**\n" +
                "     * 主键ID\n" +
                "     */\n" +
                "    private Long id;\n\n" +
                "    /**\n" +
                "     * 订单金额\n" +
                "     */\n" +
                "    private BigDecimal orderAmount;\n\n" +
                "    /**\n" +
                "     * 支付时间\n" +
                "     */\n" +
                "    private LocalDateTime payTime;\n" +
                "}\n";

        // 1. 首次增量合并
        String mergedOnce = IncrementalCodeMerger.mergeJavaSource(existingJava, generatedV2);
        Assert.assertTrue(mergedOnce.contains("import java.math.BigDecimal;"));
        Assert.assertTrue(mergedOnce.contains("import java.time.LocalDateTime;"));
        Assert.assertTrue(mergedOnce.contains("private BigDecimal orderAmount;"));
        Assert.assertTrue(mergedOnce.contains("private LocalDateTime payTime;"));
        Assert.assertTrue(mergedOnce.contains("public boolean isValidId()"));

        // 2. 二次幂等合并验证（同一份生成代码再次合并，不应产生任何重复字段或重复 import）
        String mergedTwice = IncrementalCodeMerger.mergeJavaSource(mergedOnce, generatedV2);
        Assert.assertEquals(mergedOnce, mergedTwice);

        // 3. 文件级策略写入验证（INCREMENTAL_MERGE vs OVERWRITE）
        File targetFile = new File(tempFolder.newFolder("merge_test"), "UserOrder.java");
        IncrementalCodeMerger.writeWithStrategy(null, existingJava, targetFile.getAbsolutePath(),
                IncrementalCodeMerger.WriteStrategy.INCREMENTAL_MERGE);
        IncrementalCodeMerger.writeWithStrategy(null, generatedV2, targetFile.getAbsolutePath(),
                IncrementalCodeMerger.WriteStrategy.INCREMENTAL_MERGE);
        String diskMerged = Files.readString(targetFile.toPath(), StandardCharsets.UTF_8);
        Assert.assertTrue(diskMerged.contains("public boolean isValidId()"));
        Assert.assertTrue(diskMerged.contains("private BigDecimal orderAmount;"));

        IncrementalCodeMerger.writeWithStrategy(null, generatedV2, targetFile.getAbsolutePath(),
                IncrementalCodeMerger.WriteStrategy.OVERWRITE);
        String diskOverwritten = Files.readString(targetFile.toPath(), StandardCharsets.UTF_8);
        Assert.assertFalse(diskOverwritten.contains("public boolean isValidId()"));
    }

    /**
     * 测试场景 7（P3）：多表复杂 DDL SQL 离线解析（含表级复合主键、索引过滤、括号内逗号注释）
     */
    @Test
    public void test07_P3DdlSqlParserMultiTableAndConstraints() {
        String multiDdl =
                "CREATE TABLE IF NOT EXISTS `mall`.`user_order` (\n" +
                "  `id` bigint NOT NULL COMMENT '主键ID',\n" +
                "  `order_no` varchar(64) NOT NULL COMMENT '订单号',\n" +
                "  `pay_amount` decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '实付金额',\n" +
                "  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态（0：待付款，1：已付款，2：已发货）',\n" +
                "  PRIMARY KEY (`id`, `order_no`),\n" +
                "  UNIQUE KEY `uk_order_no` (`order_no`),\n" +
                "  KEY `idx_status` (`status`)\n" +
                ") ENGINE=InnoDB COMMENT='订单主表';\n\n" +
                "CREATE TABLE `payment_record` (\n" +
                "  `record_id` bigint PRIMARY KEY COMMENT '流水ID',\n" +
                "  `channel` varchar(32) NOT NULL COMMENT '支付渠道(1:微信, 2:支付宝)'\n" +
                ") COMMENT '支付流水表';";

        Map<String, DdlSqlParser.ParsedTable> map = DdlSqlParser.parseDdlScript(multiDdl);
        Assert.assertEquals(2, map.size());
        Assert.assertTrue(map.containsKey("user_order"));
        Assert.assertTrue(map.containsKey("payment_record"));

        DdlSqlParser.ParsedTable orderTable = map.get("user_order");
        Assert.assertEquals("订单主表", orderTable.getTableComment());
        // 确保 PRIMARY KEY / UNIQUE KEY / KEY 索引行未被误解析为字段
        Assert.assertEquals(4, orderTable.getColumns().size());
        Assert.assertTrue(orderTable.getColumns().get(0).getPrimaryKey());
        Assert.assertTrue(orderTable.getColumns().get(1).getPrimaryKey());
        Assert.assertEquals(Integer.valueOf(64), orderTable.getColumns().get(1).getMaxLength());

        DdlSqlParser.ParsedTable payTable = map.get("payment_record");
        Assert.assertEquals("支付流水表", payTable.getTableComment());
        Assert.assertEquals(2, payTable.getColumns().size());
    }

    /**
     * 测试场景 8（P3）：全量 10 种 ServiceQuery 查询类型 + Swagger2/OpenAPI3 + Boot2/Boot3 全链路代码生成验证
     */
    @Test
    public void test08_FullChainGenerationAllQueryTypesAndDocStyles() throws Exception {
        String ddl =
                "CREATE TABLE `user_order` (\n" +
                "  `id` bigint NOT NULL PRIMARY KEY COMMENT '主键ID',\n" +
                "  `order_no` varchar(64) NOT NULL COMMENT '订单编号',\n" +
                "  `customer_mobile` varchar(20) NOT NULL COMMENT '客户手机号',\n" +
                "  `customer_email` varchar(128) DEFAULT NULL COMMENT '客户邮箱',\n" +
                "  `order_amount` decimal(12,2) NOT NULL COMMENT '订单金额',\n" +
                "  `order_status` tinyint NOT NULL DEFAULT 0 COMMENT '订单状态（0：待支付，1：已支付，2：已取消）',\n" +
                "  `audit_status` tinyint NOT NULL DEFAULT 0 COMMENT '审核状态(10:待审, 20:通过)',\n" +
                "  `create_date_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户订单表';";

        Map<String, DdlSqlParser.ParsedTable> parsedMap = DdlSqlParser.parseDdlScript(ddl);

        // 构造覆盖全部 0~9 共 10 种类型的 ServiceQuery 列表
        List<ServiceQuery> allServiceQueries = new ArrayList<>();
        for (int t = 0; t <= 9; t++) {
            ServiceQuery sq = new ServiceQuery();
            sq.setType(String.valueOf(t));
            sq.setColumn("`order_no`");
            sq.setParameter("OrderNo");
            sq.setParameterType(t % 2 == 0);
            sq.setSql("\"SELECT order_no FROM sub_order\"");
            sq.setStart("StartTime");
            sq.setEnd("EndTime");
            allServiceQueries.add(sq);
        }

        List<BeanQuery> beanQueries = new ArrayList<>();
        String[] customFields = {"StartTime", "EndTime"};
        for (String cf : customFields) {
            BeanQuery bq = new BeanQuery();
            bq.setName(StringUtilsUncapitalize(cf));
            bq.setFormat("LocalDateTime");
            bq.setType("4");
            bq.setDescription("区间查询-" + cf);
            beanQueries.add(bq);
        }

        // 1. 验证 OPENAPI3 + Spring Boot 3 (jakarta.validation) + fixedEnums 全链路生成
        File openApiProj = tempFolder.newFolder("openapi3_boot3_proj");
        Map<String, Object> params = buildFullEnableParams(parsedMap, beanQueries, allServiceQueries);
        params.put("docStyle", "OPENAPI3");
        params.put("bootVersion", "3");
        params.put("enableValidation", true);
        params.put("fixedEnums", "order_status"); // 限定仅生成 order_status 的枚举类

        CodeGenerate.foundCode(null, "user_order", "order", "syrobin", params, openApiProj.getAbsolutePath() + "/");

        String baseDir = openApiProj.getAbsolutePath() + "/src/main/java/com/example/order/";
        String boContent = Files.readString(new File(baseDir + "entity/bo/UserOrderBO.java").toPath(), StandardCharsets.UTF_8);
        Assert.assertTrue(boContent.contains("import io.swagger.v3.oas.annotations.media.Schema;"));
        Assert.assertTrue(boContent.contains("import jakarta.validation.constraints.*;"));
        Assert.assertTrue(boContent.contains("@Schema(name = \"UserOrderBO\""));
        Assert.assertTrue(boContent.contains("@NotBlank(message = \"订单编号不能为空\")"));
        Assert.assertTrue(boContent.contains("@Size(max = 64"));
        Assert.assertTrue(boContent.contains("@Pattern(regexp = \"^1[3-9]\\\\d{9}$\""));
        Assert.assertTrue(boContent.contains("@Email(message = \"客户邮箱格式不正确\")"));
        Assert.assertTrue(boContent.contains("@PositiveOrZero(message = \"订单金额不能为负数\")"));
        Assert.assertTrue(boContent.contains("@author syrobin"));

        // 验证 fixedEnums 生效：仅生成了 UserOrderOrderStatusEnum.java，未生成 UserOrderAuditStatusEnum.java
        Assert.assertTrue(new File(baseDir + "entity/enums/UserOrderOrderStatusEnum.java").exists());
        Assert.assertFalse(new File(baseDir + "entity/enums/UserOrderAuditStatusEnum.java").exists());

        // 验证 0~9 全部 10 种 ServiceQuery 在 UserOrderServiceQuery.java 中均正确渲染
        String sqContent = Files.readString(new File(baseDir + "service/query/UserOrderServiceQuery.java").toPath(), StandardCharsets.UTF_8);
        Assert.assertTrue(sqContent.contains("queryWrapper.eq("));
        Assert.assertTrue(sqContent.contains("queryWrapper.likeRight("));
        Assert.assertTrue(sqContent.contains("queryWrapper.isNull("));
        Assert.assertTrue(sqContent.contains("queryWrapper.isNotNull("));
        Assert.assertTrue(sqContent.contains("queryWrapper.inSql("));
        Assert.assertTrue(sqContent.contains("queryWrapper.notIn("));
        Assert.assertTrue(sqContent.contains("queryWrapper.notInSql("));
        Assert.assertTrue(sqContent.contains("queryWrapper.between("));
        Assert.assertTrue(sqContent.contains("queryWrapper.notBetween("));

        // 2. 验证 SWAGGER2 + Spring Boot 2 (javax.validation) 全链路生成
        File swagger2Proj = tempFolder.newFolder("swagger2_boot2_proj");
        Map<String, Object> swaggerParams = buildFullEnableParams(parsedMap, beanQueries, allServiceQueries);
        swaggerParams.put("docStyle", "SWAGGER2");
        swaggerParams.put("bootVersion", "2");
        swaggerParams.put("enableValidation", true);

        CodeGenerate.foundCode(null, "user_order", "order", "syrobin", swaggerParams, swagger2Proj.getAbsolutePath() + "/");
        String swBaseDir = swagger2Proj.getAbsolutePath() + "/src/main/java/com/example/order/";
        String swBoContent = Files.readString(new File(swBaseDir + "entity/bo/UserOrderBO.java").toPath(), StandardCharsets.UTF_8);
        Assert.assertTrue(swBoContent.contains("import io.swagger.annotations.ApiModel;"));
        Assert.assertTrue(swBoContent.contains("import javax.validation.constraints.*;"));
        Assert.assertTrue(swBoContent.contains("@ApiModelProperty(value = \"订单编号\")"));

        // 未设置 fixedEnums 时，两个枚举字段均应生成枚举类
        Assert.assertTrue(new File(swBaseDir + "entity/enums/UserOrderOrderStatusEnum.java").exists());
        Assert.assertTrue(new File(swBaseDir + "entity/enums/UserOrderAuditStatusEnum.java").exists());
    }

    private Map<String, Object> buildFullEnableParams(Map<String, DdlSqlParser.ParsedTable> parsedMap,
                                                      List<BeanQuery> beanQueries,
                                                      List<ServiceQuery> serviceQueries) {
        Map<String, Object> params = new HashMap<>();
        params.put("controller", "y");
        params.put("entity", "y");
        params.put("entityBO", "y");
        params.put("entityVO", "y");
        params.put("entityQuery", "y");
        params.put("enums", "y");
        params.put("service", "y");
        params.put("serviceResults", "y");
        params.put("serviceQuery", "y");
        params.put("mapper", "y");
        params.put("parsedDdlTables", parsedMap);
        params.put("beanQuery", beanQueries);
        params.put("serviceQueryList", serviceQueries);
        return params;
    }

    private String StringUtilsUncapitalize(String s) {
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
