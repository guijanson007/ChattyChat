package com.chattychat.Exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class OAuth2ExceptionHandler {
    @ExceptionHandler(OAuth2ProvisioningException.class)
    public ResponseEntity<Object> handleAuthenticationException(OAuth2ProvisioningException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
    }
}
