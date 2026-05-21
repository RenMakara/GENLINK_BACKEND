package co.istad.makara.account.application.dto.create;

import java.util.Map;
import java.util.UUID;

public record CustomerResponse(
        UUID customerId,
        Map<String, String> name
) {
}
