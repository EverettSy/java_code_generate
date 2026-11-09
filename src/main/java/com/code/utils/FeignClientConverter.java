package com.code.utils;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 接口 / Controller 转 Spring Cloud OpenFeign Client 转换器（v3.0：支持将已有 Controller 类或 Service/API 接口直接转换为标准 @FeignClient 接口）
 *
 * 核心能力：
 * 1. 自动识别并剥离 Controller 方法体、Lombok/Autowired 注入注解与内部 Service 字段，提炼纯净的远程 RPC 契约接口。
 * 2. 支持无 Web 注解的普通 Java/Service 接口转换：根据方法名前缀（select/get/list/page/count/save/update/remove）自动补齐 @GetMapping/@PostMapping/@PutMapping/@DeleteMapping。
 * 3. 自动遵循 OpenFeign 参数规范：
 *    - GET 请求中的复杂查询对象自动追加 @SpringQueryMap，简单标量参数自动补齐 @RequestParam(value = "xxx", required = false)
 *    - POST/PUT/DELETE 请求中的复杂 BO/DTO 对象自动补齐 @RequestBody（保留 @Validated / @Valid）
 *    - 自动为缺省名称的 @RequestParam / @PathVariable 补齐显式 value 属性
 *
 * @author syrobin
 */
public class FeignClientConverter {

