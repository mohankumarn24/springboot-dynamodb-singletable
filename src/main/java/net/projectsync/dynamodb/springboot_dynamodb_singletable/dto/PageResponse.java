package net.projectsync.dynamodb.springboot_dynamodb_singletable.dto;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        String nextCursor
) {}
