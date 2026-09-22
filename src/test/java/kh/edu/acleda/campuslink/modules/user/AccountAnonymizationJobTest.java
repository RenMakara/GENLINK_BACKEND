package kh.edu.acleda.campuslink.modules.user;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import kh.edu.acleda.campuslink.shared.service.ScheduledJobRunRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountAnonymizationJobTest {

    @Mock
    private AccountAnonymizationService service;

    @Mock
    private ScheduledJobRunRegistry registry;

    @Test
    void successfulRunIsRecorded() {
        when(service.anonymizeExpiredAccounts()).thenReturn(2);

        new AccountAnonymizationJob(service, registry).anonymizeExpiredAccounts();

        verify(registry).recordSuccess(ScheduledJobRunRegistry.ACCOUNT_ANONYMIZATION_JOB);
    }

    @Test
    void failedRunIsRecordedAndSwallowedLikeBefore() {
        doThrow(new IllegalStateException("database failure"))
            .when(service).anonymizeExpiredAccounts();

        new AccountAnonymizationJob(service, registry).anonymizeExpiredAccounts();

        verify(registry).recordFailure(ScheduledJobRunRegistry.ACCOUNT_ANONYMIZATION_JOB);
    }
}
