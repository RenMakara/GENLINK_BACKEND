package kh.edu.acleda.campuslink.modules.entertainment.service;

import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.dto.request.QuizSubmitRequest;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.QuizAttemptResponse;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.QuizAttemptSummaryResponse;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.QuizDetailResponse;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.QuizResponse;
import kh.edu.acleda.campuslink.modules.entertainment.enums.QuizDifficulty;
import kh.edu.acleda.campuslink.shared.enums.Language;

public interface QuizService {

    List<QuizResponse> getAll(String category, QuizDifficulty difficulty, Language language);

    /** Quiz with questions + options, correct answer withheld. */
    QuizDetailResponse getQuiz(UUID id, Language language);

    /** Grades an attempt, logs it, and awards XP once per quiz (scaled by score). */
    QuizAttemptResponse submit(UUID quizId, QuizSubmitRequest request);

    List<QuizAttemptSummaryResponse> history(Language language);
}
