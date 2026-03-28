package com.investmentdiary.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.investmentdiary.constants.ResponseCode;
import com.investmentdiary.dto.UnifiedApiResponse;
import com.investmentdiary.dto.contact.ContactInquiryCreatedResponse;
import com.investmentdiary.dto.contact.ContactInquiryRequest;
import com.investmentdiary.service.ContactInquiryRateLimiter;
import com.investmentdiary.service.ContactInquiryService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/contact")
@RequiredArgsConstructor
public class ContactController {

    private final ContactInquiryService contactInquiryService;
    private final ContactInquiryRateLimiter contactInquiryRateLimiter;

    @PostMapping("/inquiries")
    public UnifiedApiResponse<ContactInquiryCreatedResponse> submitInquiry(
            @Valid @RequestBody ContactInquiryRequest request,
            HttpServletRequest httpRequest) {
        contactInquiryRateLimiter.checkAndRecord(httpRequest);
        ContactInquiryCreatedResponse data = contactInquiryService.submit(request);
        return UnifiedApiResponse.<ContactInquiryCreatedResponse>builder()
            .success(true)
            .code(ResponseCode.CREATED)
            .message("문의가 접수되었습니다.")
            .data(data)
            .count(1)
            .timestamp(java.time.LocalDateTime.now())
            .path(httpRequest.getRequestURI())
            .build();
    }
}
