package com.learn.scan.primary;

import com.learn.summer.annotation.Bean;
import com.learn.summer.annotation.Configuration;
import com.learn.summer.annotation.Primary;

@Configuration
public class PrimaryConfiguration {
    @Primary
    @Bean
    DogBean husky() {
        return new DogBean("husky");
    }
    @Bean
    DogBean teddy() {
        return new DogBean("teddy");
    }
}
