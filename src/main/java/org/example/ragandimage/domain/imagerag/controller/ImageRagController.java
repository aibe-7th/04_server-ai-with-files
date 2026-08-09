package org.example.ragandimage.domain.imagerag.controller;

import lombok.RequiredArgsConstructor;
import org.example.ragandimage.domain.imagerag.service.ImageIngestService;
import org.example.ragandimage.domain.imagerag.service.ImageSearchService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

// [Step 2-5] Image RAG 웹 컨트롤러
@Controller
@RequestMapping("/image-rag")
@RequiredArgsConstructor
public class ImageRagController {

    private final ImageIngestService imageIngestService;
    private final ImageSearchService imageSearchService;

    @GetMapping
    public String index() {
        return "image-rag/index";
    }

    // 이미지 업로드 요청
    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file,
                         @RequestParam(value = "ownerId", defaultValue = "userA") String ownerId,
                         Model model) {
        try {
            imageIngestService.ingestImage(file, ownerId);
            model.addAttribute("message", "이미지 분석 및 수집 완료");
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        return "image-rag/index";
    }

    // 이미지 크로스모달 검색 요청
    @PostMapping("/search")
    public String search(@RequestParam("query") String query,
                         @RequestParam(value = "ownerId", defaultValue = "userA") String ownerId,
                         Model model) {
        model.addAttribute("results", imageSearchService.searchImages(query, ownerId));
        return "image-rag/index";
    }
}
