package com.chattychat.Exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class UserExceptionHandler {
    @ExceptionHandler(InvalidUserException.class)
    public ResponseEntity<Object> handleInvalidUserException(InvalidUserException ex) {
        return ResponseEntity.notFound().build();
    }
}
