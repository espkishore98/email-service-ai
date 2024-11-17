package com.documentAi.documentAi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DocumentAiApplication {

	public static void main(String[] args) {
		SpringApplication.run(DocumentAiApplication.class, args);
	}

}
