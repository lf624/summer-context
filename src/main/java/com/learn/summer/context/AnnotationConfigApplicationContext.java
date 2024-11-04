package com.learn.summer.context;

import com.learn.summer.annotation.*;
import com.learn.summer.exception.*;
import com.learn.summer.io.PropertyResolver;
import com.learn.summer.io.ResourceResolver;
import com.learn.summer.utils.ClassUtils;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

public class AnnotationConfigApplicationContext implements ConfigurableApplicationContext{
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final PropertyResolver propertyResolver;
    protected final Map<String, BeanDefinition> beans;

    // 用 Set 表示正在创建的 Bean，以解决循环依赖问题
    private Set<String> creatingBeanNames;
    private List<BeanPostProcessor> beanPostProcessors = new ArrayList<>();

    public AnnotationConfigApplicationContext(Class<?> configClass, PropertyResolver resolver) {
        ApplicationContextUtils.setApplicationContext(this);

        this.propertyResolver = resolver;
        // 扫描包中 .class 文件，并获得完整类名
        Set<String> beanClassNames = scanForClassNames(configClass);
        // 创建 Bean 定义
        this.beans = createBeanDefinitions(beanClassNames);

        this.creatingBeanNames = new HashSet<>();
        // 先创建 @Configuration 类型 Bean
        this.beans.values().stream()
                .filter(this::isConfigurationDefinition).sorted()
                .map(def -> {
                    createBeanAsEarlySingleton(def);
                    return def.getName();
                }).toList();

        // 创建BeanPostProcessor类型的Bean
        List<BeanPostProcessor> processors = this.beans.values().stream()
                .filter(this::isBeanPostProcessorDefinition)
                .sorted()
                .map(def -> (BeanPostProcessor)createBeanAsEarlySingleton(def))
                .toList();
        this.beanPostProcessors.addAll(processors);

        // 创建其他普通 Bean
        createNormalBeans();
        // 字段和 Setter 方法注入
        this.beans.values().forEach(this::injectBean);
        // 调用 init 方法
        this.beans.values().forEach(this::initBean);
    }

    void createNormalBeans() {
        List<BeanDefinition> ordinaryDefs = this.beans.values().stream()
                .filter(def -> def.getInstance() == null).sorted()
                .toList();
        ordinaryDefs.forEach(def -> {
            if(def.getInstance() == null)
                createBeanAsEarlySingleton(def);
        });
    }

    public Object createBeanAsEarlySingleton(BeanDefinition def) {
        logger.atDebug().log("try create bean '{}' as early singleton", def.getName());
        if(!this.creatingBeanNames.add(def.getName())) {
            // 重复创建 Bean 导致的循环依赖
            throw new UnsatisfiedDependencyException(String.format(
                    "Circular dependency detected when create bean '%s'", def.getName()));
        }

        // 工厂方法或构造方法
        Executable createFn = def.getFactoryName() == null
                ? def.getConstructor() : def.getFactoryMethod();

        Parameter[] parameters = createFn.getParameters();
        logger.atDebug().log("parameters: {}", Arrays.stream(parameters).toList());
        Object[] args = new Object[parameters.length];
        for(int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            // 这里直接从参数的注解中获得相应的注解，简化了参考（是否合理）
            Value value = param.getAnnotation(Value.class);
            Autowired autowired = param.getAnnotation(Autowired.class);
            // 参数合法性检查
            // @Configuration类型的Bean是工厂，不允许使用@Autowired创建
            boolean isConfiguration = isConfigurationDefinition(def);
            if(isConfiguration && autowired != null)
                throw new BeanCreationException(String.format("Cannot specify @Autowired when create @Configuration bean '%s': %s.",
                        def.getName(), def.getBeanClass().getName()));
            // 参数只需 @Value 和 @AutoWired 两者之一
            if (value != null && autowired != null) {
                throw new BeanCreationException(
                        String.format("Cannot specify both @Autowired and @Value when create bean '%s': %s.",
                                def.getName(), def.getBeanClass().getName()));
            }
            if (value == null && autowired == null) {
                throw new BeanCreationException(
                        String.format("Must specify @Autowired or @Value when create bean '%s': %s.",
                                def.getName(), def.getBeanClass().getName()));
            }
            // 获取参数
            Class<?> type = param.getType();
            if(value != null) {
                String prop = value.value();
                args[i] = propertyResolver.getRequiredProperty(prop, type);
            } else {
                String name = autowired.name();
                boolean required = autowired.value();
                // 若指定了名字，还需要检查依赖的 Bean 是否与参数 type 匹配
                BeanDefinition dependsOnDef = name.isEmpty() ? findBeanDefinition(type) :
                        findBeanDefinition(name, type);
                // required 为 true 时，依赖的Bean必须存在
                if(required && dependsOnDef == null)
                    throw new BeanCreationException("Missing autowired bean with type '%s' when create bean '%s':%s."
                            .formatted(type.getName(), def.getName(), def.getBeanClass().getName()));
                if(dependsOnDef != null) {
                    Object autowiredInstance = dependsOnDef.getInstance();
                    if(autowiredInstance == null)
                        autowiredInstance = createBeanAsEarlySingleton(dependsOnDef);
                    args[i] = autowiredInstance;
                } else {
                    args[i] = null;
                }
            }
        }
        // 创建 Bean 实例
        Object instance = null;
        if(def.getFactoryMethod() == null) {
            try {
                instance = def.getConstructor().newInstance(args);
            } catch (Exception e) {
                throw new BeanCreationException(String.format(
                        "Exception when create bean '%s': %s",
                        def.getName(), def.getBeanClass().getName()), e);
            }
        } else {
            Object configInstance = getBean(def.getFactoryName());
            try {
                instance = def.getFactoryMethod().invoke(configInstance, args);
            }catch (Exception e) {
                throw new BeanDefinitionException(String.format("Exception when create bean '%s': %s",
                        def.getName(), def.getBeanClass().getName()), e);
            }
        }
        def.setInstance(instance);

        // 调用BeanPostProcessor处理Bean
        for(BeanPostProcessor processor : beanPostProcessors) {
            Object processed = processor.postProcessBeforeInitialization(instance, def.getName());
            if(instance != processed) {
                logger.atDebug().log("Bean '{}' was replaced by post processor {}.", def.getName(), processor.getClass().getName());
                def.setInstance(processed);
            }
        }
        return def.getInstance();
    }

