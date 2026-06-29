package com.solo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SoloApplication {

    public static void main(String[] args) {
        SpringApplication.run(SoloApplication.class, args);
    }

}
