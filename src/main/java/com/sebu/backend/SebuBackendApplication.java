package com.sebu.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class SebuBackendApplication {

	public static void main(String[] args) {
		ConfigurableApplicationContext context = SpringApplication.run(SebuBackendApplication.class, args);
		if (context.getEnvironment().matchesProfiles(
			"crawler | promotion | research-field-extraction"
				+ " | research-field-manual-split | research-field-promotion"
		)) {
			context.close();
		}
	}

}
