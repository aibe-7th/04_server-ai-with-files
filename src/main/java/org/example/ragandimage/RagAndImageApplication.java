package org.example.ragandimage;

import org.example.ragandimage.global.config.AiProperties;
import org.example.ragandimage.global.config.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

// [Step 0-3] @EnableConfigurationProperties 바인딩 활성화
@SpringBootApplication
@EnableConfigurationProperties({AiProperties.class, StorageProperties.class})
public class RagAndImageApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagAndImageApplication.class, args);
    }

}
