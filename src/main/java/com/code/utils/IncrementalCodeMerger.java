package com.code.utils;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.chains.SimpleDiffRequestChain;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 全语法增量代码合并与 Diff 对比预览引擎（v4.0 M1：支持 Java PSI AST 字段演进/注释/注解更新与枚举常量追加、Mapper.xml 节点增量合并、TypeScript 接口增量合并）
 *
 * @author syrobin
 */
public class IncrementalCodeMerger {

    /**
     * 文件写入策略枚举
     */
    public enum WriteStrategy {
        INCREMENTAL_MERGE("增量合并（保留手写字段与方法）"),
        DIFF_PREVIEW("Diff 对比预览确认后写入"),
        OVERWRITE("直接覆盖已有文件");

        private final String displayName;

        WriteStrategy(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static WriteStrategy fromName(String name) {
            for (WriteStrategy s : values()) {
                if (s.name().equalsIgnoreCase(name) || s.displayName.equals(name)) {
                    return s;
                }
            }
            return INCREMENTAL_MERGE;
        }
    }

    /**
     * 匹配 Java 类中的字段声明块（包含上方紧邻的 Javadoc 注释与注解）
     */
    private static final Pattern FIELD_BLOCK_PATTERN = Pattern.compile(
            "((?:[ \\t]*/\\*\\*[\\s\\S]*?\\*/\\s*)?(?:[ \\t]*@[A-Za-z0-9_$.]+(?:\\([^)]*\\))?\\s*)*[ \\t]*(?:private|protected|public)\\s+(?!static\\s+final\\b)[A-Za-z0-9_$<>\\[\\],\\s]+\\s+([a-zA-Z0-9_$]+)\\s*(?:=[^;]+)?;)"
    );

    /**
     * 匹配 import 语句
     */
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^\\s*import\\s+([^;]+);\\s*$", Pattern.MULTILINE);

    /**
     * 匹配枚举常量项，如 `WAIT_PAY(0, "待支付")`
     */
    private static final Pattern ENUM_CONSTANT_PATTERN = Pattern.compile(
            "((?:[ \\t]*/\\*\\*[\\s\\S]*?\\*/\\s*)?[ \\t]*([A-Z][A-Z0-9_]*)\\s*\\([^)]*\\))"
    );

    /**
     * 匹配 MyBatis Mapper.xml 中的 <id .../> 或 <result .../> 映射行
     */
    private static final Pattern XML_RESULT_ITEM_PATTERN = Pattern.compile(
            "(<(?:id|result)\\s+[^>]*column=\"([^\"]+)\"[^>]*/>)"
    );

    /**
     * 匹配 MyBatis Mapper.xml 中的 <sql id="Base_Column_List">...</sql>
     */
    private static final Pattern XML_BASE_COLUMN_LIST_PATTERN = Pattern.compile(
            "(<sql\\s+id=\"Base_Column_List\"\\s*>)([\\s\\S]*?)(</sql>)"
    );

    /**
     * 匹配 TypeScript export interface 块
     */
    private static final Pattern TS_INTERFACE_PATTERN = Pattern.compile(
            "(export\\s+interface\\s+([A-Za-z0-9_]+)\\s*\\{)([\\s\\S]*?)(\\n\\})"
    );

    /**
     * 匹配 TypeScript 接口内的属性声明块（含上方紧邻的 JSDoc 注释）
     */
    private static final Pattern TS_PROPERTY_PATTERN = Pattern.compile(
            "((?:[ \\t]*/\\*\\*[\\s\\S]*?\\*/\\s*)?[ \\t]*([a-zA-Z0-9_$]+)\\??\\s*:[^;]+;)"
    );

