package co.istad.makara.account.application.dto.create;

import co.istad.makara.common.domain.valueobject.Money;
import lombok.Builder;

@Builder
public record CreateDepositResponse(
        Money amount,
        String remark
) {
}
