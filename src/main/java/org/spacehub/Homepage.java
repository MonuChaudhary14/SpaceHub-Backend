package org.spacehub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class Homepage {

    public static void main(String[] args) {

        SpringApplication.run(Homepage.class, args);
    }

}
