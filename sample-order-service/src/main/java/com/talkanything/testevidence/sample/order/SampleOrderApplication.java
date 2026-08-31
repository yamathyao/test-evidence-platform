package com.talkanything.testevidence.sample.order;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableDubbo
public class SampleOrderApplication {
    public static void main(String[] args) {
        SpringApplication.run(SampleOrderApplication.class, args);
    }
}
