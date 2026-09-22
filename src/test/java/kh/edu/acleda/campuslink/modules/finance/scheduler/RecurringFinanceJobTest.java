package kh.edu.acleda.campuslink.modules.finance.scheduler;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import kh.edu.acleda.campuslink.modules.finance.service.AutoSaveService;
import kh.edu.acleda.campuslink.modules.finance.service.RecurringTransactionService;
import kh.edu.acleda.campuslink.shared.service.ScheduledJobRunRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecurringFinanceJobTest {

    @Mock
    private RecurringTransactionService recurringTransactionService;

    @Mock
    private AutoSaveService autoSaveService;

    @Mock
    private ScheduledJobRunRegistry registry;

    @Test
    void bothSuccessfulSubtasksRecordOneSuccessfulRun() {
        when(recurringTransactionService.processDue(org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(autoSaveService.processDue(org.mockito.ArgumentMatchers.any())).thenReturn(1);

        new RecurringFinanceJob(recurringTransactionService, autoSaveService, registry).run();

        verify(registry).recordSuccess(ScheduledJobRunRegistry.RECURRING_FINANCE_JOB);
    }

    @Test
    void oneFailedSubtaskMarksTheWholeRunFailedAndStillRunsTheOther() {
        doThrow(new IllegalStateException("finance failure"))
            .when(recurringTransactionService).processDue(org.mockito.ArgumentMatchers.any());
        when(autoSaveService.processDue(org.mockito.ArgumentMatchers.any())).thenReturn(1);

        new RecurringFinanceJob(recurringTransactionService, autoSaveService, registry).run();

        verify(autoSaveService).processDue(org.mockito.ArgumentMatchers.any());
        verify(registry).recordFailure(ScheduledJobRunRegistry.RECURRING_FINANCE_JOB);
    }
}
