package org.example.ragandimage.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// [Step 0-3] app.storage 프로퍼티 바인딩 Record
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String bucket
) {}
