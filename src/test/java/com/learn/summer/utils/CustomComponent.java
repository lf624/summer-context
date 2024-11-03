package com.learn.summer.utils;

import com.learn.summer.annotation.Component;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface CustomComponent {

    String value() default "";

}
