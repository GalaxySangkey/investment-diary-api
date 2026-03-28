package com.investmentdiary.config;

import com.investmentdiary.util.EncryptionUtil;
import com.investmentdiary.util.EncryptionUtilHolder;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class EncryptionConfig {

    private final EncryptionUtil encryptionUtil;

    @PostConstruct
    public void init() {
        EncryptionUtilHolder.set(encryptionUtil);
    }
}
