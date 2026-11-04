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

	/** Запуск сравнения stream() и parallelStream() при старте приложения. */
	@Bean
	public CommandLineRunner threadDemoRunner(StreamBenchmarkService streamBenchmarkService) {
		return args -> streamBenchmarkService.runStreamBenchmark();
	}

}
