package kh.edu.acleda.campuslink.modules.entertainment.service;

import kh.edu.acleda.campuslink.modules.entertainment.dto.response.BadgeResponse;

import java.util.List;

public interface BadgeService {
    List<BadgeResponse> getAll();
}
