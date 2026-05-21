package co.makara.istad.account.query.dataaccess.adapter;

import co.makara.istad.account.query.applicationservice.ports.output.repository.AccountQueryRepository;
import co.makara.istad.account.query.dataaccess.entity.AccountEntity;
import co.makara.istad.account.query.dataaccess.mapper.AccountDataAccessMapper;
import co.makara.istad.account.query.dataaccess.repository.AccountQueryReactiveRepository;
import co.makara.istad.account.query.domain.entity.AccountView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AccountQueryRepositoryImpl implements AccountQueryRepository {

    private final AccountQueryReactiveRepository accountQueryReactiveRepository;
    private final AccountDataAccessMapper accountDataAccessMapper;
    @Override
    public Mono<AccountView> save(AccountView accountView) {

        AccountEntity accountEntity = accountDataAccessMapper.toAccountEntity(accountView);

        // Idempotent: if already exists (duplicate event from Kafka), skip insert
        return accountQueryReactiveRepository
                .findById(accountEntity.getAccountId())
                .map(accountDataAccessMapper::toAccountView)
                .switchIfEmpty(Mono.defer(() -> {
                    accountEntity.markNew();
                    return accountQueryReactiveRepository
                            .save(accountEntity)
                            .map(accountDataAccessMapper::toAccountView);
                }));
    }

    @Override
    public Mono<AccountView> findById(UUID accountId) {

        return accountQueryReactiveRepository
                .findById(accountId)
                .map(accountDataAccessMapper::toAccountView);
    }
}

