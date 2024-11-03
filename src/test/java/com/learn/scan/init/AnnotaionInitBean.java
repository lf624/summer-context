package com.learn.scan.init;

import com.learn.summer.annotation.Component;
import com.learn.summer.annotation.Value;
import jakarta.annotation.PostConstruct;

@Component
public class AnnotaionInitBean {
    @Value("${app.title}")
    String appTitle;

    @Value("${app.version}")
    String appVersion;

    public String appName;

    @PostConstruct
    void init() {
        this.appName = appTitle + " / " + appVersion;
    }
}
