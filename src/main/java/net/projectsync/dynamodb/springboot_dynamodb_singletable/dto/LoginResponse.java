package net.projectsync.dynamodb.springboot_dynamodb_singletable.dto;

public record LoginResponse(
        String userId,
        String selectedProfileId,
        String status
) {}