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

	/** Запуск демонстрации буфера на ReentrantLock и Condition при старте приложения. */
	@Bean
	public CommandLineRunner threadDemoRunner(BoundedBufferDemoService boundedBufferDemoService) {
		return args -> boundedBufferDemoService.runBoundedBufferDemo();
	}

}
