package com.learn.summer.exception;

public class NoSuchBeanDefinitionException extends BeanDefinitionException {
    public NoSuchBeanDefinitionException() {}
    public NoSuchBeanDefinitionException(String message) {
        super(message);
    }
}