    /**
     * 根据指定的写入策略将生成的代码写入目标文件（自动按 .java / .xml / .ts 分发增量合并）
     */
    public static void writeWithStrategy(@Nullable Project project,
                                         String generatedContent,
                                         String targetFilePath,
                                         WriteStrategy strategy) throws Exception {
        File targetFile = new File(targetFilePath);
        if (!targetFile.exists()) {
            CodeGenerateUtils.writeFile(generatedContent, targetFilePath);
            return;
        }

        if (strategy == WriteStrategy.OVERWRITE) {
            CodeGenerateUtils.writeFile(generatedContent, targetFilePath);
            return;
        }

        String existingContent = Files.readString(targetFile.toPath(), StandardCharsets.UTF_8);
        String candidateContent = (strategy == WriteStrategy.INCREMENTAL_MERGE || strategy == WriteStrategy.DIFF_PREVIEW)
                ? mergeSourceByExtension(project, targetFile.getName(), existingContent, generatedContent)
                : generatedContent;

        if (StringUtils.equals(existingContent.trim(), candidateContent.trim())) {
            return;
        }

        if (strategy == WriteStrategy.DIFF_PREVIEW && project != null) {
            boolean confirmed = showDiffAndConfirmOnEdt(project, targetFile.getName(), existingContent, candidateContent);
            if (!confirmed) {
                return;
            }
        }

        CodeGenerateUtils.writeFile(candidateContent, targetFilePath);
    }

