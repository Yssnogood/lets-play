package com.example.crudapi;

import com.example.crudapi.model.User;
import com.example.crudapi.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
public class CrudApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(CrudApiApplication.class, args);
	}

	@Bean
	CommandLineRunner createTestUser(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder) {

		return args -> {
			if (userRepository.findByEmail("admin@test.com").isEmpty()) {

				User admin = new User();
				admin.setName("Admin");
				admin.setEmail("admin@test.com");
				admin.setPassword(passwordEncoder.encode("admin123"));
				admin.setRole("ROLE_ADMIN");

				userRepository.save(admin);

				System.out.println("✅ Test admin user created");
			}
		};
	}
}
