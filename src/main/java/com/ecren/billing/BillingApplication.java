package com.ecren.billing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling activates @Scheduled methods app-wide.
// Without it, BillingCycleService's @Scheduled annotation is silently ignored.
@SpringBootApplication
@EnableScheduling
public class BillingApplication {

	public static void main(String[] args) {
		SpringApplication.run(BillingApplication.class, args);
	}

}
