package com.learn.summer.utils;

import com.learn.summer.annotation.Bean;
import com.learn.summer.annotation.Component;
import com.learn.summer.exception.BeanDefinitionException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class ClassUtils {
    public static <A extends Annotation> A findAnnotation(Class<?> target, Class<A> annoClass) {
        A a = target.getAnnotation(annoClass);
        for(Annotation anno : target.getAnnotations()) {
            Class<? extends Annotation> annoType = anno.annotationType();
            if(!annoType.getPackageName().equals("java.lang.annotation")) { // 只需找自定义的注解
                A found = findAnnotation(annoType, annoClass);
                if(found != null) {
                    if(a != null)
                        throw new BeanDefinitionException("Duplicate @" + annoClass.getSimpleName() +
                                " found on class " + target.getSimpleName());
                    a = found;
                }
            }
        }
        return a;
    }

    // @Component 标注的 Bean 类
    public static String getBeanName(Class<?> clazz) {
        String name = "";
        Component component = clazz.getAnnotation(Component.class);
        if(component != null)
            name = component.value();
        else {
            // 未找到 @Component，继续在其他注解中查找
            for(Annotation anno : clazz.getAnnotations()) {
                if(findAnnotation(anno.annotationType(), Component.class) != null) {
                    try {
                        name = (String) anno.annotationType().getMethod("value").invoke(anno);
                    }catch (ReflectiveOperationException e) {
                        throw new BeanDefinitionException("Cannot get annotation value.", e);
                    }
                }
            }
        }
        if(name.isEmpty()) {
            // default name: "HelloWorld" => "helloWorld"
            name = clazz.getSimpleName();
            name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        }
        return name;
    }

    // 工厂方法 @Bean 标注的 Bean
    public static String getBeanName(Method method) {
        Bean bean = method.getAnnotation(Bean.class);
        String name = bean.value();
        if(name.isEmpty())
            name = method.getName();
        return name;
    }

    // 找 @PostConstruct 或 @PreDestroy 方法
    public static Method findAnnotationMethod(Class<?> clazz, Class<? extends Annotation> annoClass) {
        List<Method> ms = Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(annoClass))
                .peek(m -> {
                    if(m.getParameterCount() != 0) {
                        throw new BeanDefinitionException(String.format(
                                "Method '%s' with @%s must not have argument: %s",
                                m.getName(), annoClass.getSimpleName(), clazz.getName()));
                    }
                }).toList();
        if(ms.isEmpty())
            return null;
        if(ms.size() == 1)
            return ms.getFirst();
        throw new BeanDefinitionException(String.format("Multiple methods with @%s found in class: %s",
                annoClass.getSimpleName(), clazz.getName()));
    }

    public static Method getNamedMethod(Class<?> clazz, String name) {
        try {
            return clazz.getDeclaredMethod(name);
        }catch (NoSuchMethodException e) {
            throw new BeanDefinitionException(String.format("Method '%s' not found in class: %s",
                    name, clazz.getName()));
        }
    }
}
