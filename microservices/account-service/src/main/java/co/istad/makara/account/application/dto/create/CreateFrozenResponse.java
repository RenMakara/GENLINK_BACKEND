package co.istad.makara.account.application.dto.create;

import co.istad.makara.common.domain.valueobject.AccountId;
import lombok.Builder;

@Builder
public record CreateFrozenResponse(
        AccountId accountId,
        String remark
) {
}
