package com.ib.pki.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.security")
@Getter @Setter
public class AppProperties {
    private String masterKeySecret;
    private int masterKeyRotationBatchSize;
    private int masterKeyRotationDays;
}
