package com.example.laptoprec.service;

import com.example.laptoprec.dto.RecommendChatRequest;
import com.example.laptoprec.vo.RecommendChatVO;

public interface RecommendService {
    RecommendChatVO chat(RecommendChatRequest request);
}
