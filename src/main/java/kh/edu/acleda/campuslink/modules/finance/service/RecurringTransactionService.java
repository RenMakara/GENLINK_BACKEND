package kh.edu.acleda.campuslink.modules.finance.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.finance.dto.request.RecurringTransactionCreateRequest;
import kh.edu.acleda.campuslink.modules.finance.dto.response.RecurringTransactionResponse;

/**
 * Recurring income/expense templates. Users manage their own; the scheduler materialises the due
 * ones into real income/expense rows (and moves the wallet) through {@link #processDue}.
 */
public interface RecurringTransactionService {

    RecurringTransactionResponse create(RecurringTransactionCreateRequest request);

    List<RecurringTransactionResponse> getMine();

    void delete(UUID id);

    /**
     * Materialise every active template whose next run is on or before {@code asOf}, catching up if
     * several runs were missed. Not user-scoped - for the scheduler. Returns occurrences created.
     */
    int processDue(LocalDate asOf);
}
