package com.lms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.modulith.Modulithic;

/**
 * Modular monolith. Each direct sub-package (tenancy, organization, ...) is a Spring Modulith
 * application module; {@code shared} is usable by every module. Module boundaries are verified
 * by {@code ModularityTest}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@Modulithic(sharedModules = "shared")
public class LmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(LmsApplication.class, args);
    }
}
