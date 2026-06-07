package kh.edu.acleda.campuslink.modules.entertainment.service;

import kh.edu.acleda.campuslink.modules.entertainment.dto.response.ChallengeResponse;

import java.util.List;

public interface ChallengeService {
    List<ChallengeResponse> getAll();
}
