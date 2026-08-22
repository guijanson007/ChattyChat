package com.chattychat.Exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class InvalidInviteHandler {
    @ExceptionHandler(InvalidInviteException.class)
    public ResponseEntity<Object> handleInvalidInviteException(InvalidInviteException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
    }
}
