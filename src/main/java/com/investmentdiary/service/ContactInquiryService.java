package com.investmentdiary.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.investmentdiary.dto.contact.ContactInquiryCreatedResponse;
import com.investmentdiary.dto.contact.ContactInquiryRequest;
import com.investmentdiary.entity.ContactInquiry;
import com.investmentdiary.repository.ContactInquiryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ContactInquiryService {

    private final ContactInquiryRepository contactInquiryRepository;

    @Transactional
    public ContactInquiryCreatedResponse submit(ContactInquiryRequest request) {
        ContactInquiry row = ContactInquiry.builder()
            .name(request.getName().trim())
            .email(request.getEmail().trim())
            .category(request.getSubject().trim())
            .message(request.getMessage().trim())
            .build();
        ContactInquiry saved = contactInquiryRepository.save(row);
        return ContactInquiryCreatedResponse.builder().id(saved.getId()).build();
    }
}
