package com.talkanything.testevidence.sample.fulfillment;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableDubbo
public class SampleFulfillmentApplication {
    public static void main(String[] args) {
        SpringApplication.run(SampleFulfillmentApplication.class, args);
    }
}
