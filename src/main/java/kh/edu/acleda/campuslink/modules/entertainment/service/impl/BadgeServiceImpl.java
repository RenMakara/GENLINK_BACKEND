package kh.edu.acleda.campuslink.modules.entertainment.service.impl;

import kh.edu.acleda.campuslink.modules.entertainment.dto.response.BadgeResponse;
import kh.edu.acleda.campuslink.modules.entertainment.service.BadgeService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BadgeServiceImpl implements BadgeService {

    @Override
    public List<BadgeResponse> getAll() {
        // TODO: Load Badge data from repository with localized fields selected by LanguageResolver.
        return List.of();
    }
}
