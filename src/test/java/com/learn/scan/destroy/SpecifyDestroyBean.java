package com.learn.scan.destroy;

public class SpecifyDestroyBean {
    public String appTitle;

    public SpecifyDestroyBean(String apptitle) {
        this.appTitle = apptitle;
    }

    void destroy() {
        this.appTitle = null;
    }
}
