package org.example.ragandimage.domain.rag.controller;

import lombok.RequiredArgsConstructor;
import org.example.ragandimage.domain.rag.service.PdfIngestService;
import org.example.ragandimage.domain.rag.service.RagQueryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

// [Step 1-5] PDF RAG 웹 컨트롤러
@Controller
@RequestMapping("/rag")
@RequiredArgsConstructor
public class RagDocumentController {

    private final PdfIngestService pdfIngestService;
    private final RagQueryService ragQueryService;

    @GetMapping
    public String index() {
        return "rag/index";
    }

    // PDF 업로드 및 벡터 DB 수집
    @PostMapping("/documents")
    public String uploadPdf(@RequestParam("file") MultipartFile file, Model model) {
        try {
            int count = pdfIngestService.ingestPdf(file);
            model.addAttribute("message", "%d개 청크 저장 완료".formatted(count));
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        return "rag/index";
    }

    // RAG 질의응답
    @PostMapping("/ask")
    public String askQuestion(@RequestParam("question") String question, Model model) {
        RagQueryService.RagResponse response = ragQueryService.askQuestion(question);
        model.addAttribute("question", question);
        model.addAttribute("answer", response.answer());
        model.addAttribute("sources", response.sources());
        return "rag/index";
    }
}
