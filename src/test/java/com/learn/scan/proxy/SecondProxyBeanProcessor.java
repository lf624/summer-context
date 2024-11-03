package com.learn.scan.proxy;

import com.learn.summer.annotation.Component;
import com.learn.summer.annotation.Order;
import com.learn.summer.context.BeanPostProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@Order(200)
@Component
public class SecondProxyBeanProcessor implements BeanPostProcessor {
    final Logger logger = LoggerFactory.getLogger(getClass());

    Map<String, Object> originBeans = new HashMap<>();

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if(OriginBean.class.isAssignableFrom(bean.getClass())) {
            logger.debug("create first proxy for bean `{}`:{}", beanName, bean);
            SecondProxyBean proxy = new SecondProxyBean((OriginBean) bean);
            originBeans.put(beanName, bean);
            return proxy;
        }
        return bean;
    }

    @Override
    public Object postProcessOnSetProperty(Object bean, String beanName) {
        Object origin = originBeans.get(beanName);
        if(origin != null) {
            logger.debug("auto set property for {} from first proxy {} to origin bean: {}",
                    beanName, bean, origin);
            return origin;
        }
        return bean;
    }
}
