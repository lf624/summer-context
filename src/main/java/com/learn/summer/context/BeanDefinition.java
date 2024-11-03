package com.learn.summer.context;

import com.learn.summer.exception.BeanCreationException;
import jakarta.annotation.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public class BeanDefinition implements Comparable<BeanDefinition>{
    // 全局唯一 Name
    private final String name;
    // 声明类型
    private final Class<?> beanClass;
    // 实例
    private Object instance = null;
    // 构造方法
    private final Constructor<?> constructor;
    // 工厂方法名称
    private final String factoryName;
    // 工厂方法
    private final Method factoryMethod;
    // 顺序
    private final int order;
    // 是否标识@Primary
    private final boolean primary;

    private String initMethodName;
    private String destroyMethodName;

    private Method initMethod;
    private Method destroyMethod;
    // 通过构造器创建 @Component标识
    public BeanDefinition(String name, Class<?> beanClass, Constructor<?> constructor, int order, boolean primary,
                          String initMethodName, String destroyMethodName, Method initMethod, Method destroyMethod) {
        this.name = name;
        this.beanClass = beanClass;
        this.constructor = constructor;
        this.factoryName = null;
        this.factoryMethod = null;
        this.order = order;
        this.primary = primary;
        constructor.setAccessible(true);
        setInitAndDestroyMethod(initMethodName, destroyMethodName, initMethod, destroyMethod);
    }
    // 工厂方法创建 @Bean标识
    public BeanDefinition(String name, Class<?> beanClass, String factoryName, Method factoryMethod,
                          int order, boolean primary, String initMethodName, String destroyMethodName,
                          Method initMethod, Method destroyMethod) {
        this.name = name;
        this.beanClass = beanClass;
        this.constructor = null;
        this.factoryName = factoryName;
        this.factoryMethod = factoryMethod;
        this.order = order;
        this.primary = primary;
        factoryMethod.setAccessible(true);
        setInitAndDestroyMethod(initMethodName, destroyMethodName, initMethod, destroyMethod);
    }

    public void setInitAndDestroyMethod(String initMethodName, String destroyMethodName,
                                        Method initMethod, Method destroyMethod) {
        this.initMethodName = initMethodName;
        this.destroyMethodName = destroyMethodName;
        if (initMethod != null)
            initMethod.setAccessible(true);
        if (destroyMethod != null) {
            destroyMethod.setAccessible(true);
        }
        this.initMethod = initMethod;
        this.destroyMethod = destroyMethod;
    }

    public String getName() {
        return name;
    }

    public Class<?> getBeanClass() {
        return beanClass;
    }

    @Nullable
    public Constructor<?> getConstructor() {
        return constructor;
    }

    @Nullable
    public String getFactoryName() {
        return factoryName;
    }

    @Nullable
    public Method getFactoryMethod() {
        return factoryMethod;
    }

    @Nullable
    public String getInitMethodName() {
        return initMethodName;
    }

    @Nullable
    public String getDestroyMethodName() {
        return destroyMethodName;
    }

    @Nullable
    public Method getInitMethod() {
        return initMethod;
    }

    @Nullable
    public Method getDestroyMethod() {
        return destroyMethod;
    }

    @Nullable
    public Object getInstance() {
        return instance;
    }
    public Object getRequiredInstance() {
        if(this.instance == null)
            throw new BeanCreationException(String.format("Instance of bean with name '%s' and type '%s' is not instantiated during current stage.",
                    this.getName(), this.getBeanClass().getName()));
        return this.instance;
    }
    public void setInstance(Object instance) {
        Objects.requireNonNull(instance, "Bean instance is null.");
        if(!this.beanClass.isAssignableFrom(instance.getClass())) {
            throw new BeanCreationException(
                    String.format("Instance '%s' of Bean '%s' is not the expected type: %s", instance,
                            instance.getClass().getName(), this.beanClass.getName()));
        }
        this.instance = instance;
    }
    public boolean isPrimary() { return this.primary;}

    @Override
    public String toString() {
        return "BeanDefinition [name=" + name + ", beanClass=" + beanClass.getName() +
                ", factory=" + getCreateDetail() + ", init-method=" +
                (initMethod == null ? "null" : initMethod.getName()) +
                ", destroy-method=" + (destroyMethod == null ? "null" : destroyMethod.getName()) +
                ", primary=" + primary + ", instance=" + instance + "]";
    }

    String getCreateDetail() {
        if(this.factoryMethod != null) {
            String params = Arrays.stream(this.factoryMethod.getParameterTypes())
                    .map(Class::getName)
                    .collect(Collectors.joining(", "));
            return this.factoryMethod.getDeclaringClass().getSimpleName() + "." +
                    this.factoryMethod.getName() + "(" + params + ")";
        }
        return null;
    }

    @Override
    public int compareTo(BeanDefinition o) {
        int cmp = Integer.compare(this.order, o.order);
        if(cmp != 0)
            return cmp;
        return this.name.compareTo(o.name);
    }
}