    void injectBean(BeanDefinition def) {
        Object proxiedBean = getProxiedInstance(def);
        try {
            // 需要递归调用，包装一层
            injectProperties(def, def.getBeanClass(), proxiedBean);
        }catch (ReflectiveOperationException e) {
            throw new BeanCreationException(e);
        }
    }

    void initBean(BeanDefinition def) {
        // 调用原始实例的方法
        Object beanInstance = getProxiedInstance(def);
        callMethod(beanInstance, def.getInitMethod(), def.getInitMethodName());

        // 调用BeanPostProcessor.postProcessAfterInitialization()
        beanPostProcessors.forEach(beanPostProcessor -> {
            Object processedInstance = beanPostProcessor.postProcessAfterInitialization(
                    def.getInstance(), def.getName());
            if(processedInstance != def.getInstance()) {
                logger.atDebug().log("BeanPostProcessor {} return different bean from {} to {}.",
                        beanPostProcessor.getClass().getSimpleName(),
                        def.getInstance().getClass().getName(), processedInstance.getClass().getName());
                def.setInstance(processedInstance);
            }
        });
    }

    void injectProperties(BeanDefinition def, Class<?> clazz, Object bean)
            throws ReflectiveOperationException{
        // 字段注入
        for(Field f : clazz.getDeclaredFields()) {
            tryInjectProperties(def, clazz, bean, f);
        }
        for(Method m : clazz.getDeclaredMethods()) {
            tryInjectProperties(def, clazz, bean, m);
        }
        Class<?> superClass = clazz.getSuperclass();
        if(superClass != null)
            injectProperties(def, superClass, bean);
    }

