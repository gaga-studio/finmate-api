package com.gagastudio.finmate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.gagastudio.finmate.config.FinmateProperties;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
@EnableConfigurationProperties(FinmateProperties.class)
public class FinmateApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(FinmateApiApplication.class, args);
	}

}
