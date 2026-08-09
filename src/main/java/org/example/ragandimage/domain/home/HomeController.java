package org.example.ragandimage.domain.home;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// [Step 0-5] 홈 인덱스 화면 컨트롤러
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "home";
    }
}
