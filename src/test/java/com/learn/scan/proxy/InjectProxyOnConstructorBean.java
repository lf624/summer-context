package com.learn.scan.proxy;

import com.learn.summer.annotation.Autowired;
import com.learn.summer.annotation.Component;

@Component
public class InjectProxyOnConstructorBean {
    public OriginBean injected;

    public InjectProxyOnConstructorBean(@Autowired OriginBean injected) {
        this.injected = injected;
    }
}
