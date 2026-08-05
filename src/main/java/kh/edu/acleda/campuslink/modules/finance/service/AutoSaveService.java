package kh.edu.acleda.campuslink.modules.finance.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.dto.request.AutoSaveRuleCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.AutoSaveRuleResponse;

/**
 * Automatic monthly saving into a goal (a percent of income, or round-up spare change). Users
 * manage their own rules; the scheduler runs the due ones through {@link #processDue}.
 */
public interface AutoSaveService {

    AutoSaveRuleResponse create(AutoSaveRuleCreateRequest request);

    List<AutoSaveRuleResponse> getMine();

    void delete(UUID id);

    /**
     * Run every active rule due on or before {@code asOf} over the month(s) it covers, moving money
     * into the goal. Not user-scoped - for the scheduler. Returns rules that moved money.
     */
    int processDue(LocalDate asOf);
}
