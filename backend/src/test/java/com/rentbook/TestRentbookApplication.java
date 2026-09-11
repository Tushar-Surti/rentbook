package com.rentbook;

import org.springframework.boot.SpringApplication;

public class TestRentbookApplication {

	public static void main(String[] args) {
		SpringApplication.from(RentbookApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
