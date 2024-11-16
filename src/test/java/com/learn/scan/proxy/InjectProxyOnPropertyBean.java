package com.learn.scan.proxy;

import com.learn.summer.annotation.Autowired;
import com.learn.summer.annotation.Component;

@Component
public class InjectProxyOnPropertyBean {

    @Autowired
    public OriginBean injected;
}