    /**
     * 根据文件扩展名智能分发增量合并策略（支持 .java / .xml / .ts）
     */
    @NotNull
    public static String mergeSourceByExtension(@Nullable Project project,
                                                @NotNull String fileName,
                                                @NotNull String existingContent,
                                                @NotNull String generatedContent) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".java")) {
            return mergeJavaSource(project, existingContent, generatedContent);
        }
        if (lower.endsWith(".xml")) {
            return mergeXmlSource(existingContent, generatedContent);
        }
        if (lower.endsWith(".ts")) {
            return mergeTypeScriptSource(existingContent, generatedContent);
        }
        return generatedContent;
    }

    /**
     * 兼容无 Project 参数的合并签名
     */
    public static String mergeJavaSource(String existingSource, String generatedSource) {
        return mergeJavaSource(null, existingSource, generatedSource);
    }

    /**
     * 智能合并已有 Java 源码与新生成的源码：
     * 优先尝试使用 IntelliJ PSI AST 语法树合并，若无 Project 上下文则回退至结构化正则合并
     */
    public static String mergeJavaSource(@Nullable Project project, String existingSource, String generatedSource) {
        if (StringUtils.isBlank(existingSource)) {
            return generatedSource;
        }
        if (StringUtils.isBlank(generatedSource)) {
            return existingSource;
        }

        if (project != null && !project.isDisposed() && ApplicationManager.getApplication() != null) {
            try {
                String psiMerged = mergeWithPsiAst(project, existingSource, generatedSource);
                if (StringUtils.isNotBlank(psiMerged)) {
                    return psiMerged;
                }
            } catch (Throwable ignored) {
                // 回退至确定性结构化合并
            }
        }

        return mergeJavaSourceFallback(existingSource, generatedSource);
    }

    @Nullable
    private static String mergeWithPsiAst(@NotNull Project project,
                                          @NotNull String existingSource,
                                          @NotNull String generatedSource) {
        AtomicReference<String> mergedRef = new AtomicReference<>();
        Runnable action = () -> {
            FileType javaFileType = FileTypeManager.getInstance().getFileTypeByExtension("java");
            PsiFileFactory fileFactory = PsiFileFactory.getInstance(project);
            PsiFile existingFile = fileFactory.createFileFromText("Existing.java", javaFileType, existingSource, System.currentTimeMillis(), true);
            PsiFile generatedFile = fileFactory.createFileFromText("Generated.java", javaFileType, generatedSource, System.currentTimeMillis(), false);

            Object[] existingClasses = invokeArray(existingFile, "getClasses");
            Object[] generatedClasses = invokeArray(generatedFile, "getClasses");
            if (existingClasses == null || generatedClasses == null || existingClasses.length == 0 || generatedClasses.length == 0) {
                return;
            }

            WriteCommandAction.runWriteCommandAction(project, "Syrobin PSI AST Merge", null, () -> {
                boolean changed = false;

                Object existingImportList = invokeNoArg(existingFile, "getImportList");
                Object generatedImportList = invokeNoArg(generatedFile, "getImportList");
                if (existingImportList instanceof PsiElement existingImpElem && generatedImportList != null) {
                    Object[] existingImps = invokeArray(existingImportList, "getAllImportStatements");
                    Object[] genImps = invokeArray(generatedImportList, "getAllImportStatements");
                    if (existingImps != null && genImps != null) {
                        Set<String> existingImportTexts = new HashSet<>();
                        for (Object imp : existingImps) {
                            if (imp instanceof PsiElement el) {
                                existingImportTexts.add(el.getText().replaceAll("\\s+", " ").trim());
                            }
                        }
                        for (Object genImp : genImps) {
                            if (genImp instanceof PsiElement genEl) {
                                String norm = genEl.getText().replaceAll("\\s+", " ").trim();
                                if (!existingImportTexts.contains(norm)) {
                                    existingImpElem.add(genEl.copy());
                                    existingImportTexts.add(norm);
                                    changed = true;
                                }
                            }
                        }
                    }
                }

                Object targetClassObj = existingClasses[0];
                Object sourceClassObj = generatedClasses[0];
                if (targetClassObj instanceof PsiElement targetClass && sourceClassObj instanceof PsiElement) {
                    Object[] targetFields = invokeArray(targetClassObj, "getFields");
                    Object[] sourceFields = invokeArray(sourceClassObj, "getFields");
                    if (targetFields != null && sourceFields != null) {
                        Map<String, PsiElement> existingFieldMap = new LinkedHashMap<>();
                        PsiElement lastInstanceField = null;
                        for (Object f : targetFields) {
                            String fName = String.valueOf(invokeNoArg(f, "getName"));
                            if (f instanceof PsiElement fEl) {
                                existingFieldMap.put(fName, fEl);
                                if (!"serialVersionUID".equals(fName)) {
                                    lastInstanceField = fEl;
                                }
                            }
                        }

                        for (Object genFieldObj : sourceFields) {
                            String fieldName = String.valueOf(invokeNoArg(genFieldObj, "getName"));
                            if (genFieldObj instanceof PsiElement genField && StringUtils.isNotBlank(fieldName)) {
                                PsiElement existingField = existingFieldMap.get(fieldName);
                                if (existingField == null) {
                                    PsiElement copiedField = genField.copy();
                                    if (lastInstanceField != null) {
                                        lastInstanceField = targetClass.addAfter(copiedField, lastInstanceField);
                                    } else {
                                        Object[] methods = invokeArray(targetClassObj, "getMethods");
                                        if (methods != null && methods.length > 0 && methods[0] instanceof PsiElement firstMethod) {
                                            lastInstanceField = targetClass.addBefore(copiedField, firstMethod);
                                        } else {
                                            lastInstanceField = targetClass.add(copiedField);
                                        }
                                    }
                                    existingFieldMap.put(fieldName, lastInstanceField);
                                    changed = true;
                                } else if (!"serialVersionUID".equals(fieldName)) {
                                    String normOld = existingField.getText().replaceAll("\\s+", " ").trim();
                                    String normNew = genField.getText().replaceAll("\\s+", " ").trim();
                                    if (!normOld.equals(normNew)) {
                                        PsiElement replaced = existingField.replace(genField.copy());
                                        if (lastInstanceField == existingField) {
                                            lastInstanceField = replaced;
                                        }
                                        existingFieldMap.put(fieldName, replaced);
                                        changed = true;
                                    }
                                }
                            }
                        }
                    }
                }

                if (changed) {
                    CodeStyleManager.getInstance(project).reformat(existingFile);
                }
            });

            mergedRef.set(existingFile.getText());
        };

        if (ApplicationManager.getApplication().isDispatchThread()) {
            action.run();
        } else {
            ApplicationManager.getApplication().invokeAndWait(action);
        }
        return mergedRef.get();
    }

    @Nullable
    private static Object invokeNoArg(@Nullable Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method m = target.getClass().getMethod(methodName);
            return m.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private static Object[] invokeArray(@Nullable Object target, String methodName) {
        Object res = invokeNoArg(target, methodName);
        return (res instanceof Object[] arr) ? arr : null;
    }

    private static String mergeJavaSourceFallback(String existingSource, String generatedSource) {
        String result = existingSource;

        // 1. 增量补齐缺失的 import
        Set<String> existingImports = extractImports(existingSource);
        Set<String> generatedImports = extractImports(generatedSource);
        List<String> missingImports = new ArrayList<>();
        for (String imp : generatedImports) {
            if (!existingImports.contains(imp)) {
                missingImports.add("import " + imp + ";");
            }
        }

        if (!missingImports.isEmpty()) {
            Matcher pkgMatcher = Pattern.compile("^\\s*package\\s+[^;]+;\\s*$", Pattern.MULTILINE).matcher(result);
            if (pkgMatcher.find()) {
                int insertPos = pkgMatcher.end();
                String importBlock = "\n" + String.join("\n", missingImports);
                result = result.substring(0, insertPos) + importBlock + result.substring(insertPos);
            }
        }

        // 2. 若是枚举类 (public enum ...)，增量合并新增的枚举常量项
        if (result.contains("public enum ") && generatedSource.contains("public enum ")) {
            result = mergeEnumConstants(result, generatedSource);
        }

        // 3. 同步已有字段的演进（类型/注释/校验注解变更）并收集新增字段
        Map<String, String> existingFields = extractFieldMap(result);
        Map<String, String> generatedFields = extractFieldMap(generatedSource);
        List<String> newFieldBlocks = new ArrayList<>();
        for (Map.Entry<String, String> entry : generatedFields.entrySet()) {
            String fieldName = entry.getKey();
            String genBlock = entry.getValue();
            if (!existingFields.containsKey(fieldName)) {
                newFieldBlocks.add(genBlock);
            } else {
                String oldBlock = existingFields.get(fieldName);
                String normOld = oldBlock.replaceAll("\\s+", " ").trim();
                String normNew = genBlock.replaceAll("\\s+", " ").trim();
                if (!normOld.equals(normNew)) {
                    result = result.replace(oldBlock.trim(), genBlock.trim());
                }
            }
        }

        if (!newFieldBlocks.isEmpty()) {
            int lastBraceIndex = result.lastIndexOf('}');
            if (lastBraceIndex > 0) {
                StringBuilder insertion = new StringBuilder("\n");
                for (String block : newFieldBlocks) {
                    insertion.append("\n    ").append(block.trim()).append("\n");
                }
                result = result.substring(0, lastBraceIndex) + insertion + result.substring(lastBraceIndex);
            }
        }

        return result;
    }

    private static String mergeEnumConstants(String existingEnumSource, String generatedEnumSource) {
        Map<String, String> existingConsts = extractEnumConstants(existingEnumSource);
        Map<String, String> generatedConsts = extractEnumConstants(generatedEnumSource);
        List<String> newConstBlocks = new ArrayList<>();
        for (Map.Entry<String, String> entry : generatedConsts.entrySet()) {
            if (!existingConsts.containsKey(entry.getKey())) {
                newConstBlocks.add(entry.getValue().trim());
            }
        }
        if (newConstBlocks.isEmpty()) {
            return existingEnumSource;
        }
        int enumIdx = existingEnumSource.indexOf("public enum ");
        int openBrace = existingEnumSource.indexOf('{', enumIdx);
        int semiIdx = existingEnumSource.indexOf(';', openBrace);
        if (openBrace > 0 && semiIdx > openBrace) {
            String insertion = ",\n\n    " + String.join(",\n\n    ", newConstBlocks);
            return existingEnumSource.substring(0, semiIdx) + insertion + existingEnumSource.substring(semiIdx);
        }
        return existingEnumSource;
    }

    private static Map<String, String> extractEnumConstants(String source) {
        Map<String, String> map = new LinkedHashMap<>();
        int enumIdx = source.indexOf("public enum ");
        if (enumIdx < 0) {
            return map;
        }
        int openBrace = source.indexOf('{', enumIdx);
        int semiIdx = source.indexOf(';', openBrace);
        if (openBrace < 0 || semiIdx < openBrace) {
            return map;
        }
        String constantRegion = source.substring(openBrace + 1, semiIdx);
        Matcher m = ENUM_CONSTANT_PATTERN.matcher(constantRegion);
        while (m.find()) {
            map.put(m.group(2).trim(), m.group(1));
        }
        return map;
    }

    /**
     * 增量合并 MyBatis Mapper.xml（v4.0 M1）：
     * 合并 <resultMap id="BaseResultMap"> 中的新增 <result/> 映射与 <sql id="Base_Column_List"> 中的新增列，保留手写 SQL 标签
     */
    @NotNull
    public static String mergeXmlSource(@Nullable String existingXml, @Nullable String generatedXml) {
        if (StringUtils.isBlank(existingXml)) {
            return StringUtils.defaultString(generatedXml);
        }
        if (StringUtils.isBlank(generatedXml)) {
            return existingXml;
        }
        String result = existingXml;

        // 1. 合并 BaseResultMap 中新增的 <id/> / <result/> 节点
        Set<String> existingCols = new LinkedHashSet<>();
        Matcher existResultMatcher = XML_RESULT_ITEM_PATTERN.matcher(result);
        while (existResultMatcher.find()) {
            existingCols.add(existResultMatcher.group(2).trim().toLowerCase(Locale.ROOT));
        }

        List<String> newResultTags = new ArrayList<>();
        Matcher genResultMatcher = XML_RESULT_ITEM_PATTERN.matcher(generatedXml);
        while (genResultMatcher.find()) {
            String tag = genResultMatcher.group(1).trim();
            String colName = genResultMatcher.group(2).trim();
            if (!existingCols.contains(colName.toLowerCase(Locale.ROOT))) {
                newResultTags.add("        " + tag);
                existingCols.add(colName.toLowerCase(Locale.ROOT));
            }
        }

        if (!newResultTags.isEmpty()) {
            int closeResultMap = result.indexOf("</resultMap>");
            if (closeResultMap > 0) {
                String insertion = String.join("\n", newResultTags) + "\n    ";
                result = result.substring(0, closeResultMap) + insertion + result.substring(closeResultMap);
            }
        }

        // 2. 合并 <sql id="Base_Column_List"> 中的列名列表
        Matcher existSqlMatcher = XML_BASE_COLUMN_LIST_PATTERN.matcher(result);
        Matcher genSqlMatcher = XML_BASE_COLUMN_LIST_PATTERN.matcher(generatedXml);
        if (existSqlMatcher.find() && genSqlMatcher.find()) {
            String existColsRaw = existSqlMatcher.group(2).trim();
            String genColsRaw = genSqlMatcher.group(2).trim();
            Set<String> mergedColSet = new LinkedHashSet<>();
            Map<String, String> caseMap = new LinkedHashMap<>();
            for (String c : existColsRaw.split(",")) {
                String trimmed = c.trim();
                if (!trimmed.isEmpty()) {
                    mergedColSet.add(trimmed.toLowerCase(Locale.ROOT));
                    caseMap.put(trimmed.toLowerCase(Locale.ROOT), trimmed);
                }
            }
            boolean addedCol = false;
            for (String c : genColsRaw.split(",")) {
                String trimmed = c.trim();
                if (!trimmed.isEmpty() && mergedColSet.add(trimmed.toLowerCase(Locale.ROOT))) {
                    caseMap.put(trimmed.toLowerCase(Locale.ROOT), trimmed);
                    addedCol = true;
                }
            }
            if (addedCol) {
                String newSqlBody = "\n        " + String.join(", ", caseMap.values()) + "\n    ";
                result = result.substring(0, existSqlMatcher.start(2)) + newSqlBody + result.substring(existSqlMatcher.end(2));
            }
        }

        return result;
    }

    /**
     * 增量合并前端 TypeScript (.api.ts) 文件（v4.0 M1）：
     * 合并各 export interface 中新增的属性与注释，同时完整保留开发者手写的自定义请求函数与拦截器逻辑
     */
    @NotNull
    public static String mergeTypeScriptSource(@Nullable String existingTs, @Nullable String generatedTs) {
        if (StringUtils.isBlank(existingTs)) {
            return StringUtils.defaultString(generatedTs);
        }
        if (StringUtils.isBlank(generatedTs)) {
            return existingTs;
        }
        String result = existingTs;

        Map<String, String> genInterfaces = new LinkedHashMap<>();
        Matcher genMatcher = TS_INTERFACE_PATTERN.matcher(generatedTs);
        while (genMatcher.find()) {
            genInterfaces.put(genMatcher.group(2).trim(), genMatcher.group(3));
        }

        for (Map.Entry<String, String> entry : genInterfaces.entrySet()) {
            String ifaceName = entry.getKey();
            String genBody = entry.getValue();
            Pattern singleIfacePattern = Pattern.compile(
                    "(export\\s+interface\\s+" + Pattern.quote(ifaceName) + "\\s*\\{)([\\s\\S]*?)(\\n\\})"
            );
            Matcher existMatcher = singleIfacePattern.matcher(result);
            if (!existMatcher.find()) {
                continue;
            }
            String existBody = existMatcher.group(2);
            Set<String> existingProps = new HashSet<>();
            Matcher existPropMatcher = TS_PROPERTY_PATTERN.matcher(existBody);
            while (existPropMatcher.find()) {
                existingProps.add(existPropMatcher.group(2).trim());
            }

            List<String> newPropBlocks = new ArrayList<>();
            Matcher genPropMatcher = TS_PROPERTY_PATTERN.matcher(genBody);
            while (genPropMatcher.find()) {
                String propName = genPropMatcher.group(2).trim();
                if (!existingProps.contains(propName)) {
                    newPropBlocks.add("  " + genPropMatcher.group(1).trim());
                    existingProps.add(propName);
                }
            }

            if (!newPropBlocks.isEmpty()) {
                String updatedBody = existBody + "\n" + String.join("\n", newPropBlocks);
                result = result.substring(0, existMatcher.start(2)) + updatedBody + result.substring(existMatcher.end(2));
            }
        }

        return result;
    }

    private static Set<String> extractImports(String source) {
        Set<String> imports = new LinkedHashSet<>();
        Matcher matcher = IMPORT_PATTERN.matcher(source);
        while (matcher.find()) {
            imports.add(matcher.group(1).trim());
        }
        return imports;
    }

    private static Map<String, String> extractFieldMap(String source) {
        Map<String, String> fields = new LinkedHashMap<>();
        Matcher matcher = FIELD_BLOCK_PATTERN.matcher(source);
        while (matcher.find()) {
            String fullBlock = matcher.group(1);
            String fieldName = matcher.group(2);
            if (StringUtils.isNotBlank(fieldName) && !"serialVersionUID".equals(fieldName)) {
                fields.put(fieldName.trim(), fullBlock);
            }
        }
        return fields;
    }

    /**
     * 构建单个文件的 DiffRequest（支持 Java / XML / TypeScript / HTTP 语法高亮与增量合并预演）
     */
    @NotNull
    public static SimpleDiffRequest buildFileDiffRequest(@NotNull Project project,
                                                         @NotNull String targetFilePath,
                                                         @NotNull String generatedContent,
                                                         @NotNull WriteStrategy strategy) {
        File targetFile = new File(targetFilePath);
        String fileName = targetFile.getName();
        String existingContent = "";
        if (targetFile.exists()) {
            try {
                existingContent = Files.readString(targetFile.toPath(), StandardCharsets.UTF_8);
            } catch (Exception ignored) {
            }
        }

        boolean supportsMerge = fileName.endsWith(".java") || fileName.endsWith(".xml") || fileName.endsWith(".ts");
        String rightSideContent = (targetFile.exists() && supportsMerge && strategy != WriteStrategy.OVERWRITE)
                ? mergeSourceByExtension(project, fileName, existingContent, generatedContent)
                : generatedContent;

        String leftTitle = targetFile.exists()
                ? "当前磁盘已有文件 (" + fileName + ")"
                : "当前磁盘尚无此文件（将新增 " + fileName + "）";
        String rightTitle = (targetFile.exists() && supportsMerge && strategy != WriteStrategy.OVERWRITE)
                ? "AST 增量合并后预览 (" + fileName + ")"
                : "新生成代码预览 (" + fileName + ")";

        String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1) : "java";
        FileType fileType = FileTypeManager.getInstance().getFileTypeByExtension(ext);
        DiffContentFactory contentFactory = DiffContentFactory.getInstance();
        return new SimpleDiffRequest(
                "Syrobin Code Generate 实时预演 — " + fileName,
                contentFactory.create(project, existingContent, fileType),
                contentFactory.create(project, rightSideContent, fileType),
                leftTitle,
                rightTitle
        );
    }

    /**
     * 在向导右侧预览表中双击某一行时，直接拉起该文件的实时 Diff 预演窗口
     */
    public static void openSingleFilePreviewDiff(@NotNull Project project,
                                                 @NotNull String targetFilePath,
                                                 @NotNull String generatedContent,
                                                 @NotNull WriteStrategy strategy) {
        Runnable showTask = () -> {
            SimpleDiffRequest diffRequest = buildFileDiffRequest(project, targetFilePath, generatedContent, strategy);
            DiffManager.getInstance().showDiff(project, diffRequest);
        };

        if (ApplicationManager.getApplication().isDispatchThread()) {
            showTask.run();
        } else {
            ApplicationManager.getApplication().invokeLater(showTask);
        }
    }

    /**
     * 批量变更集汇总 Diff 预览（使用 SimpleDiffRequestChain 在同一窗口内切换预览整张表或整批生成的所有文件变更）
     */
    public static void openBatchDiffPreviewChain(@NotNull Project project,
                                                 @NotNull Map<String, String> renderedFilesMap,
                                                 @NotNull WriteStrategy strategy) {
        if (renderedFilesMap.isEmpty()) {
            return;
        }
        Runnable showChainTask = () -> {
            List<DiffRequest> requests = new ArrayList<>(renderedFilesMap.size());
            for (Map.Entry<String, String> entry : renderedFilesMap.entrySet()) {
                requests.add(buildFileDiffRequest(project, entry.getKey(), entry.getValue(), strategy));
            }
            SimpleDiffRequestChain chain = new SimpleDiffRequestChain(requests);
            DiffManager.getInstance().showDiff(project, chain, DiffDialogHints.DEFAULT);
        };

        if (ApplicationManager.getApplication().isDispatchThread()) {
            showChainTask.run();
        } else {
            ApplicationManager.getApplication().invokeLater(showChainTask);
        }
    }

    private static boolean showDiffAndConfirmOnEdt(Project project,
                                                    String fileName,
                                                    String existingContent,
                                                    String mergedContent) {
        AtomicBoolean approved = new AtomicBoolean(false);
        Runnable task = () -> {
            String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1) : "java";
            FileType fileType = FileTypeManager.getInstance().getFileTypeByExtension(ext);
            DiffContentFactory contentFactory = DiffContentFactory.getInstance();
            SimpleDiffRequest diffRequest = new SimpleDiffRequest(
                    "Syrobin Code Generate Diff 预览 - " + fileName,
                    contentFactory.create(project, existingContent, fileType),
                    contentFactory.create(project, mergedContent, fileType),
                    "当前工程已有文件 (" + fileName + ")",
                    "增量合并后新文件 (" + fileName + ")"
            );
            DiffManager.getInstance().showDiff(project, diffRequest);

            int choice = Messages.showYesNoDialog(
                    project,
                    "是否将合并后的变更写入文件 [" + fileName + "] ？",
                    "确认写入变更 - " + fileName,
                    "确认写入",
                    "跳过此文件",
                    Messages.getQuestionIcon()
            );
            approved.set(choice == Messages.YES);
        };

        if (ApplicationManager.getApplication().isDispatchThread()) {
            task.run();
        } else {
            ApplicationManager.getApplication().invokeAndWait(task);
        }
        return approved.get();
    }
}
