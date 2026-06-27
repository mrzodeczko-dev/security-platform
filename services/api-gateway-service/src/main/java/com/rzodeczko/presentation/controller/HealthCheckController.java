package com.rzodeczko.presentation.controller;

import com.rzodeczko.presentation.dto.HealthCheckResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthCheckController {
    @GetMapping("/")
    public ResponseEntity<HealthCheckResponseDto> healthCheck() {
        return ResponseEntity.ok().body(new HealthCheckResponseDto("API GATEWAY SERVICE OK"));
    }
}
