package com.learn.summer.utils;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class YamlUtils {
    public static Map<String, Object> loadYaml(String path) {
        var loaderOptions = new LoaderOptions();
        var dumperOptions = new DumperOptions();
        var representer = new Representer(dumperOptions);
        var resolver = new NoImplicitResolver();
        var yaml = new Yaml(new Constructor(loaderOptions),
                representer, dumperOptions, loaderOptions, resolver);
        return ClassPathUtils.readInputStream(path, yaml::load);
    }

    public static Map<String, Object> loadYamlAsPlain(String path) {
        Map<String, Object> source = loadYaml(path);
        Map<String, Object> plain = new LinkedHashMap<>();
        convertTo(source, "", plain);
        return plain;
    }

    static void convertTo(Map<String, Object> source, String prefix, Map<String, Object> plain) {
        for(String key : source.keySet()) {
            Object val = source.get(key);
            if(val instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) val;
                convertTo(map, prefix + key + ".", plain);
            } else if(val instanceof List) {
                plain.put(prefix + key, val);
            } else {
                plain.put(prefix + key, val.toString());
            }
        }
    }
}
// 禁用所有隐式转换，并将 value 视为字符串
class NoImplicitResolver extends Resolver {
    public NoImplicitResolver() {
        super();
        super.yamlImplicitResolvers.clear();
    }
}
