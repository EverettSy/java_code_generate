package com.code.utils;

import com.code.config.CodeGenSettingsState;
import com.code.spi.CodeGenPostProcessor;
import com.code.utils.entity.*;
import com.code.utils.freemarker.FreeMarkers;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.FileTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.StringTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.StringReader;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 核心代码生成器（v4.0 GA：支持 20 套全栈与数据库迁移模板、自定义模板包 CustomTemplatePack、开放扩展点 CodeGenPostProcessor、统一响应包装 Result<T>、TreeVO、主子表自动发现、智能查询推导、EasyExcel、Vue3 CRUD 与 JUnit5 单元测试）
 *
 * @author syrobin
 */
public class CodeGenerate {

    private static <T> T supplyOnEdt(Supplier<T> supplier) {
        if (ApplicationManager.getApplication() == null || ApplicationManager.getApplication().isDispatchThread()) {
            return supplier.get();
        }
        AtomicReference<T> resultRef = new AtomicReference<>();
        ApplicationManager.getApplication().invokeAndWait(() -> resultRef.set(supplier.get()));
        return resultRef.get();
    }

    private static boolean isEnabled(Map<String, Object> parameterMap, String key) {
        return Strings.CI.equals(String.valueOf(parameterMap.get(key)), "y");
    }

    /**
     * 兼容旧版调用签名
     */
    public static void foundCode(String table, String moduleName, String author, Map<String, Object> parameterMap, String path) throws Exception {
        foundCode(null, table, moduleName, author, parameterMap, path);
    }

    /**
     * 核心代码生成方法（支持在线数据库元数据读取、离线 DDL SQL 解析、多模块分层路由、LocalHistory 快照记录与增量合并）
     */
    public static void foundCode(@Nullable Project project,
                                 String table,
                                 String moduleName,
                                 String author,
                                 Map<String, Object> parameterMap,
                                 String path) throws Exception {
        if (StringUtils.isBlank(table)) {
            return;
        }
        IncrementalCodeMerger.WriteStrategy writeStrategy = resolveWriteStrategy(project, parameterMap);
        Map<String, String> renderedFiles = renderAllTargetFilesInMemory(project, table, moduleName, author, parameterMap, path, null);
        for (Map.Entry<String, String> entry : renderedFiles.entrySet()) {
            LocalHistoryHelper.recordFileBeforeWrite(project, entry.getKey());
            IncrementalCodeMerger.writeWithStrategy(project, entry.getValue(), entry.getKey(), writeStrategy);
        }
    }

