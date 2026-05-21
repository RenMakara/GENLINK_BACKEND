package co.makara.istad.account.query.applicationservice.ports.output.repository;

import co.makara.istad.account.query.domain.entity.AccountView;
import reactor.core.publisher.Mono;

import java.util.UUID;

// Output Port for data access technology
public interface AccountQueryRepository {

    // save account query
    Mono<AccountView> save(AccountView accountView);
    Mono<AccountView> findById(UUID accountId);

}
