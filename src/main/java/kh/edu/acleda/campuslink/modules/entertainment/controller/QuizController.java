package kh.edu.acleda.campuslink.modules.entertainment.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.QuizResponse;
import kh.edu.acleda.campuslink.modules.entertainment.service.QuizService;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/entertainment/quizzes")
@Tag(name = "Entertainment - Quizzes")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<QuizResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success("Quiz list retrieved", quizService.getAll()));
    }
}
