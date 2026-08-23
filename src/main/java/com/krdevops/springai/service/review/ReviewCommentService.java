package com.krdevops.springai.service.review;

import com.krdevops.springai.model.review.ScreenReviewSession;
import org.springframework.stereotype.Service;

@Service
public class ReviewCommentService {
    public ScreenReviewSession.Comment create(String id, String author, String text, String evidenceLocation) {
        if (evidenceLocation == null || evidenceLocation.isBlank()) throw new IllegalArgumentException("Comment Evidence 위치는 필수입니다.");
        return new ScreenReviewSession.Comment(id, author, text, evidenceLocation);
    }
}
