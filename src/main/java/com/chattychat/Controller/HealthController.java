package com.chattychat.Controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    @GetMapping("/actuator/health")
    public ResponseEntity<?> getHealth() {
        return ResponseEntity.status(200).build();
    }
}
