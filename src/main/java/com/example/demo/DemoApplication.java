package com.example.demo;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
        System.out.println("ok");

	}

	/** Запуск демонстрации жизненного цикла потоков при старте приложения. */
	@Bean
	public CommandLineRunner threadDemoRunner(ThreadLifecycleService threadLifecycleService) {
		return args -> threadLifecycleService.runLifecycleDemo();
	}

}
