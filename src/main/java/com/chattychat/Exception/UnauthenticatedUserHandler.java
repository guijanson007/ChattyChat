package com.chattychat.Exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class UnauthenticatedUserHandler {
    @ExceptionHandler(UnauthenticatedUserException.class)
    public ResponseEntity<Object> handleUnauthenticatedUserException(UnauthenticatedUserException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    }
}
