package com.code.i18n;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Properties;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

/**
 * 插件国际化资源包管理中心（v3.0 M3：支持中英双语切换与参数化消息格式化）
 *
 * @author syrobin
 */
public final class CodeGenBundle {

    public static final String BUNDLE_NAME = "messages.CodeGenBundle";

    private static volatile Locale currentLocale = Locale.SIMPLIFIED_CHINESE;

    private CodeGenBundle() {
    }

    public static void setLocale(@NotNull Locale locale) {
        currentLocale = locale;
    }

    @NotNull
    public static Locale getLocale() {
        return currentLocale;
    }

    @NotNull
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE_NAME) String key, Object... params) {
        return message(currentLocale, key, params);
    }

    @NotNull
    public static String message(@NotNull Locale locale, @NotNull String key, Object... params) {
        String pattern = resolvePattern(locale, key);
        if (params == null || params.length == 0) {
            return pattern;
        }
        try {
            return MessageFormat.format(pattern, params);
        } catch (Exception ignored) {
            return pattern;
        }
    }

    private static String resolvePattern(@NotNull Locale locale, @NotNull String key) {
        String resourcePath = Locale.SIMPLIFIED_CHINESE.getLanguage().equalsIgnoreCase(locale.getLanguage())
                ? "/messages/CodeGenBundle_zh_CN.properties"
                : "/messages/CodeGenBundle.properties";
        try (InputStream is = CodeGenBundle.class.getResourceAsStream(resourcePath)) {
            if (is != null) {
                Properties props = new Properties();
                props.load(new InputStreamReader(is, StandardCharsets.UTF_8));
                String val = props.getProperty(key);
                if (val != null) {
                    return val;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            ResourceBundle bundle = PropertyResourceBundle.getBundle(BUNDLE_NAME, locale);
            if (bundle.containsKey(key)) {
                return bundle.getString(key);
            }
        } catch (Exception ignored) {
        }
        return key;
    }
}
