package com.financehub;

import com.financehub.application.imports.ImportProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan("com.financehub.config")
@EnableConfigurationProperties(ImportProperties.class)
public class FinanceHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanceHubApplication.class, args);
    }
}
