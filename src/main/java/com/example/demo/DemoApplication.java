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

	/** Запуск демонстрации пула потоков при старте приложения. */
	@Bean
	public CommandLineRunner threadDemoRunner(ThreadPoolDemoService threadPoolDemoService) {
		return args -> threadPoolDemoService.runThreadPoolDemo();
	}

}
