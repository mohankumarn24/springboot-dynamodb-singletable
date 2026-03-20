package net.projectsync.dynamodb.springboot_dynamodb_singletable.dto;

public record ErrorResponse(
        String message,
        int status
) {}
