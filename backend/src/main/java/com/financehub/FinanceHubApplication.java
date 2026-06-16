package com.financehub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.financehub.config")
public class FinanceHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanceHubApplication.class, args);
    }
}
