package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"com.example.demo", "main.java"})
@EntityScan("main.java.entities")
@EnableJpaRepositories("main.java.repositories")
public class MsTransaccionesApplication {

	public static void main(String[] args) {
		SpringApplication.run(MsTransaccionesApplication.class, args);
	}

}
