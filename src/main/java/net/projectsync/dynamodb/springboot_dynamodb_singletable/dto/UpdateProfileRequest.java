package net.projectsync.dynamodb.springboot_dynamodb_singletable.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(
        @NotBlank String profileName
) {}