    private static final Set<String> SCALAR_TYPES = new HashSet<>(Arrays.asList(
            "String", "Integer", "int", "Long", "long", "Short", "short",
            "Double", "double", "Float", "float", "Boolean", "boolean",
            "Byte", "byte", "Character", "char", "BigDecimal", "BigInteger",
            "LocalDate", "LocalDateTime", "LocalTime", "Date"
    ));

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([a-zA-Z0-9_.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^\\s*import\\s+([^;]+);\\s*$", Pattern.MULTILINE);
    private static final Pattern REQUEST_MAPPING_PATH_PATTERN = Pattern.compile(
            "@RequestMapping\\s*\\(\\s*(?:value\\s*=\\s*|path\\s*=\\s*)?\"([^\"]+)\""
    );
    private static final Pattern TYPE_DECL_PATTERN = Pattern.compile(
            "public\\s+(?:class|interface)\\s+([A-Za-z0-9_]+)(?:\\s+extends\\s+[^{]+)?(?:\\s+implements\\s+[^{]+)?\\s*\\{"
    );

    /**
     * 将指定的 Controller 或接口源码字符串转换为 FeignClient 接口源码
     */
    @NotNull
    public static String convertToFeignClientSource(@NotNull String sourceCode,
                                                    @Nullable String customFeignServiceName,
                                                    @Nullable String customTargetPackage) {
        String origPackage = extractPackage(sourceCode);
        String targetPackage = StringUtils.isNotBlank(customTargetPackage)
                ? customTargetPackage.trim()
                : deriveFeignPackage(origPackage);

        Matcher typeMatcher = TYPE_DECL_PATTERN.matcher(sourceCode);
        if (!typeMatcher.find()) {
            throw new IllegalArgumentException("未在源码中识别到 public class 或 public interface 声明");
        }

        String origTypeName = typeMatcher.group(1);
        int classBodyOpenBrace = typeMatcher.end() - 1;
        String headerSegment = sourceCode.substring(0, typeMatcher.start());
        String classBodyContent = extractBalancedBody(sourceCode, classBodyOpenBrace);

        String domainName = stripTypeSuffix(origTypeName);
        String feignInterfaceName = domainName + "FeignClient";
        String lowerDomain = StringUtils.uncapitalize(domainName);

        String pathPrefix = extractRequestMappingPath(headerSegment);
        if (StringUtils.isBlank(pathPrefix)) {
            pathPrefix = "/" + lowerDomain;
        } else if (!pathPrefix.startsWith("/")) {
            pathPrefix = "/" + pathPrefix;
        }

        String serviceName = resolveFeignServiceName(customFeignServiceName, origPackage, lowerDomain);
        List<String> preservedClassDocAnnotations = extractClassDocAnnotations(headerSegment);
        Set<String> imports = collectAndFilterImports(sourceCode, origPackage, targetPackage);

        List<String> feignMethodBlocks = extractAndConvertMethods(classBodyContent);

        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(targetPackage)) {
            sb.append("package ").append(targetPackage).append(";\n\n");
        }

        for (String imp : imports) {
            sb.append("import ").append(imp).append(";\n");
        }
        sb.append("\n");

        sb.append("/**\n");
        sb.append(" * ").append(domainName).append(" 远程调用 Feign Client 接口（由 ").append(origTypeName).append(" 自动转换）\n");
        sb.append(" *\n");
        sb.append(" * @author syrobin\n");
        sb.append(" * @since ").append(CodeGenerateUtils.getDate()).append("\n");
        sb.append(" */\n");

        for (String docAnn : preservedClassDocAnnotations) {
            sb.append(docAnn).append("\n");
        }

        sb.append("@FeignClient(\n");
        sb.append("        name = \"").append(serviceName).append("\",\n");
        sb.append("        contextId = \"").append(lowerDomain).append("FeignClient\",\n");
        sb.append("        path = \"").append(pathPrefix).append("\"\n");
        sb.append(")\n");
        sb.append("public interface ").append(feignInterfaceName).append(" {\n");

        for (String methodBlock : feignMethodBlocks) {
            sb.append("\n").append(methodBlock).append("\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    /**
     * 将磁盘上的 Controller 或 Service 接口文件转换为同模块 feign 包下的 *FeignClient.java 文件（返回目标文件对象与源码）
     */
    @NotNull
    public static File resolveTargetFeignFile(@NotNull File sourceJavaFile) throws Exception {
        String sourceCode = Files.readString(sourceJavaFile.toPath(), StandardCharsets.UTF_8);
        Matcher typeMatcher = TYPE_DECL_PATTERN.matcher(sourceCode);
        String origTypeName = typeMatcher.find() ? typeMatcher.group(1) : sourceJavaFile.getName().replace(".java", "");
        String feignInterfaceName = stripTypeSuffix(origTypeName) + "FeignClient";

        File parentDir = sourceJavaFile.getParentFile();
        File feignDir;
        if (parentDir != null && (parentDir.getName().equalsIgnoreCase("controller")
                || parentDir.getName().equalsIgnoreCase("service")
                || parentDir.getName().equalsIgnoreCase("api"))) {
            feignDir = new File(parentDir.getParentFile(), "feign");
        } else {
            feignDir = new File(parentDir, "feign");
        }
        return new File(feignDir, feignInterfaceName + ".java");
    }

    private static String extractPackage(String sourceCode) {
        Matcher m = PACKAGE_PATTERN.matcher(sourceCode);
        return m.find() ? m.group(1).trim() : "";
    }

    public static String deriveFeignPackage(String origPackage) {
        if (StringUtils.isBlank(origPackage)) {
            return "com.example.feign";
        }
        if (origPackage.endsWith(".controller")) {
            return origPackage.substring(0, origPackage.length() - ".controller".length()) + ".feign";
        }
        if (origPackage.endsWith(".service.impl")) {
            return origPackage.substring(0, origPackage.length() - ".service.impl".length()) + ".feign";
        }
        if (origPackage.endsWith(".service")) {
            return origPackage.substring(0, origPackage.length() - ".service".length()) + ".feign";
        }
        if (origPackage.endsWith(".api")) {
            return origPackage.substring(0, origPackage.length() - ".api".length()) + ".feign";
        }
        if (origPackage.endsWith(".feign")) {
            return origPackage;
        }
        return origPackage + ".feign";
    }

    public static String stripTypeSuffix(String typeName) {
        if (StringUtils.isBlank(typeName)) {
            return "Default";
        }
        String[] suffixes = {"Controller", "ServiceImpl", "Service", "FeignClient", "Client", "Api"};
        for (String suffix : suffixes) {
            if (typeName.endsWith(suffix) && typeName.length() > suffix.length()) {
                return typeName.substring(0, typeName.length() - suffix.length());
            }
        }
        return typeName;
    }

    private static String extractRequestMappingPath(String headerSegment) {
        Matcher m = REQUEST_MAPPING_PATH_PATTERN.matcher(headerSegment);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "";
    }

    private static String resolveFeignServiceName(@Nullable String customServiceName,
                                                  String origPackage,
                                                  String lowerDomain) {
        if (StringUtils.isNotBlank(customServiceName)) {
            return customServiceName.trim();
        }
        if (StringUtils.isNotBlank(origPackage)) {
            String basePkg = origPackage
                    .replaceAll("\\.(controller|service\\.impl|service|api|feign)$", "");
            int lastDot = basePkg.lastIndexOf('.');
            String moduleSegment = lastDot >= 0 ? basePkg.substring(lastDot + 1) : basePkg;
            if (StringUtils.isNotBlank(moduleSegment) && !"example".equalsIgnoreCase(moduleSegment) && !"code".equalsIgnoreCase(moduleSegment)) {
                return moduleSegment.toLowerCase(Locale.ROOT) + "-service";
            }
        }
        return lowerDomain + "-service";
    }

    private static List<String> extractClassDocAnnotations(String headerSegment) {
        List<String> list = new ArrayList<>();
        String[] lines = headerSegment.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("@Api(") || trimmed.startsWith("@Tag(")) {
                list.add(trimmed);
            }
        }
        return list;
    }

    private static Set<String> collectAndFilterImports(String sourceCode, String origPackage, String targetPackage) {
        Set<String> imports = new LinkedHashSet<>();
        Matcher m = IMPORT_PATTERN.matcher(sourceCode);
        while (m.find()) {
            String imp = m.group(1).trim();
            if (imp.equals("lombok.Setter")
                    || imp.equals("lombok.Getter")
                    || imp.equals("lombok.RequiredArgsConstructor")
                    || imp.equals("lombok.extern.slf4j.Slf4j")
                    || imp.equals("org.springframework.beans.factory.annotation.Autowired")
                    || imp.equals("javax.annotation.Resource")
                    || imp.equals("jakarta.annotation.Resource")
                    || imp.equals("org.springframework.stereotype.Service")
                    || imp.equals("org.springframework.stereotype.Controller")
                    || imp.equals("org.springframework.web.bind.annotation.RestController")
                    || imp.equals("com.baomidou.mybatisplus.extension.service.IService")
                    || imp.endsWith("Service")
                    || imp.endsWith("Mapper")) {
                continue;
            }
            imports.add(imp);
        }
        imports.add("org.springframework.cloud.openfeign.FeignClient");
        imports.add("org.springframework.cloud.openfeign.SpringQueryMap");
        imports.add("org.springframework.web.bind.annotation.*");
        return imports;
    }

    private static String extractBalancedBody(String source, int openBraceIdx) {
        int depth = 0;
        for (int i = openBraceIdx; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(openBraceIdx + 1, i);
                }
            }
        }
        return source.substring(openBraceIdx + 1);
    }

