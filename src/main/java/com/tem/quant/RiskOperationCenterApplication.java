package com.tem.quant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RiskOperationCenterApplication {

	public static void main(String[] args) {
		SpringApplication.run(RiskOperationCenterApplication.class, args);
	}

}
