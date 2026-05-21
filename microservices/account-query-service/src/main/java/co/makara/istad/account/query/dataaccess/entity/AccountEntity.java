package co.makara.istad.account.query.dataaccess.entity;

import co.istad.makara.common.domain.valueobject.AccountStatus;
import co.istad.makara.common.domain.valueobject.AccountTypeCode;
import co.istad.makara.common.domain.valueobject.Currency;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Table(name = "accounts")
public class AccountEntity implements Persistable<UUID> {

    @Id
    private UUID accountId;

    private UUID customerId;

    private UUID branchId;

    private UUID accountTypeId;

    private String accountNumber;

    private String accountHolder;

    private BigDecimal balance;

    private Currency currency;

    private AccountTypeCode typeCode;

    private AccountStatus accountStatus;

    private String createdBy;

    private String updatedBy;

    private ZonedDateTime createdAt;

    private ZonedDateTime updatedAt;

    @Transient
    private boolean newEntity = false;

    @Override
    public UUID getId() {
        return accountId;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    public AccountEntity markNew() {
        this.newEntity = true;
        return this;
    }
}