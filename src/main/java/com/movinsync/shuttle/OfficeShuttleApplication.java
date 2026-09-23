package com.movinsync.shuttle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableCaching
@EnableAsync
public class OfficeShuttleApplication {

    public static void main(String[] args) {
        SpringApplication.run(OfficeShuttleApplication.class, args);
    }
}
