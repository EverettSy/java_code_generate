package com.code;

import com.code.action.*;
import com.code.config.CodeGenSettingsState;
import com.code.i18n.CodeGenBundle;
import com.code.spi.CodeGenPostProcessor;
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
 * Syrobin Java Code Generate 全方位自动化测试套件（覆盖 P0 ~ P3、v3.0 M1/M2/M3/FeignClient 及 v4.0 M1/M2/M3 全部核心特性）
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

        Assert.assertEquals("Float", CodeGenerateUtils.sqlType2JavaType("float"));
        Assert.assertEquals("Float", CodeGenerateUtils.sqlType2JavaType("float4"));
        Assert.assertEquals("Double", CodeGenerateUtils.sqlType2JavaType("double"));
        Assert.assertEquals("Double", CodeGenerateUtils.sqlType2JavaType("float8"));
        Assert.assertEquals("Double", CodeGenerateUtils.sqlType2JavaType("binary_double"));
        Assert.assertEquals("Double", CodeGenerateUtils.sqlType2JavaType("double precision"));
        Assert.assertEquals("BigDecimal", CodeGenerateUtils.sqlType2JavaType("decimal"));
        Assert.assertEquals("BigDecimal", CodeGenerateUtils.sqlType2JavaType("numeric"));
        Assert.assertEquals("BigDecimal", CodeGenerateUtils.sqlType2JavaType("real"));
        Assert.assertEquals("BigDecimal", CodeGenerateUtils.sqlType2JavaType("money"));
        Assert.assertEquals("BigDecimal", CodeGenerateUtils.sqlType2JavaType("smallmoney"));

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
        Assert.assertEquals("String", CodeGenerateUtils.sqlType2JavaType("character varying"));
        Assert.assertEquals("String", CodeGenerateUtils.sqlType2JavaType("json"));
        Assert.assertEquals("String", CodeGenerateUtils.sqlType2JavaType("jsonb"));
        Assert.assertEquals("String", CodeGenerateUtils.sqlType2JavaType("text"));
        Assert.assertEquals("String", CodeGenerateUtils.sqlType2JavaType("clob"));
        Assert.assertEquals("String", CodeGenerateUtils.sqlType2JavaType(""));
        Assert.assertEquals("String", CodeGenerateUtils.sqlType2JavaType(null));

        Assert.assertEquals("UserOrderRecord", CodeGenerateUtils.toUpper("user_order_record"));
        Assert.assertEquals("Id", CodeGenerateUtils.toUpper("id"));
        Assert.assertEquals("", CodeGenerateUtils.toUpper(""));
        Assert.assertEquals("", CodeGenerateUtils.toUpper(null));

        String now = CodeGenerateUtils.getDate();
        Assert.assertTrue(now.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));

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

        File projRoot = tempFolder.newFolder("prop_proj");
        File cfgDir = new File(projRoot, "src/main/resources/templates/config");
        Assert.assertTrue(cfgDir.mkdirs());
        Files.writeString(new File(cfgDir, "mysql.properties").toPath(),
                "url=jdbc:mysql://127.0.0.1:3306/test_db\ndatabase=test_db\nusername=syrobin\npassword=secret\npackageName=com.syrobin\n",
                StandardCharsets.UTF_8);
        PropertiesUtil mysqlProps = PropertiesUtil.getMySqlProperties(projRoot.getAbsolutePath() + "/");
        Assert.assertEquals("test_db", mysqlProps.getProperty("database"));
        Assert.assertEquals("com.syrobin", mysqlProps.getProperty("packageName"));

        Assert.assertNull(RedisUtils.openSession(projRoot.getAbsolutePath() + "/"));
        Assert.assertNull(RedisUtils.hget(null, "beanQuery", "user_order"));
        Assert.assertEquals(RedisUtils.Status.FAIL_LONG, RedisUtils.hset(null, "beanQuery", "user_order", "[]"));
    }

    /**
     * 测试场景 3（P1）：枚举注释解析器全格式与复杂边界测试
     */
    @Test
    public void test03_P1EnumCommentParserAllFormats() {
        List<EnumsEntityContent> fullWidth = EnumCommentParser.parseEnumItems("类型（0：首页，1：百万年薪，2：报价方案，  3  ：  工保百科）");
        Assert.assertEquals(4, fullWidth.size());
        Assert.assertEquals("0", fullWidth.get(0).getState());
        Assert.assertEquals("首页", fullWidth.get(0).getNote());
        Assert.assertEquals("3", fullWidth.get(3).getState());
        Assert.assertEquals("工保百科", fullWidth.get(3).getNote());

        List<EnumsEntityContent> halfWidth = EnumCommentParser.parseEnumItems("订单状态(0:待支付, 1:已支付; 2:已取消)");
        Assert.assertEquals(3, halfWidth.size());
        Assert.assertEquals("2", halfWidth.get(2).getState());
        Assert.assertEquals("已取消", halfWidth.get(2).getNote());

        List<EnumsEntityContent> bracketStyle = EnumCommentParser.parseEnumItems("审核结果[10=通过、20=驳回]");
        Assert.assertEquals(2, bracketStyle.size());

        List<EnumsEntityContent> dashStyle = EnumCommentParser.parseEnumItems("发货状态(1-待发货, 2-已发货)");
        Assert.assertEquals(2, dashStyle.size());
        Assert.assertEquals("已发货", dashStyle.get(1).getNote());

        List<EnumsEntityContent> multiParens = EnumCommentParser.parseEnumItems("结算标识(内部财务专用)（0：未结算，1：已结算）");
        Assert.assertEquals(2, multiParens.size());
        Assert.assertEquals("未结算", multiParens.get(0).getNote());

        Assert.assertTrue(EnumCommentParser.parseEnumItems("普通备注字段(无键值对)").isEmpty());
        Assert.assertTrue(EnumCommentParser.parseEnumItems("").isEmpty());
        Assert.assertTrue(EnumCommentParser.parseEnumItems(null).isEmpty());

        String preview = EnumCommentParser.formatPreview("order_status", halfWidth);
        Assert.assertEquals("order_status -> [0:待支付, 1:已支付, 2:已取消]", preview);
    }

    /**
     * 测试场景 4（P1）：全部 19 个 BaseAnAction 子类的目标模块预勾选契约验证
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
        actionExpectedTargets.put(new FeignClientAction(), new String[]{"feignClient"});
        actionExpectedTargets.put(new ServiceAction(), new String[]{"service"});
        actionExpectedTargets.put(new ServiceQueryAction(), new String[]{"serviceQuery"});
        actionExpectedTargets.put(new ServiceResultsAction(), new String[]{"serviceResults"});
        actionExpectedTargets.put(new MapperAction(), new String[]{"mapper"});
        actionExpectedTargets.put(new HttpClientAction(), new String[]{"httpClient"});
        actionExpectedTargets.put(new ExcelDtoAction(), new String[]{"excelDto"});
        actionExpectedTargets.put(new FrontendVueAction(), new String[]{"frontendApi", "frontendVue"});
        actionExpectedTargets.put(new UnitTestAction(), new String[]{"unitTest"});
        actionExpectedTargets.put(new FlywayMigrationAction(), new String[]{"flywayMigration"});

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

        String mergedOnce = IncrementalCodeMerger.mergeJavaSource(existingJava, generatedV2);
        Assert.assertTrue(mergedOnce.contains("import java.math.BigDecimal;"));
        Assert.assertTrue(mergedOnce.contains("import java.time.LocalDateTime;"));
        Assert.assertTrue(mergedOnce.contains("private BigDecimal orderAmount;"));
        Assert.assertTrue(mergedOnce.contains("private LocalDateTime payTime;"));
        Assert.assertTrue(mergedOnce.contains("public boolean isValidId()"));

        String mergedTwice = IncrementalCodeMerger.mergeJavaSource(mergedOnce, generatedV2);
        Assert.assertEquals(mergedOnce, mergedTwice);

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

        File openApiProj = tempFolder.newFolder("openapi3_boot3_proj");
        Map<String, Object> params = buildFullEnableParams(parsedMap, beanQueries, allServiceQueries);
        params.put("docStyle", "OPENAPI3");
        params.put("bootVersion", "3");
        params.put("enableValidation", true);
        params.put("fixedEnums", "order_status");

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

        Assert.assertTrue(new File(baseDir + "entity/enums/UserOrderOrderStatusEnum.java").exists());
        Assert.assertFalse(new File(baseDir + "entity/enums/UserOrderAuditStatusEnum.java").exists());

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

        Assert.assertTrue(new File(swBaseDir + "entity/enums/UserOrderOrderStatusEnum.java").exists());
        Assert.assertTrue(new File(swBaseDir + "entity/enums/UserOrderAuditStatusEnum.java").exists());
    }

    /**
     * 测试场景 9（v3.0 M1）：表前缀剥离、BaseEntity 基类继承与公共列过滤、多模块分层路由、内存预渲染及团队规范契约 (.syrobin-codegen.json)
     */
    @Test
    public void test09_V3M1TablePrefixBaseEntityMultiModuleAndTeamContract() throws Exception {
        Assert.assertEquals("UserOrder", CodeGenerateUtils.toUpper("t_user_order", "t_,sys_,biz_"));
        Assert.assertEquals("ArticleCategory", CodeGenerateUtils.toUpper("sys_article_category", "t_,sys_,biz_"));
        Assert.assertEquals("TUserOrder", CodeGenerateUtils.toUpper("t_user_order", ""));
        Assert.assertEquals("FinanceInvoice2", CodeGenerateUtils.toUpper("SYS_FINANCE_INVOICE2", "sys_"));
        Assert.assertEquals("UserOrder", CodeGenerateUtils.toUpper("t_userOrder", "t_"));

        String ddl =
                "CREATE TABLE `t_user_order` (\n" +
                "  `id` bigint NOT NULL PRIMARY KEY COMMENT '主键ID',\n" +
                "  `order_no` varchar(64) NOT NULL COMMENT '订单编号',\n" +
                "  `order_status` tinyint NOT NULL DEFAULT 0 COMMENT '状态（0：待支付，1：已支付）',\n" +
                "  `create_date_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'\n" +
                ") COMMENT='用户订单表';";

        Map<String, DdlSqlParser.ParsedTable> parsedMap = DdlSqlParser.parseDdlScript(ddl);
        File rootWorkspace = tempFolder.newFolder("multi_module_workspace");
        File daoModule = new File(rootWorkspace, "order-dao");
        File apiModule = new File(rootWorkspace, "order-api");
        File webModule = new File(rootWorkspace, "order-web");
        Assert.assertTrue(new File(daoModule, "src/main/java").mkdirs());
        Assert.assertTrue(new File(apiModule, "src/main/java").mkdirs());
        Assert.assertTrue(new File(webModule, "src/main/java").mkdirs());

        Map<String, String> discovered = CodeGenerateUtils.discoverSubModules(null, daoModule.getAbsolutePath());
        Assert.assertTrue(discovered.containsKey("order-dao"));
        Assert.assertTrue(discovered.containsKey("order-api"));
        Assert.assertTrue(discovered.containsKey("order-web"));

        Map<String, Object> params = buildFullEnableParams(parsedMap, Collections.emptyList(), Collections.emptyList());
        params.put("tablePrefixes", "t_,sys_");
        params.put("baseEntityClass", "com.example.common.entity.BaseEntity");
        params.put("ignoredBaseColumns", "id,create_date_time");

        Map<String, String> routing = new LinkedHashMap<>();
        routing.put("entity", daoModule.getAbsolutePath());
        routing.put("mapper", daoModule.getAbsolutePath());
        routing.put("entityBO", apiModule.getAbsolutePath());
        routing.put("entityVO", apiModule.getAbsolutePath());
        routing.put("controller", webModule.getAbsolutePath());
        params.put("layerModuleRouting", routing);

        Map<String, String> dryRunFiles = CodeGenerate.renderAllTargetFilesInMemory(
                null, "t_user_order", "order", "syrobin", params, daoModule.getAbsolutePath() + "/", null
        );
        Assert.assertFalse(dryRunFiles.isEmpty());
        Assert.assertFalse(new File(daoModule, "src/main/java/com/example/order/entity/UserOrder.java").exists());

        CodeGenerate.foundCode(null, "t_user_order", "order", "syrobin", params, daoModule.getAbsolutePath() + "/");

        File entityFile = new File(daoModule, "src/main/java/com/example/order/entity/UserOrder.java");
        File boFile = new File(apiModule, "src/main/java/com/example/order/entity/bo/UserOrderBO.java");
        File controllerFile = new File(webModule, "src/main/java/com/example/order/controller/UserOrderController.java");
        Assert.assertTrue(entityFile.exists());
        Assert.assertTrue(boFile.exists());
        Assert.assertTrue(controllerFile.exists());

        String entitySource = Files.readString(entityFile.toPath(), StandardCharsets.UTF_8);
        Assert.assertTrue(entitySource.contains("import com.example.common.entity.BaseEntity;"));
        Assert.assertTrue(entitySource.contains("public class UserOrder extends BaseEntity implements Serializable"));
        Assert.assertTrue(entitySource.contains("@TableName(\"t_user_order\")"));
        Assert.assertFalse(entitySource.contains("private Long id;"));
        Assert.assertFalse(entitySource.contains("private LocalDateTime createDateTime;"));
        Assert.assertTrue(entitySource.contains("private String orderNo;"));

        String boSource = Files.readString(boFile.toPath(), StandardCharsets.UTF_8);
        Assert.assertTrue(boSource.contains("private Long id;"));
        Assert.assertTrue(boSource.contains("private String orderNo;"));

        CodeGenSettingsState settings = new CodeGenSettingsState();
        CodeGenSettingsState.ConfigState st = settings.getState();
        Assert.assertNotNull(st);
        st.packageName = "com.syrobin.cloud";
        st.tablePrefixes = "t_,sys_";
        st.baseEntityClass = "com.syrobin.common.BaseEntity";
        st.storeCredential("top_secret_db_password");

        File contractFile = settings.exportTeamContract(rootWorkspace.getAbsolutePath());
        Assert.assertTrue(contractFile.exists());
        String contractJson = Files.readString(contractFile.toPath(), StandardCharsets.UTF_8);
        Assert.assertTrue(contractJson.contains("com.syrobin.cloud"));
        Assert.assertFalse(contractJson.contains("top_secret_db_password"));

        CodeGenSettingsState restoredSettings = new CodeGenSettingsState();
        Assert.assertTrue(restoredSettings.importTeamContract(rootWorkspace.getAbsolutePath()));
        Assert.assertEquals("com.syrobin.cloud", restoredSettings.getState().packageName);
        Assert.assertEquals("t_,sys_", restoredSettings.getState().tablePrefixes);
        Assert.assertEquals("com.syrobin.common.BaseEntity", restoredSettings.getState().baseEntityClass);
    }

    /**
     * 测试场景 10（v3.0 M2）：PostgreSQL/Oracle 表外 COMMENT ON + ALTER TABLE ADD COLUMN 解析、
     * Mapper.xml + MapStruct Converter 生成、20 套模板实时沙箱预览、动态驱动发现与 Database 面板节点提取
     */
    @Test
    public void test10_V3M2PostgresOracleDdlMapperXmlMapStructAndLivePreview() throws Exception {
        String pgOracleDdl =
                "CREATE TABLE \"PUBLIC\".\"FINANCE_INVOICE\" (\n" +
                "  \"ID\" NUMBER(19) NOT NULL PRIMARY KEY,\n" +
                "  \"INVOICE_CODE\" VARCHAR2(64 BYTE) NOT NULL,\n" +
                "  \"TOTAL_AMOUNT\" NUMBER(12, 2) NOT NULL,\n" +
                "  \"RETRY_COUNT\" NUMBER(4) DEFAULT 0,\n" +
                "  \"IS_VERIFIED\" NUMBER(1) DEFAULT 0\n" +
                ");\n" +
                "ALTER TABLE \"PUBLIC\".\"FINANCE_INVOICE\" ADD COLUMN \"INVOICE_STATUS\" SMALLINT NOT NULL;\n" +
                "COMMENT ON TABLE \"PUBLIC\".\"FINANCE_INVOICE\" IS '财务发票主表';\n" +
                "COMMENT ON COLUMN \"PUBLIC\".\"FINANCE_INVOICE\".\"INVOICE_CODE\" IS '发票代码';\n" +
                "COMMENT ON COLUMN \"PUBLIC\".\"FINANCE_INVOICE\".\"TOTAL_AMOUNT\" IS '发票总金额';\n" +
                "COMMENT ON COLUMN \"PUBLIC\".\"FINANCE_INVOICE\".\"INVOICE_STATUS\" IS '发票状态（0：待开具，1：已开具，2：已作废）';\n";

        Map<String, DdlSqlParser.ParsedTable> parsedMap = DdlSqlParser.parseDdlScript(pgOracleDdl);
        Assert.assertEquals(1, parsedMap.size());
        DdlSqlParser.ParsedTable invoiceTable = parsedMap.get("FINANCE_INVOICE");
        Assert.assertNotNull(invoiceTable);
        Assert.assertEquals("财务发票主表", invoiceTable.getTableComment());
        Assert.assertEquals(6, invoiceTable.getColumns().size());

        Assert.assertEquals("Long", invoiceTable.getColumns().get(0).getDataType());
        Assert.assertEquals("String", invoiceTable.getColumns().get(1).getDataType());
        Assert.assertEquals("发票代码", invoiceTable.getColumns().get(1).getColumnComment());
        Assert.assertEquals("BigDecimal", invoiceTable.getColumns().get(2).getDataType());
        Assert.assertEquals("Integer", invoiceTable.getColumns().get(3).getDataType());
        Assert.assertEquals("Boolean", invoiceTable.getColumns().get(4).getDataType());
        Assert.assertEquals("Short", invoiceTable.getColumns().get(5).getDataType());
        Assert.assertEquals("发票状态（0：待开具，1：已开具，2：已作废）", invoiceTable.getColumns().get(5).getColumnComment());

        File m2Proj = tempFolder.newFolder("m2_mapstruct_proj");
        Map<String, Object> params = buildFullEnableParams(parsedMap, Collections.emptyList(), Collections.emptyList());
        params.put("mapperXml", "y");
        params.put("converter", "y");
        params.put("mapperStrategy", "MAPSTRUCT");

        CodeGenerate.foundCode(null, "FINANCE_INVOICE", "finance", "syrobin", params, m2Proj.getAbsolutePath() + "/");

        File mapperXmlFile = new File(m2Proj, "src/main/resources/mapper/FinanceInvoiceMapper.xml");
        File converterFile = new File(m2Proj, "src/main/java/com/example/finance/entity/converter/FinanceInvoiceConverter.java");
        File serviceImplFile = new File(m2Proj, "src/main/java/com/example/finance/service/impl/FinanceInvoiceServiceImpl.java");
        File serviceResultsFile = new File(m2Proj, "src/main/java/com/example/finance/service/results/FinanceInvoiceServiceResults.java");

        Assert.assertTrue(mapperXmlFile.exists());
        Assert.assertTrue(converterFile.exists());
        Assert.assertTrue(serviceImplFile.exists());
        Assert.assertTrue(serviceResultsFile.exists());

        String xmlContent = Files.readString(mapperXmlFile.toPath(), StandardCharsets.UTF_8);
        Assert.assertTrue("生成的 Mapper XML 命名空间不匹配：\n" + xmlContent,
                xmlContent.contains("<mapper namespace=\"com.example.finance.mapper.FinanceInvoiceMapper\">"));
        Assert.assertTrue(xmlContent.contains("<id column=\"ID\" property=\"id\" />"));
        Assert.assertTrue(xmlContent.contains("<result column=\"TOTAL_AMOUNT\" property=\"totalAmount\" />"));

        String converterContent = Files.readString(converterFile.toPath(), StandardCharsets.UTF_8);
        Assert.assertTrue(converterContent.contains("@Mapper(componentModel = \"spring\""));
        Assert.assertTrue(converterContent.contains("FinanceInvoice boToEntity(FinanceInvoiceBO bo);"));

        String serviceImplContent = Files.readString(serviceImplFile.toPath(), StandardCharsets.UTF_8);
        Assert.assertTrue(serviceImplContent.contains("FinanceInvoiceConverter.INSTANCE.boToEntity("));
        Assert.assertTrue(serviceImplContent.contains("FinanceInvoiceConverter.INSTANCE.queryToEntity("));

        String resultsContent = Files.readString(serviceResultsFile.toPath(), StandardCharsets.UTF_8);
        Assert.assertTrue(resultsContent.contains("FinanceInvoiceConverter.INSTANCE.entityToVO("));
        Assert.assertTrue(resultsContent.contains("FinanceInvoiceConverter.INSTANCE.entityListToVOList("));

        Assert.assertEquals(20, CodeGenSettingsState.TEMPLATE_NAMES.length);
        for (String tplName : CodeGenSettingsState.TEMPLATE_NAMES) {
            String tplContent = CodeGenSettingsState.loadBuiltInTemplate(tplName);
            Assert.assertFalse("内置模板不应为空: " + tplName, tplContent.isBlank());
            String rendered = CodeGenerate.renderSamplePreview(
                    tplName, tplContent, "3", "OPENAPI3", "MAPSTRUCT", true, "com.example.common.BaseEntity"
            );
            Assert.assertFalse("模板实时沙箱渲染不应为空: " + tplName, rendered.isBlank());
        }

        File fakeRepo = tempFolder.newFolder("fake_m2_repo");
        File pgDir = new File(fakeRepo, "org/postgresql/postgresql/42.7.3");
        Assert.assertTrue(pgDir.mkdirs());
        File fakePgJar = new File(pgDir, "postgresql-42.7.3.jar");
        Assert.assertTrue(fakePgJar.createNewFile());
        File discoveredJar = DatabaseDialect.POSTGRESQL.findDriverJarInLocalRepositories(fakeRepo);
        Assert.assertNotNull(discoveredJar);
        Assert.assertEquals("postgresql-42.7.3.jar", discoveredJar.getName());

        Object mockDbTable = new MockDasTable("orders");
        List<String> extractedTables = DatabaseToolWindowGenerateAction.extractTableNamesFromObjects(new Object[]{mockDbTable});
        Assert.assertEquals(Collections.singletonList("orders"), extractedTables);
    }

    /**
     * 测试场景 11（v3.0 M3）：主子表 One-to-Many 级联生成、前端 TypeScript/Axios API 生成、LocalHistory 一键事务回滚与 CodeGenBundle 中英双语国际化
     */
    @Test
    public void test11_V3M3MasterDetailSubTableFrontendTsLocalHistoryAndI18n() throws Exception {
        String ddl =
                "CREATE TABLE `user_order` (\n" +
                "  `id` bigint NOT NULL PRIMARY KEY COMMENT '主键ID',\n" +
                "  `order_no` varchar(64) NOT NULL COMMENT '订单编号',\n" +
                "  `order_amount` decimal(12,2) NOT NULL COMMENT '订单金额'\n" +
                ") COMMENT='用户订单表';\n" +
                "CREATE TABLE `user_order_item` (\n" +
                "  `id` bigint NOT NULL PRIMARY KEY COMMENT '明细ID',\n" +
                "  `order_id` bigint NOT NULL COMMENT '关联主订单ID',\n" +
                "  `sku_name` varchar(128) NOT NULL COMMENT '商品名称'\n" +
                ") COMMENT='用户订单明细表';";

        Map<String, DdlSqlParser.ParsedTable> parsedMap = DdlSqlParser.parseDdlScript(ddl);
        File m3Proj = tempFolder.newFolder("m3_master_detail_proj");

        File preExistingEntity = new File(m3Proj, "src/main/java/com/example/order/entity/UserOrder.java");
        String originalHandWrittenEntity =
                "package com.example.order.entity;\n\n" +
                "public class UserOrder {\n" +
                "    private Long id;\n" +
                "    public String customHandWrittenMethod() { return \"keep_me\"; }\n" +
                "}\n";
        CodeGenerateUtils.writeFile(originalHandWrittenEntity, preExistingEntity.getAbsolutePath());

        Map<String, Object> params = buildFullEnableParams(parsedMap, Collections.emptyList(), Collections.emptyList());
        params.put("frontendApi", "y");
        params.put("docStyle", "OPENAPI3");
        params.put("subTableRelations", Collections.singletonList(
                new SubTableRelation("user_order_item", "order_id", "id", "订单商品明细集合")
        ));

        LocalHistoryHelper.clearHistory(null);
        LocalHistoryHelper.startGenerationBatch(null, "user_order");
        CodeGenerate.foundCode(null, "user_order", "order", "syrobin", params, m3Proj.getAbsolutePath() + "/");
        LocalHistoryHelper.finishGenerationBatch(null);

        File boFile = new File(m3Proj, "src/main/java/com/example/order/entity/bo/UserOrderBO.java");
        File voFile = new File(m3Proj, "src/main/java/com/example/order/entity/vo/UserOrderVO.java");
        File serviceImplFile = new File(m3Proj, "src/main/java/com/example/order/service/impl/UserOrderServiceImpl.java");
        File tsApiFile = new File(m3Proj, "src/main/resources/frontend/userOrder.api.ts");

        Assert.assertTrue(boFile.exists());
        Assert.assertTrue(voFile.exists());
        Assert.assertTrue(serviceImplFile.exists());
        Assert.assertTrue(tsApiFile.exists());

        String boSource = Files.readString(boFile.toPath(), StandardCharsets.UTF_8);
        Assert.assertTrue(boSource.contains("private List<UserOrderItemBO> userOrderItemList;"));

        String voSource = Files.readString(voFile.toPath(), StandardCharsets.UTF_8);
        Assert.assertTrue(voSource.contains("private List<UserOrderItemVO> userOrderItemList;"));

        String serviceImplSource = Files.readString(serviceImplFile.toPath(), StandardCharsets.UTF_8);
        Assert.assertTrue(serviceImplSource.contains("private UserOrderItemService userOrderItemService;"));
        Assert.assertTrue(serviceImplSource.contains("subItem.setOrderId(userOrder.getId());"));
        Assert.assertTrue(serviceImplSource.contains("userOrderItemService.saveEnhance(subItem);"));

        String tsSource = Files.readString(tsApiFile.toPath(), StandardCharsets.UTF_8);
        Assert.assertTrue(tsSource.contains("export interface UserOrderVO"));
        Assert.assertTrue(tsSource.contains("export interface UserOrderBO"));
        Assert.assertTrue(tsSource.contains("export interface UserOrderQuery"));
        Assert.assertTrue(tsSource.contains("const BASE_PATH = '/userOrder';"));
        Assert.assertTrue(tsSource.contains("export function selectUserOrderPage("));
        Assert.assertTrue(tsSource.contains("export function saveUserOrder("));

        String mergedEntity = Files.readString(preExistingEntity.toPath(), StandardCharsets.UTF_8);
        Assert.assertTrue(mergedEntity.contains("private String orderNo;"));
        Assert.assertTrue(mergedEntity.contains("customHandWrittenMethod()"));

        Assert.assertTrue(LocalHistoryHelper.hasRollbackableBatch(null));
        int rolledBackCount = LocalHistoryHelper.rollbackLastBatch(null);
        Assert.assertTrue(rolledBackCount >= 10);
        Assert.assertFalse(LocalHistoryHelper.hasRollbackableBatch(null));

        Assert.assertTrue(preExistingEntity.exists());
        Assert.assertEquals(originalHandWrittenEntity, Files.readString(preExistingEntity.toPath(), StandardCharsets.UTF_8));
        Assert.assertFalse("回滚后新增的 BO 文件应被删除", boFile.exists());
        Assert.assertFalse("回滚后新增的 TS API 文件应被删除", tsApiFile.exists());

        Assert.assertEquals("开始生成", CodeGenBundle.message(Locale.SIMPLIFIED_CHINESE, "dialog.ok.button"));
        Assert.assertEquals("Start Generate", CodeGenBundle.message(Locale.US, "dialog.ok.button"));
        Assert.assertTrue(CodeGenBundle.message(Locale.SIMPLIFIED_CHINESE, "rollback.success.message", 11, "user_order")
                .contains("已成功回滚批次 [user_order] 中的 11 个生成文件"));
        Assert.assertTrue(CodeGenBundle.message(Locale.US, "rollback.success.message", 11, "user_order")
                .contains("Successfully rolled back 11 files from batch [user_order]"));
    }

    /**
     * 测试场景 12（v3.0 FeignClient 专项验证）：
     * 1. 通过 skl_feign_client.java.ftl 模板直接生成 OpenFeign 客户端接口（支持自定义 feignServiceName 与多模块路由）
     * 2. 通过 FeignClientConverter 将已有 Controller 类或 Service 接口直接转换为标准 @FeignClient 接口
     */
    @Test
    public void test12_V3FeignClientGenerationAndControllerInterfaceConversion() throws Exception {
        String ddl =
                "CREATE TABLE `user_order` (\n" +
                "  `id` bigint NOT NULL PRIMARY KEY COMMENT '主键ID',\n" +
                "  `order_no` varchar(64) NOT NULL COMMENT '订单编号'\n" +
                ") COMMENT='用户订单表';";

        Map<String, DdlSqlParser.ParsedTable> parsedMap = DdlSqlParser.parseDdlScript(ddl);
        File feignProj = tempFolder.newFolder("feign_client_proj");

        Map<String, Object> params = buildFullEnableParams(parsedMap, Collections.emptyList(), Collections.emptyList());
        params.put("feignClient", "y");
        params.put("feignServiceName", "mall-order-service");
        params.put("docStyle", "OPENAPI3");

        CodeGenerate.foundCode(null, "user_order", "order", "syrobin", params, feignProj.getAbsolutePath() + "/");

        File feignFile = new File(feignProj, "src/main/java/com/example/order/feign/UserOrderFeignClient.java");
        File controllerFile = new File(feignProj, "src/main/java/com/example/order/controller/UserOrderController.java");
        File serviceFile = new File(feignProj, "src/main/java/com/example/order/service/UserOrderService.java");
        Assert.assertTrue("应生成 UserOrderFeignClient.java", feignFile.exists());
        Assert.assertTrue(controllerFile.exists());
        Assert.assertTrue(serviceFile.exists());

        String feignSource = Files.readString(feignFile.toPath(), StandardCharsets.UTF_8);
        Assert.assertTrue(feignSource.contains("package com.example.order.feign;"));
        Assert.assertTrue(feignSource.contains("import org.springframework.cloud.openfeign.FeignClient;"));
        Assert.assertTrue(feignSource.contains("import org.springframework.cloud.openfeign.SpringQueryMap;"));
        Assert.assertTrue(feignSource.contains("name = \"mall-order-service\""));
        Assert.assertTrue(feignSource.contains("contextId = \"userOrderFeignClient\""));
        Assert.assertTrue(feignSource.contains("path = \"/userOrder\""));
        Assert.assertTrue(feignSource.contains("Page<UserOrderVO> select(@SpringQueryMap UserOrderQuery userOrderQuery"));
        Assert.assertTrue(feignSource.contains("@RequestParam(value = \"current\", required = false) Integer current"));
        Assert.assertTrue(feignSource.contains("String save(@Validated @RequestBody UserOrderBO userOrderBO);"));

        String controllerSource = Files.readString(controllerFile.toPath(), StandardCharsets.UTF_8);
        String convertedFromController = FeignClientConverter.convertToFeignClientSource(
                controllerSource, "trade-order-center", null
        );
        Assert.assertTrue(convertedFromController.contains("package com.example.order.feign;"));
        Assert.assertTrue(convertedFromController.contains("name = \"trade-order-center\""));
        Assert.assertTrue(convertedFromController.contains("public interface UserOrderFeignClient"));
        Assert.assertTrue(convertedFromController.contains("Page<UserOrderVO> select(@SpringQueryMap UserOrderQuery userOrderQuery, @RequestParam(value = \"current\", required = false) Integer current, @RequestParam(value = \"size\", required = false) Integer size);"));
        Assert.assertTrue(convertedFromController.contains("String save(@Validated @RequestBody UserOrderBO userOrderBO);"));
        Assert.assertFalse("转换后的 Feign 接口不应残留 Service 属性或方法体", convertedFromController.contains("userOrderService."));

        String serviceSource = Files.readString(serviceFile.toPath(), StandardCharsets.UTF_8);
        String convertedFromService = FeignClientConverter.convertToFeignClientSource(
                serviceSource, null, null
        );
        Assert.assertTrue(convertedFromService.contains("package com.example.order.feign;"));
        Assert.assertTrue(convertedFromService.contains("name = \"order-service\""));
        Assert.assertTrue(convertedFromService.contains("@GetMapping(\"/pageEnhance\")"));
        Assert.assertTrue(convertedFromService.contains("Page<UserOrderVO> pageEnhance(@RequestParam(value = \"current\", required = false) Integer current, @RequestParam(value = \"size\", required = false) Integer size, @SpringQueryMap UserOrderQuery userOrderQuery);"));
        Assert.assertTrue(convertedFromService.contains("@PostMapping(\"/saveEnhance\")"));
        Assert.assertTrue(convertedFromService.contains("String saveEnhance(@RequestBody UserOrderBO userOrderBO);"));
        Assert.assertTrue(convertedFromService.contains("@PutMapping(\"/updateEnhance\")"));
        Assert.assertTrue(convertedFromService.contains("@DeleteMapping(\"/removeEnhance\")"));

        File resolvedTarget = FeignClientConverter.resolveTargetFeignFile(controllerFile);
        Assert.assertEquals("UserOrderFeignClient.java", resolvedTarget.getName());
        Assert.assertEquals("feign", resolvedTarget.getParentFile().getName());
    }

    /**
     * 测试场景 13（v4.0 M1 全特性验证）：
     * 1. 统一响应体包装 (responseWrapperClass = com.syrobin.common.Result) + 规范包名大小写保留
     * 2. 自关联树形表识别 (parent_id) -> TreeVO.children + Service.treeEnhance + Controller/Feign /tree + TS selectTree
     * 3. DDL FOREIGN KEY 解析与主子表自动发现 (RelationTopologyDetector) + 强类型 TS 子表 import
     * 4. 智能查询规则推导引擎 (SmartQueryRuleInferencer: 时间区间 Between / 名称模糊 Like / 编码前缀 LikeRight / 状态等值 Eq)
     * 5. IDEA HTTP Client (.http) 接口测试脚本生成 (skl_http_client.http.ftl)
     * 6. 全语法增量合并 (IncrementalCodeMerger: Java 字段注释/注解/类型演进 + Enum 常量增量合并 + Mapper.xml 合并 + TypeScript 接口合并)
     */
    @Test
    public void test13_V4M1ResponseWrapperTreeVoSmartQueryAndFullAstMerge() throws Exception {
        String v4Ddl =
                "CREATE TABLE `sys_dept` (\n" +
                "  `id` bigint NOT NULL PRIMARY KEY COMMENT '部门主键ID',\n" +
                "  `parent_id` bigint NOT NULL DEFAULT 0 COMMENT '父部门ID',\n" +
                "  `dept_code` varchar(32) NOT NULL COMMENT '部门编码',\n" +
                "  `dept_name` varchar(64) NOT NULL COMMENT '部门名称',\n" +
                "  `dept_status` tinyint NOT NULL DEFAULT 1 COMMENT '部门状态（0：停用，1：启用）',\n" +
                "  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'\n" +
                ") COMMENT='系统部门树形表';\n\n" +
                "CREATE TABLE `sys_dept_member` (\n" +
                "  `id` bigint NOT NULL PRIMARY KEY COMMENT '成员ID',\n" +
                "  `dept_id` bigint NOT NULL COMMENT '所属部门ID',\n" +
                "  `member_name` varchar(64) NOT NULL COMMENT '成员姓名',\n" +
                "  CONSTRAINT `fk_member_dept` FOREIGN KEY (`dept_id`) REFERENCES `sys_dept` (`id`)\n" +
                ") COMMENT='部门成员子表';";

        Map<String, DdlSqlParser.ParsedTable> parsedMap = DdlSqlParser.parseDdlScript(v4Ddl);
        Assert.assertEquals(2, parsedMap.size());

        DdlSqlParser.ParsedTable deptTable = parsedMap.get("sys_dept");
        DdlSqlParser.ParsedTable memberTable = parsedMap.get("sys_dept_member");
        Assert.assertNotNull(deptTable);
        Assert.assertNotNull(memberTable);
        Assert.assertEquals(1, memberTable.getForeignKeys().size());
        Assert.assertTrue(memberTable.getForeignKeys().containsKey("dept_id"));
        Assert.assertEquals("sys_dept.id", memberTable.getForeignKeys().get("dept_id"));

        Table parentCol = RelationTopologyDetector.detectTreeParentColumn(deptTable.getColumns());
        Assert.assertNotNull("应自动识别自关联父节点列 parent_id", parentCol);
        Assert.assertEquals("parent_id", parentCol.getTypeName());

        List<SubTableRelation> discoveredRelations = RelationTopologyDetector.discoverSubTableRelations(
                "sys_dept", parsedMap, null, "sys_"
        );
        Assert.assertEquals(1, discoveredRelations.size());
        Assert.assertEquals("sys_dept_member", discoveredRelations.get(0).getSubTableName());
        Assert.assertEquals("dept_id", discoveredRelations.get(0).getForeignKeyColumn());
        Assert.assertEquals("DeptMember", discoveredRelations.get(0).getSubClassName());

        SmartQueryRuleInferencer.InferredQueryRules inferred = SmartQueryRuleInferencer.inferFromColumns(deptTable.getColumns());
        Assert.assertEquals("create_time 应自动推导 startCreateTime 和 endCreateTime 两个扩展字段",
                2, inferred.getBeanQueries().size());
        Assert.assertTrue(inferred.getServiceQueries().stream()
                .anyMatch(sq -> "8".equals(sq.getType()) && "`create_time`".equals(sq.getColumn())));
        Assert.assertTrue(inferred.getServiceQueries().stream()
                .anyMatch(sq -> "1".equals(sq.getType()) && "`dept_code`".equals(sq.getColumn())));
        Assert.assertTrue(inferred.getServiceQueries().stream()
                .anyMatch(sq -> "1".equals(sq.getType()) && "`dept_name`".equals(sq.getColumn())));
        Assert.assertTrue(inferred.getServiceQueries().stream()
                .anyMatch(sq -> "0".equals(sq.getType()) && "`dept_status`".equals(sq.getColumn())));

        File v4Proj = tempFolder.newFolder("v4_m1_full_proj");
        Map<String, Object> params = buildFullEnableParams(parsedMap, Collections.emptyList(), Collections.emptyList());
        params.put("packageName", "com.syrobin.corePlatform");
        params.put("tablePrefixes", "sys_");
        params.put("responseWrapperClass", "com.syrobin.common.Result");
        params.put("feignClient", "y");
        params.put("frontendApi", "y");
        params.put("httpClient", "y");
        params.put("mapperXml", "y");
        params.put("docStyle", "OPENAPI3");

        CodeGenerate.foundCode(null, "sys_dept", "orgCenter", "syrobin", params, v4Proj.getAbsolutePath() + "/");

        String pkgDir = v4Proj.getAbsolutePath() + "/src/main/java/com/syrobin/corePlatform/orgCenter/";
        File voFile = new File(pkgDir + "entity/vo/DeptVO.java");
        File serviceFile = new File(pkgDir + "service/DeptService.java");
        File serviceImplFile = new File(pkgDir + "service/impl/DeptServiceImpl.java");
        File controllerFile = new File(pkgDir + "controller/DeptController.java");
        File feignFile = new File(pkgDir + "feign/DeptFeignClient.java");
        File tsFile = new File(v4Proj, "src/main/resources/frontend/dept.api.ts");
        File httpFile = new File(v4Proj, "src/test/resources/http/DeptApiTest.http");

        Assert.assertTrue("应保留驼峰包名目录并生成 DeptVO.java", voFile.exists());
        Assert.assertTrue(serviceFile.exists());
        Assert.assertTrue(serviceImplFile.exists());
        Assert.assertTrue(controllerFile.exists());
        Assert.assertTrue(feignFile.exists());
        Assert.assertTrue(tsFile.exists());
        Assert.assertTrue("应生成 DeptApiTest.http 联调测试脚本", httpFile.exists());

        String voContent = Files.readString(voFile.toPath(), StandardCharsets.UTF_8);
        Assert.assertTrue(voContent.contains("package com.syrobin.corePlatform.orgCenter.entity.vo;"));
        Assert.assertTrue("TreeVO 应包含子节点列表 children", voContent.contains("private List<DeptVO> children;"));
        Assert.assertTrue("主表 VO 应自动发现并包含子表 DeptMemberVO 列表", voContent.contains("private List<DeptMemberVO> deptMemberList;"));

        String controllerContent = Files.readString(controllerFile.toPath(), StandardCharsets.UTF_8);
        Assert.assertTrue(controllerContent.contains("import com.syrobin.common.Result;"));
        Assert.assertTrue(controllerContent.contains("public Result<Page<DeptVO>> select("));
        Assert.assertTrue(controllerContent.contains("return Result.ok(deptService.pageEnhance("));
        Assert.assertTrue(controllerContent.contains("@GetMapping(\"/tree\")"));
        Assert.assertTrue(controllerContent.contains("public Result<List<DeptVO>> tree("));

        String feignContent = Files.readString(feignFile.toPath(), StandardCharsets.UTF_8);
        Assert.assertTrue(feignContent.contains("import com.syrobin.common.Result;"));
        Assert.assertTrue(feignContent.contains("Result<Page<DeptVO>> select("));
        Assert.assertTrue(feignContent.contains("Result<List<DeptVO>> tree(@SpringQueryMap DeptQuery deptQuery);"));

        String tsContent = Files.readString(tsFile.toPath(), StandardCharsets.UTF_8);
        Assert.assertTrue("TS 应强类型 import 子表接口", tsContent.contains("import { DeptMemberVO, DeptMemberBO } from './deptMember.api';"));
        Assert.assertTrue(tsContent.contains("deptMemberList?: DeptMemberVO[];"));
        Assert.assertTrue(tsContent.contains("children?: DeptVO[];"));
        Assert.assertTrue(tsContent.contains("export function selectDeptTree("));

        String httpContent = Files.readString(httpFile.toPath(), StandardCharsets.UTF_8);
        Assert.assertTrue(httpContent.contains("GET {{baseUrl}}/dept/select?current=1&size=10"));
        Assert.assertTrue(httpContent.contains("GET {{baseUrl}}/dept/tree"));
        Assert.assertTrue(httpContent.contains("POST {{baseUrl}}/dept/save"));

        String oldJava =
                "package com.syrobin.demo;\n\n" +
                "public class DeptBO {\n" +
                "    /**\n     * 旧部门名称注释\n     */\n" +
                "    private String deptName;\n\n" +
                "    public String customBizHelper() { return deptName; }\n" +
                "}\n";
        String newJava =
                "package com.syrobin.demo;\n\n" +
                "import jakarta.validation.constraints.NotBlank;\n\n" +
                "public class DeptBO {\n" +
                "    /**\n     * 新部门名称注释（必填）\n     */\n" +
                "    @NotBlank(message = \"部门名称不能为空\")\n" +
                "    private String deptName;\n\n" +
                "    private Integer sortOrder;\n" +
                "}\n";
        String mergedJava = IncrementalCodeMerger.mergeJavaSource(oldJava, newJava);
        Assert.assertTrue("应更新已有字段的新 Javadoc 注释", mergedJava.contains("新部门名称注释（必填）"));
        Assert.assertTrue("应同步已有字段新增的校验注解", mergedJava.contains("@NotBlank(message = \"部门名称不能为空\")"));
        Assert.assertTrue("应追加新字段 sortOrder", mergedJava.contains("private Integer sortOrder;"));
        Assert.assertTrue("应保留手写业务方法", mergedJava.contains("customBizHelper()"));

        String oldEnum =
                "package com.syrobin.demo;\n\n" +
                "public enum StatusEnum {\n" +
                "    DISABLE(0, \"停用\"),\n" +
                "    ENABLE(1, \"启用\"),\n" +
                "    ;\n" +
                "    private final Integer value;\n" +
                "    private final String desc;\n" +
                "    StatusEnum(Integer value, String desc) { this.value = value; this.desc = desc; }\n" +
                "}\n";
        String newEnum =
                "package com.syrobin.demo;\n\n" +
                "public enum StatusEnum {\n" +
                "    DISABLE(0, \"停用\"),\n" +
                "    ENABLE(1, \"启用\"),\n" +
                "    FROZEN(2, \"冻结\"),\n" +
                "    ;\n" +
                "    private final Integer value;\n" +
                "    private final String desc;\n" +
                "    StatusEnum(Integer value, String desc) { this.value = value; this.desc = desc; }\n" +
                "}\n";
        String mergedEnum = IncrementalCodeMerger.mergeJavaSource(oldEnum, newEnum);
        Assert.assertTrue("应增量合并新增枚举项 FROZEN", mergedEnum.contains("FROZEN(2, \"冻结\")"));

        String oldXml =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<mapper namespace=\"com.syrobin.DeptMapper\">\n" +
                "    <resultMap id=\"BaseResultMap\" type=\"com.syrobin.Dept\">\n" +
                "        <id column=\"id\" property=\"id\" />\n" +
                "    </resultMap>\n" +
                "    <sql id=\"Base_Column_List\">\n" +
                "        id\n" +
                "    </sql>\n" +
                "    <select id=\"selectCustomStats\" resultType=\"int\">SELECT COUNT(1) FROM sys_dept</select>\n" +
                "</mapper>\n";
        String newXml =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<mapper namespace=\"com.syrobin.DeptMapper\">\n" +
                "    <resultMap id=\"BaseResultMap\" type=\"com.syrobin.Dept\">\n" +
                "        <id column=\"id\" property=\"id\" />\n" +
                "        <result column=\"dept_code\" property=\"deptCode\" />\n" +
                "    </resultMap>\n" +
                "    <sql id=\"Base_Column_List\">\n" +
                "        id, dept_code\n" +
                "    </sql>\n" +
                "</mapper>\n";
        String mergedXml = IncrementalCodeMerger.mergeXmlSource(oldXml, newXml);
        Assert.assertTrue("Mapper.xml 应增量合并新增列映射", mergedXml.contains("property=\"deptCode\""));
        Assert.assertTrue("Mapper.xml 应更新 Base_Column_List", mergedXml.contains("id, dept_code"));
        Assert.assertTrue("Mapper.xml 应保留手写自定义 SQL 标签", mergedXml.contains("selectCustomStats"));

        String oldTs =
                "export interface DeptVO {\n" +
                "  id?: number;\n" +
                "}\n\n" +
                "export function myCustomApiHelper() {\n" +
                "  return '/custom';\n" +
                "}\n";
        String newTs =
                "export interface DeptVO {\n" +
                "  id?: number;\n" +
                "  deptName?: string;\n" +
                "}\n";
        String mergedTs = IncrementalCodeMerger.mergeTypeScriptSource(oldTs, newTs);
        Assert.assertTrue("TypeScript 接口应合并新增属性 deptName", mergedTs.contains("deptName?: string;"));
        Assert.assertTrue("TypeScript 文件应保留自定义辅助函数", mergedTs.contains("myCustomApiHelper()"));
    }

    /**
     * 测试场景 14（v4.0 M2 全栈扩展与测试工程化验证）：
     * 1. EasyExcel 导入导出模型 (skl_excel_dto.java.ftl -> DeptExcelDTO.java) + Controller /exportExcel & /importExcel + TS/HTTP 联动
     * 2. Vue 3 + Element Plus 前端 CRUD 页面 (skl_frontend_vue.vue.ftl -> dept/index.vue) 含树形表格、必填校验规则与主子表动态行编辑
     * 3. JUnit 5 + Mockito 自动化单元测试 (skl_unit_test.java.ftl -> DeptServiceTest.java) 含分页/树形/主子表级联断言
     */
    @Test
    public void test14_V4M2FullStackVueExcelAndUnitTestEngineering() throws Exception {
        String v4M2Ddl =
                "CREATE TABLE `sys_dept` (\n" +
                "  `id` bigint NOT NULL PRIMARY KEY COMMENT '部门主键ID',\n" +
                "  `parent_id` bigint NOT NULL DEFAULT 0 COMMENT '父部门ID',\n" +
                "  `dept_code` varchar(32) NOT NULL COMMENT '部门编码',\n" +
                "  `dept_name` varchar(64) NOT NULL COMMENT '部门名称',\n" +
                "  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'\n" +
                ") COMMENT='系统部门表';\n\n" +
                "CREATE TABLE `sys_dept_member` (\n" +
                "  `id` bigint NOT NULL PRIMARY KEY COMMENT '成员ID',\n" +
                "  `dept_id` bigint NOT NULL COMMENT '所属部门ID',\n" +
                "  `member_name` varchar(64) NOT NULL COMMENT '成员姓名',\n" +
                "  CONSTRAINT `fk_member_dept` FOREIGN KEY (`dept_id`) REFERENCES `sys_dept` (`id`)\n" +
                ") COMMENT='部门成员子表';";

        Map<String, DdlSqlParser.ParsedTable> parsedMap = DdlSqlParser.parseDdlScript(v4M2Ddl);
        File m2Proj = tempFolder.newFolder("v4_m2_fullstack_test_proj");

        Map<String, Object> params = buildFullEnableParams(parsedMap, Collections.emptyList(), Collections.emptyList());
        params.put("packageName", "com.syrobin.enterprise");
        params.put("tablePrefixes", "sys_");
        params.put("responseWrapperClass", "com.syrobin.common.Result");
        params.put("excelDto", "y");
        params.put("frontendApi", "y");
        params.put("frontendVue", "y");
        params.put("httpClient", "y");
        params.put("unitTest", "y");
        params.put("docStyle", "OPENAPI3");

        CodeGenerate.foundCode(null, "sys_dept", "org", "syrobin", params, m2Proj.getAbsolutePath() + "/");

        File excelDtoFile = new File(m2Proj, "src/main/java/com/syrobin/enterprise/org/entity/excel/DeptExcelDTO.java");
        File controllerFile = new File(m2Proj, "src/main/java/com/syrobin/enterprise/org/controller/DeptController.java");
        File tsApiFile = new File(m2Proj, "src/main/resources/frontend/dept.api.ts");
        File vuePageFile = new File(m2Proj, "src/main/resources/frontend/dept/index.vue");
        File httpFile = new File(m2Proj, "src/test/resources/http/DeptApiTest.http");
        File unitTestFile = new File(m2Proj, "src/test/java/com/syrobin/enterprise/org/service/DeptServiceTest.java");

        Assert.assertTrue("应生成 DeptExcelDTO.java", excelDtoFile.exists());
        Assert.assertTrue("应生成 DeptController.java", controllerFile.exists());
        Assert.assertTrue("应生成 dept.api.ts", tsApiFile.exists());
        Assert.assertTrue("应生成 Vue3 CRUD 页面 dept/index.vue", vuePageFile.exists());
        Assert.assertTrue("应生成 DeptApiTest.http", httpFile.exists());
        Assert.assertTrue("应生成 JUnit 5 单元测试 DeptServiceTest.java", unitTestFile.exists());

        String excelDtoContent = Files.readString(excelDtoFile.toPath(), StandardCharsets.UTF_8);
        Assert.assertTrue(excelDtoContent.contains("package com.syrobin.enterprise.org.entity.excel;"));
        Assert.assertTrue(excelDtoContent.contains("@ExcelIgnoreUnannotated"));
        Assert.assertTrue(excelDtoContent.contains("@ExcelProperty(value = \"部门编码\", index = 2)"));

        String controllerContent = Files.readString(controllerFile.toPath(), StandardCharsets.UTF_8);
        Assert.assertTrue(controllerContent.contains("@GetMapping(\"/exportExcel\")"));
        Assert.assertTrue(controllerContent.contains("public Result<List<DeptExcelDTO>> exportExcel("));
        Assert.assertTrue(controllerContent.contains("@PostMapping(\"/importExcel\")"));
        Assert.assertTrue(controllerContent.contains("public Result<Integer> importExcel(@RequestBody List<DeptExcelDTO> excelList)"));

        String tsContent = Files.readString(tsApiFile.toPath(), StandardCharsets.UTF_8);
        Assert.assertTrue(tsContent.contains("export function exportDeptExcel("));
        Assert.assertTrue(tsContent.contains("export function importDeptExcel("));

        String httpContent = Files.readString(httpFile.toPath(), StandardCharsets.UTF_8);
        Assert.assertTrue(httpContent.contains("GET {{baseUrl}}/dept/exportExcel"));

        String vueContent = Files.readString(vuePageFile.toPath(), StandardCharsets.UTF_8);
        Assert.assertTrue(vueContent.contains(":tree-props=\"{ children: 'children' }\""));
        Assert.assertTrue(vueContent.contains("selectDeptTree,"));
        Assert.assertTrue(vueContent.contains("exportDeptExcel,"));
        Assert.assertTrue(vueContent.contains("deptCode: ["));
        Assert.assertTrue(vueContent.contains("handleAddDeptMemberRow"));
        Assert.assertTrue(vueContent.contains("handleRemoveDeptMemberRow"));

        String unitTestContent = Files.readString(unitTestFile.toPath(), StandardCharsets.UTF_8);
        Assert.assertTrue(unitTestContent.contains("package com.syrobin.enterprise.org.service;"));
        Assert.assertTrue(unitTestContent.contains("@ExtendWith(MockitoExtension.class)"));
        Assert.assertTrue(unitTestContent.contains("class DeptServiceTest {"));
        Assert.assertTrue(unitTestContent.contains("void testPageEnhance()"));
        Assert.assertTrue(unitTestContent.contains("void testSaveEnhance()"));
        Assert.assertTrue(unitTestContent.contains("verify(deptMemberService, atLeastOnce()).saveEnhance(any(DeptMemberBO.class));"));
        Assert.assertTrue(unitTestContent.contains("void testTreeEnhance()"));
    }

    /**
     * 测试场景 15（v4.0 M3 智能化与开放生态全链路验证）：
     * 1. 自然语言转标准 DDL 智能建模引擎 (NaturalLanguageDdlInferenceEngine: 中文描述推导表名、数据类型、枚举注释与主子表外键)
     * 2. 多方言数据库表结构演进增量 Diff 迁移引擎 (DdlMigrationGenerator: MySQL / PostgreSQL / Oracle / DM8 ALTER TABLE ADD/MODIFY/DROP)
     * 3. Flyway 标准化迁移脚本生成 (skl_flyway_migration.sql.ftl -> V1__init_product_stock.sql)
     * 4. 开放生态自定义模板包 (CustomTemplatePackManager: 导出/导入 .syrobin-template-pack.json 并渲染 GraphQL Schema)
     * 5. 开放生态扩展点 SPI (CodeGenPostProcessor: 动态挂载后置处理器追加自定义产物)
     */
    @Test
    public void test15_V4M3AiDdlMigrationDiffAndOpenEcosystem() throws Exception {
        // 1. 验证自然语言业务描述转标准主子表 DDL
        String nlPrompt =
                "创建商品库存表 product_stock：包含仓库编码、商品名称、库存数量、预警阈值、单价金额、库存状态（0：正常，1：缺货，2：冻结）、负责人手机号、负责人邮箱\n\n" +
                "创建商品库存明细表 product_stock_item：包含库存ID product_stock_id、批次编号、变动数量、备注说明";

        String inferredDdl = NaturalLanguageDdlInferenceEngine.inferDdlFromNaturalLanguage(nlPrompt);
        Assert.assertTrue(inferredDdl.contains("CREATE TABLE `product_stock`"));
        Assert.assertTrue(inferredDdl.contains("`warehouse_code` varchar(64) NOT NULL"));
        Assert.assertTrue(inferredDdl.contains("`stock_quantity` int NOT NULL"));
        Assert.assertTrue(inferredDdl.contains("`unit_price_amount` decimal(12,2) NOT NULL"));
        Assert.assertTrue(inferredDdl.contains("`stock_status` tinyint NOT NULL DEFAULT 0 COMMENT '库存状态（0：正常，1：缺货，2：冻结）'"));
        Assert.assertTrue(inferredDdl.contains("`manager_mobile` varchar(20) NOT NULL"));
        Assert.assertTrue(inferredDdl.contains("`manager_email` varchar(128) DEFAULT NULL"));
        Assert.assertTrue(inferredDdl.contains("CREATE TABLE `product_stock_item`"));
        Assert.assertTrue("子表应自动绑定指向主表 product_stock 的外键约束",
                inferredDdl.contains("FOREIGN KEY (`product_stock_id`) REFERENCES `product_stock` (`id`)"));

        Map<String, DdlSqlParser.ParsedTable> parsedMap = DdlSqlParser.parseDdlScript(inferredDdl);
        Assert.assertEquals(2, parsedMap.size());

        // 2. 验证多方言表结构演进 Diff SQL 生成器 (DdlMigrationGenerator)
        String oldDdl =
                "CREATE TABLE `product_stock` (\n" +
                "  `id` bigint NOT NULL PRIMARY KEY COMMENT '主键ID',\n" +
                "  `warehouse_code` varchar(32) NOT NULL COMMENT '旧仓库编码',\n" +
                "  `obsolete_col` varchar(64) DEFAULT NULL COMMENT '废弃字段'\n" +
                ") COMMENT='商品库存表';";
        String mysqlDiff = DdlMigrationGenerator.generateScriptDiffMigration(oldDdl, inferredDdl, DatabaseDialect.MYSQL);
        Assert.assertTrue("MySQL 应包含 MODIFY COLUMN warehouse_code", mysqlDiff.contains("MODIFY COLUMN `warehouse_code` VARCHAR(64)"));
        Assert.assertTrue("MySQL 应包含 ADD COLUMN stock_status", mysqlDiff.contains("ADD COLUMN `stock_status`"));
        Assert.assertTrue("MySQL 应包含 DROP COLUMN obsolete_col", mysqlDiff.contains("DROP COLUMN `obsolete_col`"));

        String pgDiff = DdlMigrationGenerator.generateScriptDiffMigration(oldDdl, inferredDdl, DatabaseDialect.POSTGRESQL);
        Assert.assertTrue("PostgreSQL 应包含 ALTER COLUMN warehouse_code TYPE", pgDiff.contains("ALTER COLUMN \"warehouse_code\" TYPE VARCHAR(64)"));
        Assert.assertTrue("PostgreSQL 应包含 COMMENT ON COLUMN", pgDiff.contains("COMMENT ON COLUMN \"product_stock\".\"stock_status\""));

        // 3. 验证自定义模板包导出/导入 + Flyway 迁移脚本 + CodeGenPostProcessor 扩展点全链路生成
        File m3EcoProj = tempFolder.newFolder("v4_m3_ecosystem_proj");
        CustomTemplatePackManager.CustomTemplateDefinition graphqlDef =
                new CustomTemplatePackManager.CustomTemplateDefinition(
                        "graphql_schema.graphqls.ftl",
                        "GraphQL Schema 定义模板",
                        "src/main/resources/graphql/${className}.graphqls",
                        "controller",
                        "type ${ClassName} {\n<#list list as col>  ${col.columnName}: String\n</#list>}\n",
                        true
                );
        CustomTemplatePackManager.TemplatePack pack = new CustomTemplatePackManager.TemplatePack();
        pack.setPackName("Enterprise GraphQL Pack");
        pack.setTemplates(Collections.singletonList(graphqlDef));

        File packFile = new File(m3EcoProj, CustomTemplatePackManager.DEFAULT_PACK_FILENAME);
        CustomTemplatePackManager.exportTemplatePack(packFile, pack);
        Assert.assertTrue(packFile.exists());
        CustomTemplatePackManager.TemplatePack importedPack = CustomTemplatePackManager.importTemplatePack(packFile);
        Assert.assertEquals(1, importedPack.getTemplates().size());

        CodeGenPostProcessor.clearProgrammaticProcessors();
        CodeGenPostProcessor customAuditProcessor = (project, tableName, templateModel, renderedFiles) -> {
            String className = String.valueOf(templateModel.get("ClassName"));
            renderedFiles.put(
                    m3EcoProj.getAbsolutePath() + "/src/main/resources/meta/" + className + ".manifest.txt",
                    "table=" + tableName + ",class=" + className
            );
        };
        CodeGenPostProcessor.registerProcessor(customAuditProcessor);

        try {
            Map<String, Object> params = buildFullEnableParams(parsedMap, Collections.emptyList(), Collections.emptyList());
            params.put("flywayMigration", "y");
            params.put("customTemplateDefinitions", importedPack.getTemplates());

            CodeGenerate.foundCode(null, "product_stock", "inventory", "syrobin", params, m3EcoProj.getAbsolutePath() + "/");

            File flywayFile = new File(m3EcoProj, "src/main/resources/db/migration/V1__init_product_stock.sql");
            File graphqlFile = new File(m3EcoProj, "src/main/resources/graphql/productStock.graphqls");
            File manifestFile = new File(m3EcoProj, "src/main/resources/meta/ProductStock.manifest.txt");

            Assert.assertTrue("应生成 Flyway 迁移脚本 V1__init_product_stock.sql", flywayFile.exists());
            Assert.assertTrue("应通过自定义模板包生成 productStock.graphqls", graphqlFile.exists());
            Assert.assertTrue("应通过 CodeGenPostProcessor 扩展点生成 ProductStock.manifest.txt", manifestFile.exists());

            String flywaySql = Files.readString(flywayFile.toPath(), StandardCharsets.UTF_8);
            Assert.assertTrue(flywaySql.contains("CREATE TABLE IF NOT EXISTS `product_stock`"));
            Assert.assertTrue(flywaySql.contains("PRIMARY KEY (`id`)"));

            String graphqlContent = Files.readString(graphqlFile.toPath(), StandardCharsets.UTF_8);
            Assert.assertTrue(graphqlContent.contains("type ProductStock {"));
            Assert.assertTrue(graphqlContent.contains("warehouseCode: String"));

            String manifestContent = Files.readString(manifestFile.toPath(), StandardCharsets.UTF_8);
            Assert.assertEquals("table=product_stock,class=ProductStock", manifestContent);
        } finally {
            CodeGenPostProcessor.clearProgrammaticProcessors();
        }
    }

    public static class MockDasTable {
        private final String name;

        public MockDasTable(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public String getKind() {
            return "TABLE";
        }
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