    void tryInjectProperties(BeanDefinition def, Class<?> clazz, Object bean, AccessibleObject acc)
            throws ReflectiveOperationException {
        Value value = acc.getAnnotation(Value.class);
        Autowired autowired = acc.getAnnotation(Autowired.class);
        if(value == null && autowired == null) return;
        if(value != null && autowired != null) {
            throw new BeanDefinitionException("Cannot specify both @Autowired and @Value on Field or Method " +
                    "%s for bean '%s':%s".formatted(acc,
                            def.getName(), def.getBeanClass().getName()));
        }

        Field field = null;
        Method method = null;
        if(acc instanceof Field f) {
            checkFieldOrMethod(f);
            f.setAccessible(true);
            field = f;
        }
        if(acc instanceof Method m) {
            checkFieldOrMethod(m);
            // 确定是 setter 方法
            if(m.getParameterCount() != 1)
                throw new BeanDefinitionException(String.format("Cannot inject a non-setter method " +
                        "%s for bean '%s':%s", m.getName(), def.getName(), def.getBeanClass().getName()));
            m.setAccessible(true);
            method = m;
        }

        String accessibleName = field != null ? field.getName() : method.getName();
        Class<?> accessibleType = field != null ? field.getType() : method.getParameterTypes()[0];

        // @Value 注入
        if(value != null) {
            Object propValue = this.propertyResolver.getProperty(value.value(), accessibleType);
            if(field != null) {
                logger.atDebug().log("Field injection: {}.{} = {}",
                        def.getBeanClass().getSimpleName(), accessibleName, propValue);
                field.set(bean, propValue);
            }
            if(method != null) {
                logger.atDebug().log("Method injection: {}.{} = {}",
                        def.getBeanClass().getName(), accessibleName, propValue);
                method.invoke(bean, propValue);
            }
        }
        // @Autowired 注入
        if(autowired != null) {
            String name = autowired.name();
            boolean required = autowired.value();
            Object depends = name.isEmpty() ? findBean(accessibleType) : findBean(name, accessibleType);
            if(required && depends == null)
                throw new UnsatisfiedDependencyException(String.format("Dependency bean not found when " +
                        "inject %s.%s for bean '%s':%s", clazz.getSimpleName(), accessibleName,
                        def.getName(), def.getBeanClass().getSimpleName()));
            if(depends != null) {
                if(field != null) {
                    logger.atDebug().log("Field injection: {}.{} = {}",
                            def.getBeanClass().getSimpleName(), accessibleName, depends);
                    field.set(bean, depends);
                }
                if(method != null) {
                    logger.atDebug().log("Method injection: {}.{} = {}",
                            def.getBeanClass().getName(), accessibleName, depends);
                    method.invoke(bean, depends);
                }
            }
        }
    }

    void checkFieldOrMethod(Member m) {
        int mod = m.getModifiers();
        if(Modifier.isStatic(mod))
            throw new BeanDefinitionException("Cannot inject static field: " + m);
        if(Modifier.isFinal(mod)) {
            if(m instanceof Field field)
                throw new BeanDefinitionException("Cannot inject final field: " + field);
            if(m instanceof Method method)
                logger.warn("Inject final method should be careful because it is not called on target " +
                        "bean when bean is proxied and may cause NullPointerException.");
        }
    }

    // Component Scan 操作
    protected Set<String> scanForClassNames(Class<?> configClass) {
        // 获取注解中的 package，若没有则取配置类的 package
        ComponentScan scan = ClassUtils.findAnnotation(configClass, ComponentScan.class);
        String[] scanPackages = scan == null || scan.value().length == 0 ?
                new String[] {configClass.getPackage().getName()} : scan.value();
        logger.atInfo().log("component scan in packages: {}", Arrays.toString(scanPackages));

        Set<String> classNameSet = new HashSet<>();
        for(String pkg : scanPackages) {
            logger.atDebug().log("scan package: {}", pkg);
            var rr = new ResourceResolver(pkg);
            List<String> classList = rr.scan(res -> {
                String name = res.name();
                if(name.endsWith(".class"))
                    return name.substring(0, name.length() - 6)
                            .replace("/", ".")
                            .replace("\\", ".");
                return null;
            });
            classNameSet.addAll(classList);
        }
        // 查找 @Import 注解
        Import importConfig = configClass.getAnnotation(Import.class);
        if(importConfig != null) {
            for(Class<?> importClass : importConfig.value()) {
                String importClassName = importClass.getName();
                if(classNameSet.contains(importClassName))
                    logger.warn("ignore import: " + importClassName + " for it is already been scanned.");
                else {
                    logger.debug("class found by import: {}", importClassName);
                    classNameSet.add(importClassName);
                }
            }
        }
        return classNameSet;
    }

