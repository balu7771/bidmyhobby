package com.bidmyhobby.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@org.springframework.scheduling.annotation.EnableAsync
public class CatalogServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(CatalogServiceApplication.class, args);
	}

}
