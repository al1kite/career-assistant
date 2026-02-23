package com.career.assistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CareerAssistantApplication {
    public static void main(String[] args) {
        SpringApplication.run(CareerAssistantApplication.class, args);
    }
}
