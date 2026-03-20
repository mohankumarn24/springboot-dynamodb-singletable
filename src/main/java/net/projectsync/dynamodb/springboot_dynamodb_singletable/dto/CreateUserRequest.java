package net.projectsync.dynamodb.springboot_dynamodb_singletable.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateUserRequest(
        @NotBlank String name,
        @Email String email,
        @Pattern(regexp = "\\+?[0-9]{10,15}") String phone
) {}