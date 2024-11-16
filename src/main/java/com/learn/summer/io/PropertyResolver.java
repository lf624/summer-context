package com.learn.summer.io;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.util.*;
import java.util.function.Function;

public class PropertyResolver {
    Logger logger = LoggerFactory.getLogger(getClass());

    Map<String, String> properties = new HashMap<>();
    Map<Class<?>, Function<String, Object>> converters = new HashMap<>();

    public PropertyResolver(Properties props) {
        // 存入环境变量
        properties.putAll(System.getenv());
        // 存入 Properties
        Set<String> names = props.stringPropertyNames();
        for(String name : names)
            properties.put(name, props.getProperty(name));
        if(logger.isDebugEnabled()) {
            List<String> keys = new ArrayList<>(properties.keySet());
            Collections.sort(keys);
            keys.forEach(name ->
                    logger.debug("PropertyResolver: {}={}", name, properties.get(name)));
        }
        // register converters
        converters.put(String.class, s -> s);
        converters.put(boolean.class, Boolean::parseBoolean);
        converters.put(Boolean.class, Boolean::valueOf);

        converters.put(byte.class, Byte::parseByte);
        converters.put(Byte.class, Byte::valueOf);

        converters.put(short.class, Short::parseShort);
        converters.put(Short.class, Short::valueOf);

        converters.put(int.class, Integer::parseInt);
        converters.put(Integer.class, Integer::valueOf);

        converters.put(long.class, Long::parseLong);
        converters.put(Long.class, Long::valueOf);

        converters.put(float.class, Float::parseFloat);
        converters.put(Float.class, Float::valueOf);

        converters.put(double.class, Double::parseDouble);
        converters.put(Double.class, Double::valueOf);

        converters.put(LocalDate.class, LocalDate::parse);
        converters.put(LocalTime.class, LocalTime::parse);
        converters.put(LocalDateTime.class, LocalDateTime::parse);
        converters.put(ZonedDateTime.class, ZonedDateTime::parse);
        converters.put(Duration.class, Duration::parse);
        converters.put(ZoneId.class, ZoneId::of);
    }

    public boolean containsProperty(String key) {
        return this.properties.containsKey(key);
    }

    @Nullable // 表示方法可能会返回 null
    public String getProperty(String key) {
        // 解析 ${key:value}
        PropertyExpr keyExpr = parsePropertyExpr(key);
        if(keyExpr != null) {
            if(keyExpr.defaultValue() != null) {
                // 带默认值查询
                return getProperty(keyExpr.key(), keyExpr.defaultValue());
            } else {
                // 无默认值查询
                return getRequiredProperty(keyExpr.key());
            }
        }
        // 普通 key 查询
        String value = this.properties.get(key);
        if(value != null) {
            return parseValue(value);
        }
        return null;
    }

    public String getProperty(String key, String defaultValue) {
        String value = getProperty(key);
        // 若 properties 中存在配置，则用，若不存在就用默认值
        return value == null ? parseValue(defaultValue) : value;
    }

    public <T> T getProperty(String key, Class<T> targetType) {
        String value = getProperty(key);
        if(value == null)
            return null;
        return convert(targetType, value);
    }

    @Nullable
    public <T> T getProperty(String key, Class<T> targetType, T defaultValue) {
        String value = getProperty(key);
        if(value == null)
            return defaultValue;
        return convert(targetType, value);
    }

    public String getRequiredProperty(String key) {
        String value = getProperty(key);
        // 若 properties 中没有，则抛出 NullPointerException
        return Objects.requireNonNull(value, "Property '" + key + "' not found.");
    }

    public <T> T getRequiredProperty(String key, Class<T> targetType) {
        T value = getProperty(key, targetType);
        return Objects.requireNonNull(value, "Property '" + key + "' not found.");
    }

    @SuppressWarnings("unchecked")
    <T> T convert(Class<T> clazz, String value) {
        Function<String, Object> fn = converters.get(clazz);
        if(fn == null)
            throw new IllegalArgumentException("Unsupported value type: " + clazz.getName());
        return (T) fn.apply(value);
    }

    String parseValue(String value) {
        PropertyExpr expr = parsePropertyExpr(value);
        if(expr == null)
            return value;
        if(expr.defaultValue() != null) {
            return getProperty(expr.key(), expr.defaultValue());
        } else {
            return getRequiredProperty(expr.key());
        }
    }

    PropertyExpr parsePropertyExpr(String key) {
        if(key.startsWith("${") && key.endsWith("}")) {
            // 是否存在 defaultValue
            int n = key.indexOf(":");
            if(n == -1) {
                // 形式 ${key}
                String k = notEmpty(key.substring(2, key.length() - 1));
                return new PropertyExpr(k, null);
            } else {
                // 形式 ${key:value}
                String k = notEmpty(key.substring(2, n));
                return new PropertyExpr(k, key.substring(n + 1, key.length() - 1));
            }
        }
        return null;
    }

    String notEmpty(String key) {
        if(key.isEmpty())
            throw new IllegalArgumentException("Invalid key: " + key);
        return key;
    }
}

record PropertyExpr(String key, String defaultValue) {}