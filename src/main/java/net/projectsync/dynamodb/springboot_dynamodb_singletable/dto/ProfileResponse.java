package net.projectsync.dynamodb.springboot_dynamodb_singletable.dto;

public record ProfileResponse(
        String userId,
        String profileId,
        String profileName,
        boolean isDefault,
        boolean isKidsProfile
) {}