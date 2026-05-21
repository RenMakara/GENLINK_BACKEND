package co.makara.istad.account.query.applicationservice.ports.input.service;

import co.makara.istad.account.query.applicationservice.dto.AccountQueryResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

// Input Port for application service
public interface AccountQueryService {

    Mono<AccountQueryResponse> getByAccountId(UUID accountId);

}
