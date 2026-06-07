package kh.edu.acleda.campuslink.modules.entertainment.service.impl;

import kh.edu.acleda.campuslink.modules.entertainment.dto.response.ChallengeResponse;
import kh.edu.acleda.campuslink.modules.entertainment.service.ChallengeService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChallengeServiceImpl implements ChallengeService {

    @Override
    public List<ChallengeResponse> getAll() {
        // TODO: Load Challenge data from repository with localized fields selected by LanguageResolver.
        return List.of();
    }
}
