package com.ringgrank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class RinggrankApplication {

	public static void main(String[] args) {
		SpringApplication.run(RinggrankApplication.class, args);
	}

}