    private static List<String> extractAndConvertMethods(String classBody) {
        List<String> results = new ArrayList<>();
        int idx = 0;
        int len = classBody.length();

        while (idx < len) {
            while (idx < len && Character.isWhitespace(classBody.charAt(idx))) {
                idx++;
            }
            if (idx >= len) {
                break;
            }

            int memberStart = idx;
            while (idx < len) {
                if (classBody.startsWith("/**", idx) || classBody.startsWith("/*", idx)) {
                    int endComment = classBody.indexOf("*/", idx + 2);
                    idx = (endComment >= 0) ? endComment + 2 : len;
                } else if (classBody.startsWith("//", idx)) {
                    int endLine = classBody.indexOf('\n', idx + 2);
                    idx = (endLine >= 0) ? endLine + 1 : len;
                } else if (classBody.charAt(idx) == '@') {
                    idx++;
                    while (idx < len && (Character.isJavaIdentifierPart(classBody.charAt(idx)) || classBody.charAt(idx) == '.')) {
                        idx++;
                    }
                    while (idx < len && Character.isWhitespace(classBody.charAt(idx))) {
                        idx++;
                    }
                    if (idx < len && classBody.charAt(idx) == '(') {
                        idx = skipBalancedParens(classBody, idx);
                    }
                } else if (Character.isWhitespace(classBody.charAt(idx))) {
                    idx++;
                } else {
                    break;
                }
            }

            if (idx >= len) {
                break;
            }

            int declStart = idx;
            int openParen = -1;
            int semiOrBrace = -1;
            char terminator = 0;
            while (idx < len) {
                char c = classBody.charAt(idx);
                if (c == '(' && openParen < 0) {
                    openParen = idx;
                    idx = skipBalancedParens(classBody, idx);
                    continue;
                }
                if (c == ';' || c == '{') {
                    semiOrBrace = idx;
                    terminator = c;
                    break;
                }
                idx++;
            }

            if (semiOrBrace < 0) {
                break;
            }

            String leadingMeta = classBody.substring(memberStart, declStart).trim();
            String signaturePart = classBody.substring(declStart, semiOrBrace).trim();

            if (terminator == '{') {
                idx = skipBalancedBraces(classBody, semiOrBrace);
            } else {
                idx = semiOrBrace + 1;
            }

            if (openParen < 0) {
                // 字段声明（如 private XxxService xxxService;），直接忽略
                continue;
            }

            String converted = convertSingleMethod(leadingMeta, signaturePart);
            if (StringUtils.isNotBlank(converted)) {
                results.add(converted);
            }
        }

        return results;
    }

