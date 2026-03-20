package net.projectsync.dynamodb.springboot_dynamodb_singletable.config;

import lombok.extern.slf4j.Slf4j;
import net.projectsync.dynamodb.springboot_dynamodb_singletable.dto.CreateProfileRequest;
import net.projectsync.dynamodb.springboot_dynamodb_singletable.dto.CreateUserRequest;
import net.projectsync.dynamodb.springboot_dynamodb_singletable.dto.UserResponse;
import net.projectsync.dynamodb.springboot_dynamodb_singletable.service.UserService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;

@Configuration
@Slf4j
@Profile("dev")
public class DataLoader {

    @Bean
    @Order(2)
    CommandLineRunner loadData(UserService service) {

        return args -> {

            UserResponse user;

            try {
                user = service.login("mohan@gmail.com", null);
                log.info("User already exists: {}", user.userId());
            } catch (Exception ex) {

                CreateUserRequest userReq = new CreateUserRequest(
                        "Mohan",
                        "mohan@gmail.com",
                        "9876543210"
                );

                user = service.createUser(userReq);
                log.info("User created: {}", user.userId());
            }

            String userId = user.userId();

            // Profiles
            try {
                service.createProfile(userId,
                        new CreateProfileRequest("Movies", false, true));
            } catch (Exception ignored) {}

            try {
                service.createProfile(userId,
                        new CreateProfileRequest("Music", false, false));
            } catch (Exception ignored) {}

            try {
                service.createProfile(userId,
                        new CreateProfileRequest("Kids", true, false));
            } catch (Exception ignored) {}
        };
    }
}