    /**
     * 内存预渲染指定表的全量目标文件（不落盘），返回「目标文件绝对路径 -> 渲染后的 Java/XML/TS/Vue/HTTP/SQL 源码」映射。
     */
    @SuppressWarnings("unchecked")
    @NotNull
    public static Map<String, String> renderAllTargetFilesInMemory(@Nullable Project project,
                                                                   String table,
                                                                   String moduleName,
                                                                   String author,
                                                                   Map<String, Object> parameterMap,
                                                                   String path,
                                                                   @Nullable List<Table> preResolvedColumns) throws Exception {
        Map<String, String> outputFiles = new LinkedHashMap<>();
        if (StringUtils.isBlank(table)) {
            return outputFiles;
        }

        String basePackage = normalizePackageName(resolveBasePackageName(project, path, parameterMap));
        String cleanModuleSegment = StringUtils.isNotBlank(moduleName) ? moduleName.trim() : "";
        String packageName = StringUtils.isNotBlank(cleanModuleSegment)
                ? basePackage + "." + cleanModuleSegment
                : basePackage;

        String tableComment = null;
        List<Table> rawColumns = null;
        Map<String, DdlSqlParser.ParsedTable> ddlMap = null;

        // 1. 优先检查是否存在离线 DDL 解析出的表元数据
        Object ddlMapObj = parameterMap.get("parsedDdlTables");
        if (parameterMap.containsKey("parsedDdlTables")) {
            if (!(ddlMapObj instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("离线 DDL 元数据无效，请重新解析");
            }
            ddlMap = (Map<String, DdlSqlParser.ParsedTable>) ddlMapObj;
            DdlSqlParser.ParsedTable parsed = findParsedTableIgnoreCase(ddlMap, table);
            if (parsed == null) {
                throw new IllegalArgumentException("离线 DDL 未包含表：" + table);
            }
            table = parsed.getTableName();
            tableComment = Objects.toString(parsed.getTableComment(), "");
            rawColumns = cloneColumns(parsed.getColumns());
        } else if (preResolvedColumns != null && !preResolvedColumns.isEmpty()) {
            rawColumns = cloneColumns(preResolvedColumns);
            tableComment = Objects.toString(parameterMap.getOrDefault("tableComment_" + table, ""), "");
        }

        // 2. 若未使用离线 DDL 且无预缓存字段，则通过数据库连接拉取表注释与字段元数据
        if (rawColumns == null) {
            String database = resolveDatabaseName(project, path);
            try (Connection connection = CodeGenerateUtils.getDblink(project, path)) {
                DatabaseDialect dialect = CodeGenerateUtils.detectDialect(connection);
                tableComment = dialect.querySingleTableComment(connection, database, table);
                if (tableComment == null) {
                    throw new IllegalArgumentException("未在数据库 [" + database + "] 中找到表：" + table);
                }
                rawColumns = dialect.queryColumns(connection, database, table);
            }
        }

        boolean enableValidation = resolveEnableValidation(project, parameterMap);
        ValidationAnnotationResolver.enrichValidationAnnotations(rawColumns, enableValidation);

        String bootVersion = resolveBootVersion(project, parameterMap);
        String validationPkg = "3".equals(bootVersion) ? "jakarta.validation.constraints" : "javax.validation.constraints";
        String mapperStrategy = resolveMapperStrategy(project, parameterMap);

        String tablePrefixes = resolveTablePrefixes(project, parameterMap);
        String className = CodeGenerateUtils.toUpper(table, tablePrefixes);

        String baseEntityClass = resolveBaseEntityClass(project, parameterMap);
        String baseEntitySimpleName = extractSimpleClassName(baseEntityClass);
        Set<String> ignoredColumns = resolveIgnoredBaseColumns(project, parameterMap, StringUtils.isNotBlank(baseEntitySimpleName));
        List<Table> entityColumns = filterEntityColumns(rawColumns, ignoredColumns);

        List<SubTableRelation> subTableRelations = resolveSubTableRelationsForTable(project, parameterMap, table, tablePrefixes, ddlMap);

        String responseWrapperClass = resolveResponseWrapperClass(project, parameterMap);
        String responseWrapperSimpleName = extractSimpleClassName(responseWrapperClass);
        String responseWrapperSuccessMethod = resolveResponseWrapperSuccessMethod(project, parameterMap);

        // v4.0 M1：树形自关联表检测（包含 parent_id / pid / parent_code 时自动开启 TreeVO 与 treeEnhance）
        Table treeParentCol = RelationTopologyDetector.detectTreeParentColumn(rawColumns);
        Table treeIdCol = RelationTopologyDetector.detectPrimaryKeyColumn(rawColumns);
        boolean treeTable = isTreeTableEnabled(parameterMap, treeParentCol);

        // v4.0 M1：若开启 autoInferQueryRules 且当前表无手配查询规则，则自动根据列元数据推导 BeanQuery 与 ServiceQuery
        Object resolvedServiceQueryList = queryRulesForTable(parameterMap, "serviceQueryList", table);
        Object resolvedBeanQueryList = queryRulesForTable(parameterMap, "beanQuery", table);
        if (!Boolean.FALSE.equals(parameterMap.get("autoInferQueryRules"))) {
            boolean explicitInfer = Boolean.TRUE.equals(parameterMap.get("autoInferQueryRules"));
            boolean emptySq = !(resolvedServiceQueryList instanceof Collection<?> c1) || c1.isEmpty();
            boolean emptyBq = !(resolvedBeanQueryList instanceof Collection<?> c2) || c2.isEmpty();
            if (explicitInfer && emptySq && emptyBq) {
                SmartQueryRuleInferencer.InferredQueryRules inferred = SmartQueryRuleInferencer.inferFromColumns(rawColumns);
                resolvedBeanQueryList = inferred.getBeanQueries();
                resolvedServiceQueryList = inferred.getServiceQueries();
            }
        }

        Configuration cfg = buildFreemarkerConfiguration(project, path);

        String resolvedModuleName = StringUtils.defaultIfBlank(moduleName, StringUtils.uncapitalize(className)).trim();
        String feignServiceName = resolveFeignServiceName(project, parameterMap, resolvedModuleName);

        Map<String, Object> model = new HashMap<>();
        model.put("packageName", packageName);
        model.put("moduleName", resolvedModuleName);
        model.put("feignServiceName", feignServiceName);
        model.put("responseWrapperClass", responseWrapperClass);
        model.put("responseWrapperSimpleName", responseWrapperSimpleName);
        model.put("responseWrapperSuccessMethod", responseWrapperSuccessMethod);
        model.put("treeTable", treeTable);
        model.put("treeParentColumnName", treeParentCol != null ? treeParentCol.getColumnName() : "parentId");
        model.put("treeParentColumnUpper", treeParentCol != null ? treeParentCol.getColumnNameUpper() : "ParentId");
        model.put("treeIdColumnName", treeIdCol != null ? treeIdCol.getColumnName() : "id");
        model.put("treeIdColumnUpper", treeIdCol != null ? treeIdCol.getColumnNameUpper() : "Id");
        model.put("className", StringUtils.uncapitalize(className));
        model.put("ClassName", className);
        model.put("author", author);
        model.put("date", CodeGenerateUtils.getDate());
        model.put("functionName", Objects.toString(tableComment, ""));
        model.put("tableName", table);
        model.put("bootVersion", bootVersion);
        model.put("docStyle", resolveDocStyle(project, parameterMap));
        model.put("mapperStrategy", mapperStrategy);
        model.put("enableValidation", enableValidation);
        model.put("validationPkg", validationPkg);
        model.put("baseEntityClass", baseEntityClass);
        model.put("baseEntitySimpleName", baseEntitySimpleName);
        model.put("list", rawColumns);
        model.put("entityColumns", entityColumns);
        model.put("subTableRelations", subTableRelations);
        model.put("braceL", "{");
        model.put("braceR", "}");
        model.put("serviceQueryList", resolvedServiceQueryList);
        model.put("beanQuery", resolvedBeanQueryList);
        model.put("excelEnabled", isEnabled(parameterMap, "excelDto"));

        String packageRelDir = packageName.replace(".", "/");

        // 1. 生成 Mapper 接口与可选 Mapper XML
        if (isEnabled(parameterMap, "mapper")) {
            String layerRoot = resolveLayerOutputRoot(project, path, "mapper", parameterMap);
            outputFiles.put(
                    layerRoot + "src/main/java/" + packageRelDir + "/mapper/" + className + "Mapper.java",
                    renderToString(cfg, "skl_mapper.java.ftl", model)
            );
        }
        if (isEnabled(parameterMap, "mapperXml")) {
            String layerRoot = resolveLayerOutputRoot(project, path, "mapper", parameterMap);
            outputFiles.put(
                    layerRoot + "src/main/resources/mapper/" + className + "Mapper.xml",
                    renderToString(cfg, "skl_mapper_xml.xml.ftl", model)
            );
        }

        // 2. 解析字段枚举注释并按需生成状态枚举类
        List<EnumsEntityName> enumsList = new ArrayList<>();
        String enumLayerRoot = resolveLayerOutputRoot(project, path, "enums", parameterMap);
        for (Table t : rawColumns) {
            if (parameterMap.get("fixedEnums") != null
                    && StringUtils.isNotBlank(String.valueOf(parameterMap.get("fixedEnums")))
                    && !Objects.equals(t.getTypeName(), parameterMap.get("fixedEnums"))) {
                continue;
            }
            List<EnumsEntityContent> enumsEntitiesList = EnumCommentParser.parseEnumItems(t.getColumnComment());
            if (!enumsEntitiesList.isEmpty()) {
                EnumsEntityName enumName = new EnumsEntityName();
                enumName.setSmallName(t.getColumnName());
                enumName.setBigName(t.getColumnNameUpper());
                enumsList.add(enumName);

                model.put("enumsEntityName", t.getColumnNameUpper());
                model.put("enumsEntitiesList", enumsEntitiesList);

                if (isEnabled(parameterMap, "enums")) {
                    outputFiles.put(
                            enumLayerRoot + "src/main/java/" + packageRelDir + "/entity/enums/" + className + t.getColumnNameUpper() + "Enum.java",
                            renderToString(cfg, "skl_entity_enums.java.ftl", model)
                    );
                }
            }
        }
        model.put("enumsList", enumsList);

        // 3. 生成实体类 Entity
        if (isEnabled(parameterMap, "entity")) {
            String layerRoot = resolveLayerOutputRoot(project, path, "entity", parameterMap);
            outputFiles.put(
                    layerRoot + "src/main/java/" + packageRelDir + "/entity/" + className + ".java",
                    renderToString(cfg, "skl_entity.java.ftl", model)
            );
        }

        // 4. 生成 VO 类
        if (isEnabled(parameterMap, "entityVO")) {
            String layerRoot = resolveLayerOutputRoot(project, path, "entityVO", parameterMap);
            outputFiles.put(
                    layerRoot + "src/main/java/" + packageRelDir + "/entity/vo/" + className + "VO.java",
                    renderToString(cfg, "skl_entity_vo.java.ftl", model)
            );
        }

        // 5. 生成 BO 类
        if (isEnabled(parameterMap, "entityBO")) {
            String layerRoot = resolveLayerOutputRoot(project, path, "entityBO", parameterMap);
            outputFiles.put(
                    layerRoot + "src/main/java/" + packageRelDir + "/entity/bo/" + className + "BO.java",
                    renderToString(cfg, "skl_entity_bo.java.ftl", model)
            );
        }

        // 6. 生成 Query 类
        if (isEnabled(parameterMap, "entityQuery")) {
            String layerRoot = resolveLayerOutputRoot(project, path, "entityQuery", parameterMap);
            outputFiles.put(
                    layerRoot + "src/main/java/" + packageRelDir + "/entity/query/" + className + "Query.java",
                    renderToString(cfg, "skl_entity_query.java.ftl", model)
            );
        }

        // 6.5 生成 MapStruct Converter 类（当显式开启 converter 或 mapperStrategy 为 MAPSTRUCT 时）
        if (isEnabled(parameterMap, "converter")
                || ("MAPSTRUCT".equalsIgnoreCase(mapperStrategy) && (isEnabled(parameterMap, "service") || isEnabled(parameterMap, "serviceResults")))) {
            String layerRoot = resolveLayerOutputRoot(project, path, "entityBO", parameterMap);
            outputFiles.put(
                    layerRoot + "src/main/java/" + packageRelDir + "/entity/converter/" + className + "Converter.java",
                    renderToString(cfg, "skl_converter.java.ftl", model)
            );
        }

        // 7. 生成 Service 接口与实现类
        if (isEnabled(parameterMap, "service")) {
            String layerRoot = resolveLayerOutputRoot(project, path, "service", parameterMap);
            outputFiles.put(
                    layerRoot + "src/main/java/" + packageRelDir + "/service/" + className + "Service.java",
                    renderToString(cfg, "skl_service.java.ftl", model)
            );
            outputFiles.put(
                    layerRoot + "src/main/java/" + packageRelDir + "/service/impl/" + className + "ServiceImpl.java",
                    renderToString(cfg, "skl_serviceImpl.java.ftl", model)
            );
        }

        // 8. 生成 ServiceQuery 类
        if (isEnabled(parameterMap, "serviceQuery")) {
            String layerRoot = resolveLayerOutputRoot(project, path, "serviceQuery", parameterMap);
            outputFiles.put(
                    layerRoot + "src/main/java/" + packageRelDir + "/service/query/" + className + "ServiceQuery.java",
                    renderToString(cfg, "skl_service_query.java.ftl", model)
            );
        }

        // 9. 生成 ServiceResults 类
        if (isEnabled(parameterMap, "serviceResults")) {
            String layerRoot = resolveLayerOutputRoot(project, path, "serviceResults", parameterMap);
            outputFiles.put(
                    layerRoot + "src/main/java/" + packageRelDir + "/service/results/" + className + "ServiceResults.java",
                    renderToString(cfg, "skl_service_results.java.ftl", model)
            );
        }

        // 10. 生成 Controller 类
        if (isEnabled(parameterMap, "controller")) {
            String layerRoot = resolveLayerOutputRoot(project, path, "controller", parameterMap);
            outputFiles.put(
                    layerRoot + "src/main/java/" + packageRelDir + "/controller/" + className + "Controller.java",
                    renderToString(cfg, "skl_controller.java.ftl", model)
            );
        }

        // 10.5 生成微服务 OpenFeign Client 接口（支持将 Controller 接口转为 Feign Client）
        if (isEnabled(parameterMap, "feignClient")) {
            String layerRoot = resolveLayerOutputRoot(project, path, "feignClient", parameterMap);
            outputFiles.put(
                    layerRoot + "src/main/java/" + packageRelDir + "/feign/" + className + "FeignClient.java",
                    renderToString(cfg, "skl_feign_client.java.ftl", model)
            );
        }

        // 11. 生成前端 TypeScript Interface & Axios API 模块（v3.0 M3 + v4.0 M1 强类型主子表 + v4.0 M2 Excel API）
        if (isEnabled(parameterMap, "frontendApi")) {
            String layerRoot = resolveLayerOutputRoot(project, path, "controller", parameterMap);
            outputFiles.put(
                    layerRoot + "src/main/resources/frontend/" + StringUtils.uncapitalize(className) + ".api.ts",
                    renderToString(cfg, "skl_frontend_api.ts.ftl", model)
            );
        }

        // 12. 生成 IDEA HTTP Client 接口联调测试文件（v4.0 M1）
        if (isEnabled(parameterMap, "httpClient")) {
            String layerRoot = resolveLayerOutputRoot(project, path, "controller", parameterMap);
            outputFiles.put(
                    layerRoot + "src/test/resources/http/" + className + "ApiTest.http",
                    renderToString(cfg, "skl_http_client.http.ftl", model)
            );
        }

        // 13. 生成 EasyExcel 导入导出 DTO 模型（v4.0 M2）
        if (isEnabled(parameterMap, "excelDto")) {
            String layerRoot = resolveLayerOutputRoot(project, path, "entityBO", parameterMap);
            outputFiles.put(
                    layerRoot + "src/main/java/" + packageRelDir + "/entity/excel/" + className + "ExcelDTO.java",
                    renderToString(cfg, "skl_excel_dto.java.ftl", model)
            );
        }

        // 14. 生成 Vue3 + Element Plus 前端 CRUD 页面（v4.0 M2）
        if (isEnabled(parameterMap, "frontendVue")) {
            String layerRoot = resolveLayerOutputRoot(project, path, "controller", parameterMap);
            outputFiles.put(
                    layerRoot + "src/main/resources/frontend/" + StringUtils.uncapitalize(className) + "/index.vue",
                    renderToString(cfg, "skl_frontend_vue.vue.ftl", model)
            );
        }

        // 15. 生成 JUnit 5 + Mockito Service 单元测试类（v4.0 M2）
        if (isEnabled(parameterMap, "unitTest")) {
            String layerRoot = resolveLayerOutputRoot(project, path, "service", parameterMap);
            outputFiles.put(
                    layerRoot + "src/test/java/" + packageRelDir + "/service/" + className + "ServiceTest.java",
                    renderToString(cfg, "skl_unit_test.java.ftl", model)
            );
        }

        // 16. 生成 Flyway / Liquibase 数据库版本迁移脚本（v4.0 M3）
        if (isEnabled(parameterMap, "flywayMigration")) {
            String layerRoot = resolveLayerOutputRoot(project, path, "mapper", parameterMap);
            outputFiles.put(
                    layerRoot + "src/main/resources/db/migration/V1__init_" + table.toLowerCase(Locale.ROOT) + ".sql",
                    renderToString(cfg, "skl_flyway_migration.sql.ftl", model)
            );
        }

        // 17. 渲染开放生态自定义模板包 (CustomTemplateDefinition)（v4.0 M3）
        Object customDefsObj = parameterMap.get("customTemplateDefinitions");
        if (customDefsObj instanceof Collection<?> customDefs) {
            for (Object item : customDefs) {
                if (item instanceof CustomTemplatePackManager.CustomTemplateDefinition def
                        && def.isEnabled()
                        && StringUtils.isNotBlank(def.getTemplateContent())) {
                    String layerRoot = resolveLayerOutputRoot(project, path,
                            StringUtils.defaultIfBlank(def.getLayerKey(), "controller"), parameterMap);
                    String targetAbsPath = CustomTemplatePackManager.resolveTargetFilePath(
                            def, layerRoot, packageRelDir, model
                    );
                    Template customTpl = new Template(
                            StringUtils.defaultIfBlank(def.getName(), "custom.ftl"),
                            new StringReader(def.getTemplateContent()),
                            cfg
                    );
                    outputFiles.put(targetAbsPath, FreeMarkers.renderTemplate(customTpl, model));
                }
            }
        }

        // 18. 触发开放生态 CodeGenPostProcessor 扩展点与注册钩子（v4.0 M3）
        CodeGenPostProcessor.invokeAll(project, table, model, outputFiles);

        return outputFiles;
    }

    private static boolean isTreeTableEnabled(Map<String, Object> parameterMap, @Nullable Table treeParentCol) {
        Object explicit = parameterMap.get("treeTable");
        if (explicit instanceof Boolean b) {
            return b;
        }
        if (explicit != null && StringUtils.isNotBlank(String.valueOf(explicit))) {
            return "true".equalsIgnoreCase(String.valueOf(explicit)) || "y".equalsIgnoreCase(String.valueOf(explicit));
        }
        return treeParentCol != null;
    }

    @NotNull
    private static String normalizePackageName(@Nullable String rawPackage) {
        if (StringUtils.isBlank(rawPackage)) {
            return "com.example";
        }
        return rawPackage.trim().replace('/', '.').replaceAll("\\.+", ".").replaceAll("^\\.|\\.$", "");
    }

    @SuppressWarnings("unchecked")
    private static List<SubTableRelation> resolveSubTableRelationsForTable(@Nullable Project project,
                                                                           Map<String, Object> parameterMap,
                                                                           String table,
                                                                           String tablePrefixes,
                                                                           @Nullable Map<String, DdlSqlParser.ParsedTable> ddlMap) {
        List<SubTableRelation> result = new ArrayList<>();
        Object byTableObj = parameterMap.get("subTableRelationsByTable");
        if (byTableObj instanceof Map<?, ?> byTableMap) {
            Object listObj = byTableMap.get(table);
            if (listObj instanceof List<?> rawList) {
                for (Object item : rawList) {
                    if (item instanceof SubTableRelation rel && StringUtils.isNotBlank(rel.getSubTableName())) {
                        rel.recomputeDerivedNames(tablePrefixes);
                        result.add(rel);
                    }
                }
                if (!result.isEmpty()) {
                    return result;
                }
            }
        }
        Object directObj = parameterMap.get("subTableRelations");
        if (directObj instanceof List<?> rawList) {
            for (Object item : rawList) {
                if (item instanceof SubTableRelation rel && StringUtils.isNotBlank(rel.getSubTableName())) {
                    rel.recomputeDerivedNames(tablePrefixes);
                    result.add(rel);
                }
            }
            if (!result.isEmpty()) {
                return result;
            }
        }
        if (project != null) {
            List<SubTableRelation> cached = CodeGenSettingsState.getInstance(project).getSubTableRelations(table);
            for (SubTableRelation rel : cached) {
                if (StringUtils.isNotBlank(rel.getSubTableName())) {
                    rel.recomputeDerivedNames(tablePrefixes);
                    result.add(rel);
                }
            }
            if (!result.isEmpty()) {
                return result;
            }
        }
        if (!Boolean.FALSE.equals(parameterMap.get("autoDiscoverSubTables")) && ddlMap != null && ddlMap.size() > 1) {
            return RelationTopologyDetector.discoverSubTableRelations(table, ddlMap, null, tablePrefixes);
        }
        return result;
    }

    /**
     * 供 Settings 模板中心使用的实时沙箱渲染器（使用内置样例表 user_order 实时渲染任意 FreeMarker 模板源码）
     */
    @NotNull
    public static String renderSamplePreview(@NotNull String templateName,
                                             @NotNull String templateContent,
                                             @NotNull String bootVersion,
                                             @NotNull String docStyle,
                                             @NotNull String mapperStrategy,
                                             boolean enableValidation,
                                             @Nullable String baseEntityClass) throws Exception {
        return renderSamplePreview(templateName, templateContent, bootVersion, docStyle, mapperStrategy, enableValidation, baseEntityClass, null);
    }

    @NotNull
    public static String renderSamplePreview(@NotNull String templateName,
                                             @NotNull String templateContent,
                                             @NotNull String bootVersion,
                                             @NotNull String docStyle,
                                             @NotNull String mapperStrategy,
                                             boolean enableValidation,
                                             @Nullable String baseEntityClass,
                                             @Nullable String responseWrapperClass) throws Exception {
        String sampleDdl =
                "CREATE TABLE `user_order` (\n" +
                "  `id` bigint NOT NULL PRIMARY KEY COMMENT '主键ID',\n" +
                "  `order_no` varchar(64) NOT NULL COMMENT '订单编号',\n" +
                "  `customer_email` varchar(128) DEFAULT NULL COMMENT '客户邮箱',\n" +
                "  `order_amount` decimal(12,2) NOT NULL COMMENT '订单金额',\n" +
                "  `order_status` tinyint NOT NULL DEFAULT 0 COMMENT '订单状态（0：待支付，1：已支付，2：已取消）',\n" +
                "  `create_date_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'\n" +
                ") COMMENT='用户订单表';";
        DdlSqlParser.ParsedTable sampleTable = DdlSqlParser.parseDdlScript(sampleDdl).get("user_order");
        List<Table> rawCols = sampleTable.getColumns();
        ValidationAnnotationResolver.enrichValidationAnnotations(rawCols, enableValidation);

        String baseSimple = extractSimpleClassName(StringUtils.defaultString(baseEntityClass));
        Set<String> ignored = StringUtils.isNotBlank(baseSimple)
                ? new HashSet<>(Arrays.asList("id", "create_date_time", "createdatetime"))
                : Collections.emptySet();
        List<Table> entityCols = filterEntityColumns(rawCols, ignored);

        String wrapperFqcn = StringUtils.defaultString(responseWrapperClass).trim();
        String wrapperSimple = extractSimpleClassName(wrapperFqcn);

        Map<String, Object> model = new HashMap<>();
        model.put("packageName", "com.example.order");
        model.put("moduleName", "order");
        model.put("feignServiceName", "order-service");
        model.put("responseWrapperClass", wrapperFqcn);
        model.put("responseWrapperSimpleName", wrapperSimple);
        model.put("responseWrapperSuccessMethod", "ok");
        model.put("treeTable", false);
        model.put("treeParentColumnName", "parentId");
        model.put("treeParentColumnUpper", "ParentId");
        model.put("treeIdColumnName", "id");
        model.put("treeIdColumnUpper", "Id");
        model.put("className", "userOrder");
        model.put("ClassName", "UserOrder");
        model.put("author", "syrobin");
        model.put("date", CodeGenerateUtils.getDate());
        model.put("functionName", "用户订单表");
        model.put("tableName", "user_order");
        model.put("bootVersion", bootVersion);
        model.put("docStyle", docStyle);
        model.put("mapperStrategy", mapperStrategy);
        model.put("enableValidation", enableValidation);
        model.put("validationPkg", "3".equals(bootVersion) ? "jakarta.validation.constraints" : "javax.validation.constraints");
        model.put("baseEntityClass", StringUtils.defaultString(baseEntityClass));
        model.put("baseEntitySimpleName", baseSimple);
        model.put("list", rawCols);
        model.put("entityColumns", entityCols);
        model.put("subTableRelations", Collections.singletonList(
                new SubTableRelation("user_order_item", "order_id", "id", "订单明细子表列表")
        ));
        model.put("braceL", "{");
        model.put("braceR", "}");
        model.put("excelEnabled", true);

        List<EnumsEntityContent> enumItems = EnumCommentParser.parseEnumItems("订单状态（0：待支付，1：已支付，2：已取消）");
        EnumsEntityName enumName = new EnumsEntityName();
        enumName.setSmallName("orderStatus");
        enumName.setBigName("OrderStatus");
        model.put("enumsList", Collections.singletonList(enumName));
        model.put("enumsEntityName", "OrderStatus");
        model.put("enumsEntitiesList", enumItems);

        BeanQuery sampleBq = new BeanQuery();
        sampleBq.setName("keyword");
        sampleBq.setFormat("String");
        sampleBq.setType("0");
        sampleBq.setDescription("搜索关键词");
        model.put("beanQuery", Collections.singletonList(sampleBq));

        ServiceQuery sampleSq = new ServiceQuery();
        sampleSq.setType("1");
        sampleSq.setColumn("`order_no`");
        sampleSq.setParameter("OrderNo");
        sampleSq.setParameterType(true);
        model.put("serviceQueryList", Collections.singletonList(sampleSq));

        Configuration cfg = new Configuration(Configuration.VERSION_2_3_29);
        cfg.setDefaultEncoding("UTF-8");
        Template tpl = new Template(templateName, new StringReader(templateContent), cfg);
        return FreeMarkers.renderTemplate(tpl, model);
    }

    /**
     * 解析指定代码层的输出工程根目录（支持多模块微服务路由）
     */
    @NotNull
    public static String resolveLayerOutputRoot(@Nullable Project project,
                                                @NotNull String defaultRootPath,
                                                @NotNull String layerKey,
                                                @Nullable Map<String, Object> parameterMap) {
        String normalizedDefault = CodeGenerateUtils.normalizeDirPath(defaultRootPath);
        if (parameterMap != null) {
            Object routingObj = parameterMap.get("layerModuleRouting");
            if (routingObj instanceof Map<?, ?> routingMap) {
                Object routed = routingMap.get(layerKey);
                if (routed == null) {
                    routed = routingMap.get("default");
                }
                if (routed != null && StringUtils.isNotBlank(String.valueOf(routed))) {
                    return CodeGenerateUtils.normalizeDirPath(String.valueOf(routed));
                }
            }
        }
        if (project != null) {
            CodeGenSettingsState.ConfigState st = CodeGenSettingsState.getInstance(project).getState();
            if (st != null && st.layerModuleRouting != null && !st.layerModuleRouting.isEmpty()) {
                String routed = st.layerModuleRouting.get(layerKey);
                if (StringUtils.isBlank(routed)) {
                    routed = st.layerModuleRouting.get("default");
                }
                if (StringUtils.isNotBlank(routed) && new File(routed).exists()) {
                    return CodeGenerateUtils.normalizeDirPath(routed);
                }
            }
        }
        return normalizedDefault;
    }

    private static List<Table> cloneColumns(List<Table> source) {
        if (source == null) {
            return null;
        }
        List<Table> copy = new ArrayList<>(source.size());
        for (Table t : source) {
            Table c = new Table();
            c.setTypeName(t.getTypeName());
            c.setColumnName(t.getColumnName());
            c.setColumnNameUpper(t.getColumnNameUpper());
            c.setDataType(t.getDataType());
            c.setColumnComment(t.getColumnComment());
            c.setNullable(t.getNullable());
            c.setMaxLength(t.getMaxLength());
            c.setPrimaryKey(t.getPrimaryKey());
            if (t.getValidationAnnotations() != null) {
                c.setValidationAnnotations(new ArrayList<>(t.getValidationAnnotations()));
            }
            copy.add(c);
        }
        return copy;
    }

    private static List<Table> filterEntityColumns(List<Table> rawColumns, Set<String> ignoredColumns) {
        if (ignoredColumns == null || ignoredColumns.isEmpty()) {
            return rawColumns;
        }
        List<Table> filtered = new ArrayList<>();
        for (Table col : rawColumns) {
            String rawName = StringUtils.defaultString(col.getTypeName()).toLowerCase(Locale.ROOT);
            String camelName = StringUtils.defaultString(col.getColumnName()).toLowerCase(Locale.ROOT);
            if (!ignoredColumns.contains(rawName) && !ignoredColumns.contains(camelName)) {
                filtered.add(col);
            }
        }
        return filtered;
    }

    private static String extractSimpleClassName(String fqcn) {
        if (StringUtils.isBlank(fqcn)) {
            return "";
        }
        String trimmed = fqcn.trim();
        int genericIdx = trimmed.indexOf('<');
        if (genericIdx > 0) {
            trimmed = trimmed.substring(0, genericIdx).trim();
        }
        int lastDot = trimmed.lastIndexOf('.');
        return lastDot >= 0 ? trimmed.substring(lastDot + 1) : trimmed;
    }

    private static String resolveTablePrefixes(@Nullable Project project, Map<String, Object> parameterMap) {
        Object paramVal = parameterMap.get("tablePrefixes");
        if (paramVal != null) {
            return String.valueOf(paramVal).trim();
        }
        if (project != null) {
            CodeGenSettingsState.ConfigState st = CodeGenSettingsState.getInstance(project).getState();
            if (st != null && st.tablePrefixes != null) {
                return st.tablePrefixes.trim();
            }
        }
        return "";
    }

    private static String resolveBaseEntityClass(@Nullable Project project, Map<String, Object> parameterMap) {
        Object paramVal = parameterMap.get("baseEntityClass");
        if (paramVal != null) {
            return String.valueOf(paramVal).trim();
        }
        if (project != null) {
            CodeGenSettingsState.ConfigState st = CodeGenSettingsState.getInstance(project).getState();
            if (st != null && st.baseEntityClass != null) {
                return st.baseEntityClass.trim();
            }
        }
        return "";
    }

    private static String resolveResponseWrapperClass(@Nullable Project project, Map<String, Object> parameterMap) {
        Object paramVal = parameterMap.get("responseWrapperClass");
        if (paramVal != null) {
            return String.valueOf(paramVal).trim();
        }
        if (project != null) {
            CodeGenSettingsState.ConfigState st = CodeGenSettingsState.getInstance(project).getState();
            if (st != null && st.responseWrapperClass != null) {
                return st.responseWrapperClass.trim();
            }
        }
        return "";
    }

    private static String resolveResponseWrapperSuccessMethod(@Nullable Project project, Map<String, Object> parameterMap) {
        Object paramVal = parameterMap.get("responseWrapperSuccessMethod");
        if (paramVal != null && StringUtils.isNotBlank(String.valueOf(paramVal))) {
            return String.valueOf(paramVal).trim();
        }
        if (project != null) {
            CodeGenSettingsState.ConfigState st = CodeGenSettingsState.getInstance(project).getState();
            if (st != null && StringUtils.isNotBlank(st.responseWrapperSuccessMethod)) {
                return st.responseWrapperSuccessMethod.trim();
            }
        }
        return "ok";
    }

    private static String resolveFeignServiceName(@Nullable Project project,
                                                  Map<String, Object> parameterMap,
                                                  String resolvedModuleName) {
        Object paramVal = parameterMap.get("feignServiceName");
        if (paramVal != null && StringUtils.isNotBlank(String.valueOf(paramVal))) {
            return String.valueOf(paramVal).trim();
        }
        if (project != null) {
            CodeGenSettingsState.ConfigState st = CodeGenSettingsState.getInstance(project).getState();
            if (st != null && StringUtils.isNotBlank(st.feignServiceName)) {
                return st.feignServiceName.trim();
            }
        }
        return resolvedModuleName.toLowerCase(Locale.ROOT) + "-service";
    }

    private static String resolveMapperStrategy(@Nullable Project project, Map<String, Object> parameterMap) {
        Object paramVal = parameterMap.get("mapperStrategy");
        if (paramVal != null && StringUtils.isNotBlank(String.valueOf(paramVal))) {
            return String.valueOf(paramVal).trim().toUpperCase(Locale.ROOT);
        }
        if (project != null) {
            CodeGenSettingsState.ConfigState st = CodeGenSettingsState.getInstance(project).getState();
            if (st != null && StringUtils.isNotBlank(st.mapperStrategy)) {
                return st.mapperStrategy.trim().toUpperCase(Locale.ROOT);
            }
        }
        return "BEAN_UTILS";
    }

    private static Set<String> resolveIgnoredBaseColumns(@Nullable Project project,
                                                         Map<String, Object> parameterMap,
                                                         boolean baseEntityEnabled) {
        if (!baseEntityEnabled) {
            return Collections.emptySet();
        }
        String csv = null;
        Object paramVal = parameterMap.get("ignoredBaseColumns");
        if (paramVal != null) {
            csv = String.valueOf(paramVal);
        } else if (project != null) {
            CodeGenSettingsState.ConfigState st = CodeGenSettingsState.getInstance(project).getState();
            if (st != null) {
                csv = st.ignoredBaseColumns;
            }
        }
        if (StringUtils.isBlank(csv)) {
            return Collections.emptySet();
        }
        Set<String> set = new HashSet<>();
        for (String item : csv.split("[,;\\s]+")) {
            if (StringUtils.isNotBlank(item)) {
                set.add(item.trim().toLowerCase(Locale.ROOT));
            }
        }
        return set;
    }

    private static Object queryRulesForTable(Map<String, Object> parameterMap, String key, String table) {
        Object perTableRules = parameterMap.get(key + "ByTable");
        if (perTableRules instanceof Map<?, ?> rules) {
            return Objects.requireNonNullElse(rules.get(table), new ArrayList<>());
        }
        return parameterMap.getOrDefault(key, new ArrayList<>());
    }

    private static DdlSqlParser.ParsedTable findParsedTableIgnoreCase(Map<String, DdlSqlParser.ParsedTable> ddlMap, String table) {
        if (ddlMap == null || table == null) {
            return null;
        }
        DdlSqlParser.ParsedTable direct = ddlMap.get(table);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, DdlSqlParser.ParsedTable> entry : ddlMap.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(table)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static Configuration buildFreemarkerConfiguration(@Nullable Project project, String path) throws Exception {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_29);
        cfg.setDefaultEncoding("UTF-8");

        List<TemplateLoader> loaders = new ArrayList<>();

        if (project != null) {
            CodeGenSettingsState.ConfigState st = CodeGenSettingsState.getInstance(project).getState();
            if (st != null && st.customTemplates != null && !st.customTemplates.isEmpty()) {
                StringTemplateLoader stringLoader = new StringTemplateLoader();
                for (Map.Entry<String, String> entry : st.customTemplates.entrySet()) {
                    if (StringUtils.isNoneBlank(entry.getKey(), entry.getValue())) {
                        stringLoader.putTemplate(entry.getKey(), entry.getValue());
                    }
                }
                loaders.add(stringLoader);
            }
        }

        File projectTemplateDir = new File(CodeGenerateUtils.normalizeDirPath(path) + "src/main/resources/templates/");
        if (projectTemplateDir.exists() && projectTemplateDir.isDirectory()) {
            loaders.add(new FileTemplateLoader(projectTemplateDir));
        }

        loaders.add(new ClassTemplateLoader(CodeGenerate.class, "/templates"));

        cfg.setTemplateLoader(new MultiTemplateLoader(loaders.toArray(new TemplateLoader[0])));
        return cfg;
    }

    private static boolean resolveEnableValidation(@Nullable Project project, Map<String, Object> parameterMap) {
        Object valObj = parameterMap.get("enableValidation");
        if (valObj instanceof Boolean) {
            return (Boolean) valObj;
        }
        if (project != null) {
            CodeGenSettingsState.ConfigState st = CodeGenSettingsState.getInstance(project).getState();
            if (st != null) {
                return st.enableValidation;
            }
        }
        return true;
    }

    private static IncrementalCodeMerger.WriteStrategy resolveWriteStrategy(@Nullable Project project, Map<String, Object> parameterMap) {
        Object paramStrategy = parameterMap.get("writeStrategy");
        if (paramStrategy != null) {
            return IncrementalCodeMerger.WriteStrategy.fromName(String.valueOf(paramStrategy));
        }
        if (project != null) {
            CodeGenSettingsState.ConfigState st = CodeGenSettingsState.getInstance(project).getState();
            if (st != null && StringUtils.isNotBlank(st.writeStrategy)) {
                return IncrementalCodeMerger.WriteStrategy.fromName(st.writeStrategy);
            }
        }
        return IncrementalCodeMerger.WriteStrategy.INCREMENTAL_MERGE;
    }

    private static String resolveBootVersion(@Nullable Project project, Map<String, Object> parameterMap) {
        Object paramBoot = parameterMap.get("bootVersion");
        if (paramBoot != null && StringUtils.isNotBlank(String.valueOf(paramBoot))) {
            return String.valueOf(paramBoot).trim();
        }
        if (project != null) {
            CodeGenSettingsState.ConfigState st = CodeGenSettingsState.getInstance(project).getState();
            if (st != null && StringUtils.isNotBlank(st.bootVersion)) {
                return st.bootVersion.trim();
            }
        }
        return "2";
    }

    private static String resolveDocStyle(@Nullable Project project, Map<String, Object> parameterMap) {
        Object paramDoc = parameterMap.get("docStyle");
        if (paramDoc != null && StringUtils.isNotBlank(String.valueOf(paramDoc))) {
            return String.valueOf(paramDoc).trim();
        }
        if (project != null) {
            CodeGenSettingsState.ConfigState st = CodeGenSettingsState.getInstance(project).getState();
            if (st != null && StringUtils.isNotBlank(st.docStyle)) {
                return st.docStyle.trim();
            }
        }
        return "NONE";
    }

    private static String resolveDatabaseName(@Nullable Project project, String path) throws Exception {
        if (project != null) {
            CodeGenSettingsState.ConfigState st = CodeGenSettingsState.getInstance(project).getState();
            if (st != null && StringUtils.isNotBlank(st.database)) {
                return st.database.trim();
            }
            if (st != null && StringUtils.isNotBlank(st.jdbcUrl)) {
                String extracted = DatabaseDialect.extractDatabaseFromJdbcUrl(st.jdbcUrl);
                if (StringUtils.isNotBlank(extracted)) {
                    return extracted;
                }
            }
        }
        File propFile = new File(CodeGenerateUtils.normalizeDirPath(path) + "src/main/resources/templates/config/mysql.properties");
        if (propFile.exists()) {
            return PropertiesUtil.getMySqlProperties(CodeGenerateUtils.normalizeDirPath(path)).getProperty("database");
        }
        throw new IllegalStateException("未配置数据库名称 (database)，请在生成向导「数据源连接」中填写或使用「离线 DDL 解析」。");
    }

    private static String resolveBasePackageName(@Nullable Project project, String path, @Nullable Map<String, Object> parameterMap) throws Exception {
        if (parameterMap != null && parameterMap.get("packageName") != null && StringUtils.isNotBlank(String.valueOf(parameterMap.get("packageName")))) {
            return String.valueOf(parameterMap.get("packageName")).trim();
        }
        if (project != null) {
            CodeGenSettingsState.ConfigState st = CodeGenSettingsState.getInstance(project).getState();
            if (st != null && StringUtils.isNotBlank(st.packageName)) {
                return st.packageName.trim();
            }
        }
        File propFile = new File(CodeGenerateUtils.normalizeDirPath(path) + "src/main/resources/templates/config/mysql.properties");
        if (propFile.exists()) {
            return PropertiesUtil.getMySqlProperties(CodeGenerateUtils.normalizeDirPath(path)).getProperty("packageName");
        }
        return "com.example";
    }

    private static String renderToString(Configuration cfg,
                                         String templateName,
                                         Map<String, Object> model) throws Exception {
        Template template = cfg.getTemplate(templateName, "UTF-8");
        return FreeMarkers.renderTemplate(template, model);
    }

    public static void fixedEnums(Map<String, Object> parameterMap) {
        if (isEnabled(parameterMap, "enums")) {
            String fieldName = supplyOnEdt(() -> Messages.showInputDialog(
                    "枚举字段名称（不填写全部生成，填写只生成填写字段）",
                    "是否设置对应枚举类",
                    Messages.getInformationIcon()
            ));
            if (StringUtils.isNotBlank(fieldName)) {
                parameterMap.put("fixedEnums", fieldName.trim());
            }
        }
    }

    public static void beanQuery(Map<String, Object> parameterMap, String tableName, String path) {
        List<BeanQuery> mapList = new ArrayList<>();
        if (StringUtils.isNotBlank(tableName) && StringUtils.isNotBlank(path)) {
            try {
                for (Project project : com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()) {
                    String basePath = project.getBasePath();
                    if (basePath != null && path.startsWith(CodeGenerateUtils.normalizeDirPath(basePath))) {
                        mapList = new ArrayList<>(CodeGenSettingsState.getInstance(project)
                                .getBeanQueryList(tableName, path));
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        parameterMap.put("beanQuery", mapList);
    }

    public static void serviceQuery(Map<String, Object> parameterMap, String tableName, String path) {
        List<ServiceQuery> mapList = new ArrayList<>();
        if (StringUtils.isNotBlank(tableName) && StringUtils.isNotBlank(path)) {
            try {
                for (Project project : com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()) {
                    String basePath = project.getBasePath();
                    if (basePath != null && path.startsWith(CodeGenerateUtils.normalizeDirPath(basePath))) {
                        mapList = new ArrayList<>(CodeGenSettingsState.getInstance(project)
                                .getServiceQueryList(tableName, path));
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        parameterMap.put("serviceQueryList", mapList);
    }
}
