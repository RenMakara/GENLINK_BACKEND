package kh.edu.acleda.campuslink.modules.admin.notification;

import java.util.List;
import kh.edu.acleda.campuslink.modules.admin.notification.dto.request.AdminBroadcastRequest;
import kh.edu.acleda.campuslink.modules.admin.notification.dto.request.AdminNotificationTemplateCreateRequest;
import kh.edu.acleda.campuslink.modules.admin.notification.dto.request.AdminNotificationTemplateUpdateRequest;
import kh.edu.acleda.campuslink.modules.admin.notification.dto.response.AdminBroadcastResponse;
import kh.edu.acleda.campuslink.modules.admin.notification.dto.response.AdminNotificationTemplateResponse;

/** Application boundary for audited Admin notification operations. */
public interface AdminNotificationService {

    List<AdminNotificationTemplateResponse> listTemplates();

    AdminNotificationTemplateResponse createTemplate(AdminNotificationTemplateCreateRequest request);

    AdminNotificationTemplateResponse updateTemplate(
        String templateKey, AdminNotificationTemplateUpdateRequest request);

    AdminBroadcastResponse broadcast(AdminBroadcastRequest request);
}
