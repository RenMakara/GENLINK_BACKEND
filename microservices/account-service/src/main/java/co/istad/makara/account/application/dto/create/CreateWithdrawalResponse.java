package co.istad.makara.account.application.dto.create;

import co.istad.makara.common.domain.valueobject.Money;
import co.istad.makara.common.domain.valueobject.TransactionId;
import lombok.Builder;

@Builder
public record CreateWithdrawalResponse(
        Money amount,
        String remark
) {
}
