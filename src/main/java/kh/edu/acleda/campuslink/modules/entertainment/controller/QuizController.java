package kh.edu.acleda.campuslink.modules.entertainment.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.dto.request.QuizSubmitRequest;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.QuizAttemptResponse;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.QuizAttemptSummaryResponse;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.QuizDetailResponse;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.QuizResponse;
import kh.edu.acleda.campuslink.modules.entertainment.enums.QuizDifficulty;
import kh.edu.acleda.campuslink.modules.entertainment.service.QuizService;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.localization.LanguageResolver;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/entertainment/quizzes")
@Tag(name = "Entertainment - Quizzes")
public class QuizController {

    private final QuizService quizService;
    private final LanguageResolver languageResolver;

    public QuizController(QuizService quizService, LanguageResolver languageResolver) {
        this.quizService = quizService;
        this.languageResolver = languageResolver;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<QuizResponse>>> getAll(
        @RequestParam(required = false) String category,
        @RequestParam(required = false) QuizDifficulty difficulty,
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success("Quizzes retrieved",
            quizService.getAll(category, difficulty, language(userDetails, acceptLanguage))));
    }

    /** Literal path, declared before /{id} so it cannot be shadowed. */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<QuizAttemptSummaryResponse>>> history(
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success("Quiz history retrieved",
            quizService.history(language(userDetails, acceptLanguage))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<QuizDetailResponse>> getById(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success("Quiz retrieved",
            quizService.getQuiz(id, language(userDetails, acceptLanguage))));
    }

    @PostMapping("/{id}/attempts")
    public ResponseEntity<ApiResponse<QuizAttemptResponse>> submit(@PathVariable UUID id,
                                                                   @Valid @RequestBody QuizSubmitRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Quiz submitted", quizService.submit(id, request)));
    }

    private Language language(UserDetails userDetails, String acceptLanguage) {
        String email = userDetails == null ? null : userDetails.getUsername();
        return languageResolver.resolve(email, acceptLanguage);
    }
}
