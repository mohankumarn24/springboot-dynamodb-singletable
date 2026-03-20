package net.projectsync.dynamodb.springboot_dynamodb_singletable.dto;

public record SelectedProfileResponse(
        String userId,
        String profileId,
        String profileName
) {}