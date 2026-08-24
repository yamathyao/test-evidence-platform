package com.talkanything.testevidence.platform.shared;

import com.talkanything.testevidence.platform.profile.ProfileConflictException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    Map<String, String> notFound(NotFoundException exception) { return Map.of("code", "NOT_FOUND", "message", exception.getMessage()); }
    @ExceptionHandler({ProfileConflictException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, String> conflict(RuntimeException exception) { return Map.of("code", "CONFLICT", "message", exception.getMessage()); }
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> invalid(IllegalArgumentException exception) { return Map.of("code", "VALIDATION_ERROR", "message", exception.getMessage()); }
}