    // 根据扫描的 class 名称获得 BeanDefinition
    Map<String, BeanDefinition> createBeanDefinitions(Set<String> classNameSet) {
        Map<String, BeanDefinition> defs = new HashMap<>();
        for(String className : classNameSet) {
            Class<?> clazz = null;
            try {
                clazz = Class.forName(className);
            }catch (ClassNotFoundException e) {
                throw new BeanCreationException(e);
            }
            if (clazz.isAnnotation() || clazz.isEnum() || clazz.isInterface() || clazz.isRecord()) {
                continue;
            }
            Component component = ClassUtils.findAnnotation(clazz, Component.class);
            if(component != null) {
                logger.atDebug().log("found component: {}", clazz.getName());
                // 不能是 abstract 或 private 修饰的类
                int mod = clazz.getModifiers();
                if(Modifier.isAbstract(mod))
                    throw new BeanDefinitionException("@Component class " + clazz.getName() +
                            " must not be abstract.");
                if(Modifier.isPrivate(mod))
                    throw new BeanDefinitionException("@Component class " + clazz.getName() +
                            " must not be private.");

                String beanName = ClassUtils.getBeanName(clazz);
                var def = new BeanDefinition(beanName, clazz, getSuitableConstructor(clazz), getOrder(clazz),
                        clazz.isAnnotationPresent(Primary.class), null, null,
                        ClassUtils.findAnnotationMethod(clazz, PostConstruct.class),
                        ClassUtils.findAnnotationMethod(clazz, PreDestroy.class));
                addBeanDefinitions(defs, def);
                logger.atDebug().log("define bean: {}", def);

                Configuration configuration = ClassUtils.findAnnotation(clazz, Configuration.class);
                if(configuration != null)
                    scanFactoryMethods(beanName, clazz, defs);
            }
        }
        return defs;
    }

    Constructor<?> getSuitableConstructor(Class<?> clazz) {
        Constructor<?>[] cons = clazz.getConstructors();
        if(cons.length == 0) {
            cons = clazz.getDeclaredConstructors();
            if(cons.length != 1)
                throw new BeanDefinitionException("More than one constructor found in class " +
                        clazz.getName() + ".");
        }
        if(cons.length != 1)
            throw new BeanDefinitionException("More than one public constructor found in class " +
                    clazz.getName() + ".");
        return cons[0];
    }

    void scanFactoryMethods(String factoryBeanName, Class<?> clazz, Map<String, BeanDefinition> defs) {
        for(Method method : clazz.getDeclaredMethods()) {
            Bean bean = method.getAnnotation(Bean.class);
            if(bean != null) {
                Class<?> beanClass = method.getReturnType();
                var def = new BeanDefinition(ClassUtils.getBeanName(method), beanClass, factoryBeanName,
                        method, getOrder(method), method.isAnnotationPresent(Primary.class),
                        bean.initMethod().isEmpty() ? null : bean.initMethod(),
                        bean.destroyMethod().isEmpty() ? null : bean.destroyMethod(),
                        null, null);
                addBeanDefinitions(defs, def);
                logger.atDebug().log("define bean: {}", def);
            }
        }
    }

    void addBeanDefinitions(Map<String, BeanDefinition> defs, BeanDefinition def) {
        if(defs.put(def.getName(), def) != null) {
            throw new BeanDefinitionException("Duplicate bean name: " + def.getName());
        }
    }

    int getOrder(Class<?> clazz) {
        Order order = clazz.getAnnotation(Order.class);
        return order == null ? Integer.MAX_VALUE : order.value();
    }

    int getOrder(Method method) {
        Order order = method.getAnnotation(Order.class);
        return order == null ? Integer.MAX_VALUE : order.value();
    }

    // 根据Type查找若干个BeanDefinition，返回0个或多个
    public List<BeanDefinition> findBeanDefinitions(Class<?> type) {
        return this.beans.values().stream()
                .filter(def -> type.isAssignableFrom(def.getBeanClass()))
                .sorted()
                .collect(Collectors.toList());
    }
    // 根据 type 查找某个 BeanDefinition，如果不存在返回null，如果存在多个返回@Primary标注的一个
    @Nullable
    public BeanDefinition findBeanDefinition(Class<?> type) {
        List<BeanDefinition> defs = findBeanDefinitions(type);
        if(defs.isEmpty())
            return null;
        else if(defs.size() == 1)
            return defs.getFirst();
        // 多于一个，查找 @Primary
        List<BeanDefinition> primaryDefs = defs.stream().filter(BeanDefinition::isPrimary).toList();
        if(primaryDefs.size() == 1)
            return primaryDefs.getFirst();
        if(primaryDefs.isEmpty()) {// 不存在 @Primary
            throw new NoUniqueBeanDefinitionException(
                    String.format("Multiple bean with type '%s' found, but no @Primary specified.",
                            type.getName()));
        }else { // @Primary 不唯一
            throw new NoUniqueBeanDefinitionException(String.format(
                    "Multiple bean with type '%s' found, and multiple @Primary specified.", type.getName()));
        }
    }
    @Nullable
    public BeanDefinition findBeanDefinition(String name) {
        return this.beans.get(name);
    }
    @Nullable
    public BeanDefinition findBeanDefinition(String name, Class<?> requiredType) {
        BeanDefinition res = findBeanDefinition(name);
        if(res == null) return null;
        // res 是否可以赋值给 requiredType 引用
        if(!requiredType.isAssignableFrom(res.getBeanClass()))
            throw new BeanNotOfRequiredTypeException("Autowire required type '%s' but bean '%s' has actual type '%s'."
                    .formatted(requiredType.getName(), name, res.getBeanClass().getName()));
        return res;
    }