    @Nullable
    private static String convertSingleMethod(String leadingMeta, String signaturePart) {
        String cleanSig = signaturePart.replaceAll("^public\\s+", "").replaceAll("^abstract\\s+", "").trim();
        int parenOpen = cleanSig.indexOf('(');
        int parenClose = cleanSig.lastIndexOf(')');
        if (parenOpen <= 0 || parenClose < parenOpen) {
            return null;
        }

        String returnAndName = cleanSig.substring(0, parenOpen).trim();
        String rawParams = cleanSig.substring(parenOpen + 1, parenClose).trim();

        int lastSpace = returnAndName.lastIndexOf(' ');
        if (lastSpace <= 0) {
            return null;
        }
        String returnType = returnAndName.substring(0, lastSpace).trim();
        String methodName = returnAndName.substring(lastSpace + 1).trim();

        String httpVerb = detectHttpVerb(leadingMeta, methodName);
        boolean hasMappingAnnotation = containsMappingAnnotation(leadingMeta);

        String transformedParams = transformMethodParameters(rawParams, httpVerb);

        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(leadingMeta)) {
            for (String line : leadingMeta.split("\\r?\\n")) {
                String trimmedLine = line.trim();
                if (trimmedLine.equals("@Override")) {
                    continue;
                }
                sb.append("    ").append(trimmedLine).append("\n");
            }
        }

        if (!hasMappingAnnotation) {
            String mappingAnn = buildDefaultMappingAnnotation(httpVerb, methodName);
            sb.append("    ").append(mappingAnn).append("\n");
        }

