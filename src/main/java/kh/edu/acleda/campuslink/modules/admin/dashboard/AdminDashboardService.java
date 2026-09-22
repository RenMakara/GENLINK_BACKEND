package kh.edu.acleda.campuslink.modules.admin.dashboard;

import kh.edu.acleda.campuslink.modules.admin.dashboard.dto.response.AdminDashboardSummaryResponse;

/** Read-only dashboard aggregation for the admin portal. */
public interface AdminDashboardService {

    AdminDashboardSummaryResponse getSummary();
}
