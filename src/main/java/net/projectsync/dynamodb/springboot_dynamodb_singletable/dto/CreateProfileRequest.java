package net.projectsync.dynamodb.springboot_dynamodb_singletable.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateProfileRequest(
        @NotBlank String profileName,
        Boolean isKidsProfile,
        Boolean isDefault   // NEW FIELD
) {}