package net.projectsync.dynamodb.springboot_dynamodb_singletable.dto;

public record UpdateUserRequest(
        String name,
        String status,
        Integer entityVersion
) {}
