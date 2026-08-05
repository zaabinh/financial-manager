package com.example.financemanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FinanceManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanceManagerApplication.class, args);
    }
}
