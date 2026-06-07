package kh.edu.acleda.campuslink.modules.entertainment.service;

import kh.edu.acleda.campuslink.modules.entertainment.dto.response.QuizResponse;

import java.util.List;

public interface QuizService {
    List<QuizResponse> getAll();
}
