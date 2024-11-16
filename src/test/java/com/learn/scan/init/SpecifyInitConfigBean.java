package com.learn.scan.init;

import com.learn.summer.annotation.Bean;
import com.learn.summer.annotation.Configuration;
import com.learn.summer.annotation.Value;

@Configuration
public class SpecifyInitConfigBean {

    @Bean(initMethod = "init")
    SpecifyInitBean createSpecifyInitBean(@Value("${app.title}") String title,
                                      @Value("app.version") String version) {
        return new SpecifyInitBean(title, version);
    }
}
