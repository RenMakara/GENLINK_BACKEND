package kh.edu.acleda.campuslink.modules.finance.service;

import kh.edu.acleda.campuslink.modules.finance.dto.request.IncomeCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.CashFlowResponse;
import kh.edu.acleda.campuslink.modules.finance.dto.response.IncomeResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface IncomeService {

    IncomeResponse create(IncomeCreateRequest request);

    /** The caller's income, newest first. source, month and year are optional filters. */
    PageResponse<IncomeResponse> getMine(String source, Integer month, Integer year, Pageable pageable);

    IncomeResponse getById(UUID id);

    IncomeResponse update(UUID id, IncomeCreateRequest request);

    void delete(UUID id);

    /** A month's income vs expense, net, and savings rate - the cash-flow picture. */
    CashFlowResponse cashFlow(int month, int year);
}
