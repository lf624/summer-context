package com.learn.summer.context;

public interface BeanPostProcessor {
    // new Bean() 之后执行
    default Object postProcessBeforeInitialization(Object bean, String beanName) {
        return bean;
    }

    // Bean.init() 之后执行
    default Object postProcessAfterInitialization(Object bean, String beanName) {
        return bean;
    }

    // Bean.setXyz() 之前执行
    default Object postProcessOnSetProperty(Object bean, String beanName) {
        return bean;
    }
}