    boolean isConfigurationDefinition(BeanDefinition def) {
        //return def.getBeanClass().getAnnotation(Configuration.class) != null;
        return ClassUtils.findAnnotation(def.getBeanClass(), Configuration.class) != null;
    }

    boolean isBeanPostProcessorDefinition(BeanDefinition def) {
        return BeanPostProcessor.class.isAssignableFrom(def.getBeanClass());
    }

    @Override
    public boolean containsBean(String name) {
        return this.beans.containsKey(name);
    }

    @SuppressWarnings("unchecked")
    public <T> T getBean(String name) {
        BeanDefinition def = this.beans.get(name);
        if(def == null)
            throw new NoSuchBeanDefinitionException(String.format("No bean defined with name '%s'", name));
        return (T) def.getInstance();
    }

    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> type) {
        BeanDefinition def = findBeanDefinition(type);
        if(def == null)
            throw new NoSuchBeanDefinitionException(String.format("No bean defined with type %s.",
                    type.getName()));
        return (T) def.getRequiredInstance();
    }
    public <T> T getBean(String name, Class<T> requiredType) {
        T t = findBean(name, requiredType);
        if(t == null)
            throw new NoSuchBeanDefinitionException(String.format("No bean defined with name '%s' and type '%s'.",
                    name, requiredType));
        return t;
    }
    @SuppressWarnings("unchecked")
    public <T> List<T> getBeans(Class<T> requiredType) {
        List<BeanDefinition> defs = findBeanDefinitions(requiredType);
        if(defs.isEmpty())
            return null;
        List<T> list = new ArrayList<>(defs.size());
        for(var def : defs)
            list.add((T)def.getRequiredInstance());
        return list;
    }

    // findxxx 与 getxxx 类似，但不存在会返回 null
    @SuppressWarnings("unchecked")
    protected <T> T findBean(Class<T> requiredType) {
        BeanDefinition def = findBeanDefinition(requiredType);
        if(def == null)
            return null;
        return (T) def.getRequiredInstance();
    }
    @SuppressWarnings("unchecked")
    protected <T> T findBean(String name, Class<T> requiredType) {
        BeanDefinition def = findBeanDefinition(name, requiredType);
        if(def == null)
            return null;
        return (T) def.getRequiredInstance();
    }
    @SuppressWarnings("unchecked")
    protected <T> List<T> findBeans(Class<T> requiredType) {
        return findBeanDefinitions(requiredType).stream()
                .map(def -> (T)def.getRequiredInstance())
                .toList();
    }

    // 调用 init/destroy 方法
    private void callMethod(Object bean, Method method, String namedMethod) {
        if(method != null) {
            try {
                method.invoke(bean);
            }catch (ReflectiveOperationException e) {
                throw new BeanCreationException(e);
            }
        } else if(namedMethod != null) {
            // 查找 ’initMethod/destroyMethod="xyz"‘，注意是在实际类型中查找
            Method named = ClassUtils.getNamedMethod(bean.getClass(), namedMethod);
            named.setAccessible(true);
            try {
                named.invoke(bean);
            }catch (ReflectiveOperationException e) {
                throw new BeanCreationException(e);
            }
        }
    }

    Object getProxiedInstance(BeanDefinition def) {
        Object beanInstance = def.getInstance();
        List<BeanPostProcessor> reversedPostProcessors = new ArrayList<>(this.beanPostProcessors);
        Collections.reverse(reversedPostProcessors);
        for(BeanPostProcessor processor : reversedPostProcessors) {
            Object getProxied = processor.postProcessOnSetProperty(beanInstance, def.getName());
            if(getProxied != beanInstance)
                beanInstance = getProxied;
        }
        return beanInstance;
    }

    @Override
    public void close() {
        logger.info("Closing {}...", this.getClass().getName());
        this.beans.values().forEach(def -> {
            Object beanInstance = getProxiedInstance(def);
            callMethod(beanInstance, def.getDestroyMethod(), def.getDestroyMethodName());
        });
        this.beans.clear();
        logger.info("{} closed.", this.getClass().getName());
        ApplicationContextUtils.setApplicationContext(null);
    }
}
