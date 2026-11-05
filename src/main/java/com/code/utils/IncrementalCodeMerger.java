package com.code.utils;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 增量代码合并与 Diff 对比预览引擎（P2 阶段核心能力）
 *
 * 解决痛点：
 * 当表结构新增列或修改注释后重新生成代码时，保留已有 Entity / BO / VO / Query / Service 等类中
 * 开发者手写的业务方法、自定义字段与自定义 import，仅增量注入新增的字段、import 与方法，
 * 并支持调起 IntelliJ 原生 DiffManager 窗口对比确认后再写入。
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
            "((?:\\s*/\\*\\*[\\s\\S]*?\\*/\\s*)?(?:\\s*@[A-Za-z0-9_$.]+(?:\\([^)]*\\))?\\s*)*\\s*(?:private|protected|public)\\s+(?!static\\s+final\\b)[A-Za-z0-9_$<>\\[\\],\\s]+\\s+([a-zA-Z0-9_$]+)\\s*(?:=[^;]+)?;)"
    );

    /**
     * 匹配 import 语句
     */
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^\\s*import\\s+([^;]+);\\s*$", Pattern.MULTILINE);

    /**
     * 根据指定的写入策略将生成的代码写入目标文件
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
                ? mergeJavaSource(existingContent, generatedContent)
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
     * 智能合并已有 Java 源码与新生成的源码：
     * 1. 补充缺失的 import 语句
     * 2. 提取新生成源码中所有在旧源码里不存在的成员变量（含 Javadoc 与注解），追加到旧类末尾
     */
    public static String mergeJavaSource(String existingSource, String generatedSource) {
        if (StringUtils.isBlank(existingSource)) {
            return generatedSource;
        }

        String result = existingSource;

        // 1. 合并缺失的 import
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

        // 2. 提取已有字段名集合
        Set<String> existingFieldNames = extractFieldMap(result).keySet();

        // 3. 找出新生成代码中新增的字段块
        Map<String, String> generatedFields = extractFieldMap(generatedSource);
        List<String> newFieldBlocks = new ArrayList<>();
        for (Map.Entry<String, String> entry : generatedFields.entrySet()) {
            if (!existingFieldNames.contains(entry.getKey())) {
                newFieldBlocks.add(entry.getValue());
            }
        }

        // 4. 将新增字段块插入到类定义最后一个 '}' 之前
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
     * 在 EDT 线程打开 IntelliJ 原生 DiffViewer 对比窗口并询问用户是否应用更新
     */
    private static boolean showDiffAndConfirmOnEdt(Project project,
                                                    String fileName,
                                                    String existingContent,
                                                    String mergedContent) {
        AtomicBoolean approved = new AtomicBoolean(false);
        Runnable task = () -> {
            FileType javaFileType = FileTypeManager.getInstance().getFileTypeByExtension("java");
            DiffContentFactory contentFactory = DiffContentFactory.getInstance();
            SimpleDiffRequest diffRequest = new SimpleDiffRequest(
                    "Skl Code Generate Diff 预览 - " + fileName,
                    contentFactory.create(project, existingContent, javaFileType),
                    contentFactory.create(project, mergedContent, javaFileType),
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
