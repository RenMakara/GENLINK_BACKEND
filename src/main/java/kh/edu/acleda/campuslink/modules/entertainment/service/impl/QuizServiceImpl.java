package kh.edu.acleda.campuslink.modules.entertainment.service.impl;

import kh.edu.acleda.campuslink.modules.entertainment.dto.response.QuizResponse;
import kh.edu.acleda.campuslink.modules.entertainment.service.QuizService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QuizServiceImpl implements QuizService {

    @Override
    public List<QuizResponse> getAll() {
        // TODO: Load Quiz data from repository with localized fields selected by LanguageResolver.
        return List.of();
    }
}
