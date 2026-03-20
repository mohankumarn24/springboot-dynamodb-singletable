package net.projectsync.dynamodb.springboot_dynamodb_singletable.dto;

public record UserResponse(
        String userId,
        String name,
        String selectedProfileId,
        String defaultProfileId,
        Integer profilesCount,
        String status
) {}
