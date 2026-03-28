package com.investmentdiary.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.investmentdiary.dto.ApiResponse;

@RestController
@RequestMapping("/api/v1/cors-test")
@CrossOrigin(origins = "*", maxAge = 3600)
public class CorsTestController {

    @GetMapping("/ping")
    public ApiResponse<Map<String, Object>> ping() {
        Map<String, Object> data = new HashMap<>();
        data.put("message", "CORS 테스트 성공!");
        data.put("timestamp", LocalDateTime.now());
        data.put("server", "investment-diary-api");
        
        return ApiResponse.success(data, "CORS 설정이 정상적으로 작동합니다.");
    }

    @PostMapping("/echo")
    public ApiResponse<Map<String, Object>> echo(@RequestBody Map<String, Object> request) {
        Map<String, Object> data = new HashMap<>();
        data.put("received", request);
        data.put("timestamp", LocalDateTime.now());
        data.put("method", "POST");
        
        return ApiResponse.success(data, "POST 요청이 정상적으로 처리되었습니다.");
    }

}
