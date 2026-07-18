package kh.edu.acleda.campuslink.modules.finance.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.dto.request.QuizSubmitRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.LessonDetailResponse;
import kh.edu.acleda.campuslink.modules.finance.dto.response.LessonSummaryResponse;
import kh.edu.acleda.campuslink.modules.finance.dto.response.LiteracyDashboardResponse;
import kh.edu.acleda.campuslink.modules.finance.dto.response.QuizQuestionResponse;
import kh.edu.acleda.campuslink.modules.finance.dto.response.QuizResultResponse;
import kh.edu.acleda.campuslink.modules.finance.service.FinancialLiteracyService;
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
@RequestMapping("/api/v1/finance/literacy")
@Tag(name = "Finance - Financial Literacy")
public class FinancialLiteracyController {

    private final FinancialLiteracyService literacyService;
    private final LanguageResolver languageResolver;

    public FinancialLiteracyController(FinancialLiteracyService literacyService,
                                       LanguageResolver languageResolver) {
        this.literacyService = literacyService;
        this.languageResolver = languageResolver;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<LiteracyDashboardResponse>> dashboard() {
        return ResponseEntity.ok(ApiResponse.success("Learning dashboard retrieved",
            literacyService.getDashboard()));
    }

    @GetMapping("/lessons")
    public ResponseEntity<ApiResponse<List<LessonSummaryResponse>>> lessons(
        @RequestParam(required = false) String topic,
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success("Lessons retrieved",
            literacyService.getLessons(topic, language(userDetails, acceptLanguage))));
    }

    @GetMapping("/lessons/{id}")
    public ResponseEntity<ApiResponse<LessonDetailResponse>> lesson(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success("Lesson retrieved",
            literacyService.getLesson(id, language(userDetails, acceptLanguage))));
    }

    @PostMapping("/lessons/{id}/complete")
    public ResponseEntity<ApiResponse<LessonDetailResponse>> complete(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success("Lesson marked complete",
            literacyService.completeLesson(id, language(userDetails, acceptLanguage))));
    }

    @GetMapping("/lessons/{id}/quiz")
    public ResponseEntity<ApiResponse<List<QuizQuestionResponse>>> quiz(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success("Quiz retrieved",
            literacyService.getQuiz(id, language(userDetails, acceptLanguage))));
    }

    @PostMapping("/lessons/{id}/quiz/submit")
    public ResponseEntity<ApiResponse<QuizResultResponse>> submit(
        @PathVariable UUID id,
        @Valid @RequestBody QuizSubmitRequest request,
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success("Quiz submitted",
            literacyService.submitQuiz(id, request, language(userDetails, acceptLanguage))));
    }

    private Language language(UserDetails userDetails, String acceptLanguage) {
        String email = userDetails == null ? null : userDetails.getUsername();
        return languageResolver.resolve(email, acceptLanguage);
    }
}
