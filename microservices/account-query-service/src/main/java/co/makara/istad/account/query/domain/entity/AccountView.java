package co.makara.istad.account.query.domain.entity;

import co.istad.makara.common.domain.valueobject.AccountStatus;
import co.istad.makara.common.domain.valueobject.AccountTypeCode;
import co.istad.makara.common.domain.valueobject.Money;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.UUID;
@Getter
@Setter
@NoArgsConstructor
public class AccountView {
    private UUID accountId;

    private UUID customerId;

    private UUID branchId;

    private AccountTypeCode accountTypeCode;

    private String accountNumber;

    private String accountHolder;

    private Money money;

    private AccountStatus accountStatus;

    private String createdBy;

    private String updatedBy;

    private ZonedDateTime createdAt;

    private ZonedDateTime updatedAt;
}
