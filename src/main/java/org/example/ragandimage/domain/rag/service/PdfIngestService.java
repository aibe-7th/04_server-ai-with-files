package org.example.ragandimage.domain.rag.service;

import lombok.RequiredArgsConstructor;
import org.example.ragandimage.domain.rag.validator.PdfValidator;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

// [Step 1-2] PDF 수집, 분할, VectorStore 저장 서비스
@Service
@RequiredArgsConstructor
public class PdfIngestService {

    private final VectorStore vectorStore;
    private final PdfValidator pdfValidator;

    public int ingestPdf(MultipartFile file) throws IOException {
        // [Step 1-2.1] PDF 기본 유효성 검증
        pdfValidator.validate(file);

        // [Step 1-2.2] PDF Document Reader로 페이지 추출
        Resource resource = new InputStreamResource(file.getInputStream());
        PagePdfDocumentReader reader = new PagePdfDocumentReader(
                resource, PdfDocumentReaderConfig.builder().build());
        List<Document> pages = reader.read();

        /*
         * [초기 실습 단계 미사용 코드 흔적]
         * 초기 단계를 위한 단순 페이지 전체 출력 확인 코드
         * System.out.println("추출된 전체 페이지 수: " + pages.size());
         */

        // [Step 1-2.3] 텍스트 길이 최소 기준 검증
        String fullText = pages.stream().map(Document::getText).reduce("", (a, b) -> a + b);
        pdfValidator.validateExtractedTextLength(fullText);

        // [Step 1-2.4] TokenTextSplitter로 청크 분할 (기본 800 토큰)
        List<Document> chunks = new TokenTextSplitter().apply(pages);

        // [Step 1-2.5] 메타데이터 보강 (원본 파일명 기록)
        chunks.forEach(chunk -> chunk.getMetadata().put("fileName", file.getOriginalFilename()));

        // [Step 1-2.6] Supabase PgVector에 저장
        vectorStore.add(chunks);
        return chunks.size();
    }
}
