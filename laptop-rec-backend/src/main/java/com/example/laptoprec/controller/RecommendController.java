package com.example.laptoprec.controller;

import com.example.laptoprec.common.Result;
import com.example.laptoprec.dto.RecommendChatRequest;
import com.example.laptoprec.service.RecommendService;
import com.example.laptoprec.vo.RecommendChatVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommend")
public class RecommendController {
    private final RecommendService recommendService;

    public RecommendController(RecommendService recommendService) {
        this.recommendService = recommendService;
    }

    @PostMapping("/chat")
    public Result<RecommendChatVO> chat(@RequestBody RecommendChatRequest request) {
        return Result.ok(recommendService.chat(request));
    }
}
