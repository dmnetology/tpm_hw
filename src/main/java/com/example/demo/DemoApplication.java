package com.example.demo;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
        System.out.println("ok");

	}

	/** Запуск демонстрации Callable и Future при старте приложения. */
	@Bean
	public CommandLineRunner threadDemoRunner(CallableFutureDemoService callableFutureDemoService) {
		return args -> callableFutureDemoService.runCallableFutureDemo();
	}

}
