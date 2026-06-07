package kh.edu.acleda.campuslink.modules.profile.service;

import kh.edu.acleda.campuslink.modules.profile.dto.request.UserSettingUpdateRequest;
import kh.edu.acleda.campuslink.modules.profile.dto.response.UserSettingResponse;

public interface UserSettingService {
    UserSettingResponse getMine();
    UserSettingResponse update(UserSettingUpdateRequest request);
}
