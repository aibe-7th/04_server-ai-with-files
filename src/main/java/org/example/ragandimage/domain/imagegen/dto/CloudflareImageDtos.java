package org.example.ragandimage.domain.imagegen.dto;

import java.util.List;

// [Step 3-1] Cloudflare Workers AI API 요청/응답 DTO
public class CloudflareImageDtos {

    public record Request(
            String prompt,
            Integer num_steps,
            Long seed
    ) {}

    public record Response(
            Result result,
            boolean success,
            List<Error> errors
    ) {}

    public record Result(
            String image // Base64 인코딩 문자열
    ) {}

    public record Error(
            int code,
            String message
    ) {}
}