        sb.append("    ").append(returnType).append(" ").append(methodName)
                .append("(").append(transformedParams).append(");");
        return sb.toString();
    }

    private static boolean containsMappingAnnotation(String meta) {
        return meta.contains("@GetMapping")
                || meta.contains("@PostMapping")
                || meta.contains("@PutMapping")
                || meta.contains("@DeleteMapping")
                || meta.contains("@PatchMapping")
                || meta.contains("@RequestMapping");
    }

    private static String detectHttpVerb(String meta, String methodName) {
        if (meta.contains("@GetMapping")) {
            return "GET";
        }
        if (meta.contains("@PostMapping")) {
            return "POST";
        }
        if (meta.contains("@PutMapping")) {
            return "PUT";
        }
        if (meta.contains("@DeleteMapping")) {
            return "DELETE";
        }
        if (meta.contains("@PatchMapping")) {
            return "PATCH";
        }
        String lower = methodName.toLowerCase(Locale.ROOT);
        if (lower.startsWith("select") || lower.startsWith("get") || lower.startsWith("find")
                || lower.startsWith("list") || lower.startsWith("page") || lower.startsWith("count")
                || lower.startsWith("query")) {
            return "GET";
        }
        if (lower.startsWith("update") || lower.startsWith("modify") || lower.startsWith("edit")) {
            return "PUT";
        }
        if (lower.startsWith("delete") || lower.startsWith("remove")) {
            return "DELETE";
        }
        return "POST";
    }

    private static String buildDefaultMappingAnnotation(String httpVerb, String methodName) {
        return switch (httpVerb) {
            case "GET" -> "@GetMapping(\"/" + methodName + "\")";
            case "PUT" -> "@PutMapping(\"/" + methodName + "\")";
            case "DELETE" -> "@DeleteMapping(\"/" + methodName + "\")";
            default -> "@PostMapping(\"/" + methodName + "\")";
        };
    }

    private static String transformMethodParameters(String rawParams, String httpVerb) {
        if (StringUtils.isBlank(rawParams)) {
            return "";
        }
        List<String> paramTokens = splitTopLevelParams(rawParams);
        List<String> convertedParams = new ArrayList<>();

        for (String token : paramTokens) {
            String p = token.trim();
            if (p.isEmpty()) {
                continue;
            }
            // 提取参数名与类型
            int lastSpace = p.lastIndexOf(' ');
            if (lastSpace <= 0) {
                convertedParams.add(p);
                continue;
            }
            String prefixAndType = p.substring(0, lastSpace).trim();
            String paramName = p.substring(lastSpace + 1).trim();

            String bareType = extractBareTypeName(prefixAndType);

            // 若是 MyBatis-Plus Page 参数（如 Service 接口的 Page page），自动转换为 OpenFeign 兼容的 current / size 查询参数
            if ("Page".equals(bareType) || "IPage".equals(bareType)) {
                convertedParams.add("@RequestParam(value = \"current\", required = false) Integer current");
                convertedParams.add("@RequestParam(value = \"size\", required = false) Integer size");
                continue;
            }

            if (p.contains("@RequestBody") || p.contains("@SpringQueryMap") || p.contains("@RequestHeader")) {
                convertedParams.add(p);
                continue;
            }

            if (p.contains("@RequestParam")) {
                convertedParams.add(ensureAnnotationValueAttribute(p, "@RequestParam", paramName));
                continue;
            }

            if (p.contains("@PathVariable")) {
                convertedParams.add(ensureAnnotationValueAttribute(p, "@PathVariable", paramName));
                continue;
            }

            boolean isScalar = SCALAR_TYPES.contains(bareType);
            if ("GET".equals(httpVerb)) {
                if (isScalar) {
                    convertedParams.add("@RequestParam(value = \"" + paramName + "\", required = false) " + p);
                } else {
                    convertedParams.add("@SpringQueryMap " + p);
                }
            } else {
                if (isScalar) {
                    convertedParams.add("@RequestParam(value = \"" + paramName + "\") " + p);
                } else {
                    if (p.startsWith("@Validated ") || p.startsWith("@Valid ")) {
                        int firstSpace = p.indexOf(' ');
                        String valAnn = p.substring(0, firstSpace);
                        String rest = p.substring(firstSpace + 1).trim();
                        convertedParams.add(valAnn + " @RequestBody " + rest);
                    } else {
                        convertedParams.add("@RequestBody " + p);
                    }
                }
            }
        }

        return String.join(", ", convertedParams);
    }

    private static String ensureAnnotationValueAttribute(String paramDecl, String annotationName, String paramName) {
        int annIdx = paramDecl.indexOf(annotationName);
        if (annIdx < 0) {
            return paramDecl;
        }
        int afterAnn = annIdx + annotationName.length();
        while (afterAnn < paramDecl.length() && Character.isWhitespace(paramDecl.charAt(afterAnn))) {
            afterAnn++;
        }
        if (afterAnn < paramDecl.length() && paramDecl.charAt(afterAnn) == '(') {
            int closeParen = skipBalancedParens(paramDecl, afterAnn) - 1;
            String inside = paramDecl.substring(afterAnn + 1, closeParen).trim();
            if (inside.isEmpty()) {
                return paramDecl.substring(0, afterAnn) + "(value = \"" + paramName + "\")" + paramDecl.substring(closeParen + 1);
            }
            return paramDecl;
        } else {
            return paramDecl.substring(0, annIdx)
                    + annotationName + "(value = \"" + paramName + "\")"
                    + paramDecl.substring(annIdx + annotationName.length());
        }
    }

    private static String extractBareTypeName(String prefixAndType) {
        String withoutAnnotations = prefixAndType.replaceAll("@[A-Za-z0-9_$.]+(?:\\([^)]*\\))?\\s*", "").trim();
        int genericIdx = withoutAnnotations.indexOf('<');
        if (genericIdx > 0) {
            withoutAnnotations = withoutAnnotations.substring(0, genericIdx).trim();
        }
        int lastDot = withoutAnnotations.lastIndexOf('.');
        return lastDot >= 0 ? withoutAnnotations.substring(lastDot + 1).trim() : withoutAnnotations;
    }

    private static List<String> splitTopLevelParams(String rawParams) {
        List<String> list = new ArrayList<>();
        int depthAngle = 0;
        int depthParen = 0;
        int start = 0;
        for (int i = 0; i < rawParams.length(); i++) {
            char c = rawParams.charAt(i);
            if (c == '<') {
                depthAngle++;
            } else if (c == '>' && depthAngle > 0) {
                depthAngle--;
            } else if (c == '(') {
                depthParen++;
            } else if (c == ')' && depthParen > 0) {
                depthParen--;
            } else if (c == ',' && depthAngle == 0 && depthParen == 0) {
                list.add(rawParams.substring(start, i));
                start = i + 1;
            }
        }
        if (start < rawParams.length()) {
            list.add(rawParams.substring(start));
        }
        return list;
    }

    private static int skipBalancedParens(String s, int openParenIdx) {
        int depth = 0;
        for (int i = openParenIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            }
        }
        return s.length();
    }

    private static int skipBalancedBraces(String s, int openBraceIdx) {
        int depth = 0;
        for (int i = openBraceIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            }
        }
        return s.length();
    }
}
