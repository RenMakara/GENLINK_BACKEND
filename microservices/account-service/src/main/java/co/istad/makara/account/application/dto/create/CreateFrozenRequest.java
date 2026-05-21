package co.istad.makara.account.application.dto.create;

import co.istad.makara.common.domain.valueobject.AccountId;

public record CreateFrozenRequest(
        AccountId accountId,
        String remark,
        String requestedBy
) {
}
