package com.learn.scan.proxy;

import com.learn.summer.annotation.Component;
import com.learn.summer.annotation.Value;

@Component
public class OriginBean {
    @Value("${app.title}")
    public String name;

    public String version;

    @Value("${app.version}")
    public void setVersion(String version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }
}
