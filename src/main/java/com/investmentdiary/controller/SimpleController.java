package com.investmentdiary.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.investmentdiary.dto.UnifiedApiResponse;
import com.investmentdiary.constants.ResponseCode;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SimpleController {

    @GetMapping("/hello")
    public UnifiedApiResponse<Map<String, Object>> hello(jakarta.servlet.http.HttpServletRequest httpRequest) {
        Map<String, Object> data = new HashMap<>();
        data.put("message", "Hello from Investment Diary API!");
        data.put("timestamp", LocalDateTime.now());
        data.put("status", "OK");
        
        return UnifiedApiResponse.<Map<String, Object>>builder()
                .success(true)
                .code(ResponseCode.SUCCESS)
                .message("API 서버가 정상적으로 동작 중입니다.")
                .data(data)
                .count(1)
                .timestamp(LocalDateTime.now())
                .path(httpRequest.getRequestURI())
                .build();
    }

    @GetMapping("/status")
    public UnifiedApiResponse<Map<String, Object>> status(jakarta.servlet.http.HttpServletRequest httpRequest) {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "UP");
        data.put("service", "investment-diary-api");
        data.put("timestamp", LocalDateTime.now());
        
        return UnifiedApiResponse.<Map<String, Object>>builder()
                .success(true)
                .code(ResponseCode.SUCCESS)
                .message("서비스 상태가 정상입니다.")
                .data(data)
                .count(1)
                .timestamp(LocalDateTime.now())
                .path(httpRequest.getRequestURI())
                .build();
    }
}
