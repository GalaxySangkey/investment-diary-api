package com.investmentdiary.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.investmentdiary.entity.ContactInquiry;

public interface ContactInquiryRepository extends JpaRepository<ContactInquiry, Long> {
}
