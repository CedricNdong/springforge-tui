package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Reference Spring Boot application used by integration tests.
 * SpringForge generates DTO, Mapper, Repository, Service, and Controller
 * layers for the entities in {@code com.example.model}.
 */
@SpringBootApplication
public class ReferenceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReferenceApplication.class, args);
    }
}
