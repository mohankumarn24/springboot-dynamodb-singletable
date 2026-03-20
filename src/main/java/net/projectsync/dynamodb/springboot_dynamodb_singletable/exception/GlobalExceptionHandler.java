package net.projectsync.dynamodb.springboot_dynamodb_singletable.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ConditionalCheckFailedException.class)
    public ResponseEntity<?> conflict() {
        return ResponseEntity.status(409).body("Duplicate / conflict");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> error(Exception e) {
        return ResponseEntity.status(500).body(e.getMessage());
    }
}
