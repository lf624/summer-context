package com.learn.scan.init;

import com.learn.summer.annotation.Value;

public class SpecifyInitBean {
    String appTitle;

    String appVersion;

    public String appName;

    SpecifyInitBean(String appTitle, String appVersion) {
        this.appTitle = appTitle;
        this.appVersion = appVersion;
    }

    void init() {
        this.appName = appTitle + " / " + appVersion;
    }
}
