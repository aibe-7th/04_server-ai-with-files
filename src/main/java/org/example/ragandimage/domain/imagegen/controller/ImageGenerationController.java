package org.example.ragandimage.domain.imagegen.controller;

import lombok.RequiredArgsConstructor;
import org.example.ragandimage.domain.imagegen.service.ImageGenerationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

// [Step 3-5] 이미지 생성 웹 컨트롤러
@Controller
@RequestMapping("/images")
@RequiredArgsConstructor
public class ImageGenerationController {

    private final ImageGenerationService service;

    @GetMapping("/generate")
    public String form() {
        return "images/form";
    }

    // 이미지 생성 및 미리보기 요청
    @PostMapping("/generate")
    public String generate(@RequestParam("prompt") String prompt,
                           @RequestParam(value = "seed", required = false) Long seed,
                           Model model) {
        model.addAttribute("result", service.generate(prompt, seed));
        return "images/form";
    }

    // 선택 저장 요청
    @PostMapping("/save")
    public String save(@RequestParam("prompt") String prompt,
                       @RequestParam("enhancedPrompt") String enhancedPrompt,
                       @RequestParam("base64Image") String base64Image,
                       @RequestParam("seed") Long seed,
                       Model model) {
        service.saveImage(prompt, enhancedPrompt, base64Image, seed);
        model.addAttribute("message", "이미지 저장 완료");
        return "images/form";
    }
}
