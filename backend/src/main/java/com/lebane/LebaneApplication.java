package com.lebane;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LebaneApplication {

    public static void main(String[] args) {
        SpringApplication.run(LebaneApplication.class, args);
    }
}
