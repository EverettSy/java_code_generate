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
 * 增量代码合并与 Diff 对比预览引擎（v3.0：基于 IntelliJ 平台通用 PsiFile + 反射式 PsiJavaFile AST 语法树级智能合并 + CodeStyleManager 自动格式化 + 单文件与批量 DiffRequestChain 预演）
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
                ? mergeJavaSource(project, existingContent, generatedContent)
                : generatedContent;

        if (Objects.equals(existingContent.trim(), candidateContent.trim())) {
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
                        Set<String> existingFieldNames = new HashSet<>();
                        PsiElement lastInstanceField = null;
                        for (Object f : targetFields) {
                            String fName = String.valueOf(invokeNoArg(f, "getName"));
                            existingFieldNames.add(fName);
                            if (f instanceof PsiElement fEl && !"serialVersionUID".equals(fName)) {
                                lastInstanceField = fEl;
                            }
                        }

                        for (Object genFieldObj : sourceFields) {
                            String fieldName = String.valueOf(invokeNoArg(genFieldObj, "getName"));
                            if (genFieldObj instanceof PsiElement genField && StringUtils.isNotBlank(fieldName) && !existingFieldNames.contains(fieldName)) {
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
                                existingFieldNames.add(fieldName);
                                changed = true;
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

        Set<String> existingFieldNames = extractFieldMap(result).keySet();
        Map<String, String> generatedFields = extractFieldMap(generatedSource);
        List<String> newFieldBlocks = new ArrayList<>();
        for (Map.Entry<String, String> entry : generatedFields.entrySet()) {
            if (!existingFieldNames.contains(entry.getKey())) {
                newFieldBlocks.add(entry.getValue());
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
     * 构建单个文件的 DiffRequest（支持 Java / XML / TypeScript 语法高亮）
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

        boolean isJava = fileName.endsWith(".java");
        String rightSideContent = (targetFile.exists() && isJava && strategy != WriteStrategy.OVERWRITE)
                ? mergeJavaSource(project, existingContent, generatedContent)
                : generatedContent;

        String leftTitle = targetFile.exists()
                ? "当前磁盘已有文件 (" + fileName + ")"
                : "当前磁盘尚无此文件（将新增 " + fileName + "）";
        String rightTitle = (targetFile.exists() && isJava && strategy != WriteStrategy.OVERWRITE)
                ? "PSI AST 增量合并后预览 (" + fileName + ")"
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
     * 批量变更集汇总 Diff 预览（v3.0 建设矩阵域 1.2：使用 SimpleDiffRequestChain 在同一窗口内切换预览整张表或整批生成的所有文件变更）
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
            FileType javaFileType = FileTypeManager.getInstance().getFileTypeByExtension("java");
            DiffContentFactory contentFactory = DiffContentFactory.getInstance();
            SimpleDiffRequest diffRequest = new SimpleDiffRequest(
                    "Syrobin Code Generate Diff 预览 - " + fileName,
                    contentFactory.create(project, existingContent, javaFileType),
                    contentFactory.create(project, mergedContent, javaFileType),
                    "当前工程已有文件 (" + fileName + ")",
                    "PSI 增量合并后新文件 (" + fileName + ")"
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
