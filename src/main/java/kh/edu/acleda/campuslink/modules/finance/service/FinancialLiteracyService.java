package kh.edu.acleda.campuslink.modules.finance.service;

import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.dto.request.QuizSubmitRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.LessonDetailResponse;
import kh.edu.acleda.campuslink.modules.finance.dto.response.LessonSummaryResponse;
import kh.edu.acleda.campuslink.modules.finance.dto.response.LiteracyDashboardResponse;
import kh.edu.acleda.campuslink.modules.finance.dto.response.QuizQuestionResponse;
import kh.edu.acleda.campuslink.modules.finance.dto.response.QuizResultResponse;
import kh.edu.acleda.campuslink.shared.enums.Language;

public interface FinancialLiteracyService {

    /** Learning dashboard: overall + per-topic progress for the current user. */
    LiteracyDashboardResponse getDashboard();

    /** Lessons, optionally filtered by topic, with the user's completion flags. */
    List<LessonSummaryResponse> getLessons(String topic, Language language);

    LessonDetailResponse getLesson(UUID lessonId, Language language);

    /** "Finish Learning": marks the lesson read. No reward - that comes from the quiz. */
    LessonDetailResponse completeLesson(UUID lessonId, Language language);

    /** Quiz questions with options, correct answer withheld. */
    List<QuizQuestionResponse> getQuiz(UUID lessonId, Language language);

    /** Grades the quiz, records the attempt, and pays the reward once on first pass. */
    QuizResultResponse submitQuiz(UUID lessonId, QuizSubmitRequest request, Language language);
}
