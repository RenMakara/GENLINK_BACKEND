package co.makara.istad.account.query.applicationservice.dto;

import java.util.UUID;

public record AccountQueryResponse(
        UUID accountId,
        String accountNumber,
        String accountHolder
) {
}
