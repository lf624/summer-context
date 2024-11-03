package com.learn.scan.destroy;

import com.learn.summer.annotation.Bean;
import com.learn.summer.annotation.Configuration;
import com.learn.summer.annotation.Value;

@Configuration
public class SpecifyDestroyConfiguration {
    @Bean(destroyMethod = "destroy")
    SpecifyDestroyBean createSpecifyDestroyBean(@Value("${app.title}") String appTitle) {
        return new SpecifyDestroyBean(appTitle);
    }
}
