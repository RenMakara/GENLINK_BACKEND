package co.makara.istad.account.query.applicationservice;

import co.makara.istad.account.query.applicationservice.dto.AccountQueryResponse;
import co.makara.istad.account.query.applicationservice.mapper.AccountAppDataMapper;
import co.makara.istad.account.query.applicationservice.ports.input.service.AccountQueryService;
import co.makara.istad.account.query.applicationservice.ports.output.repository.AccountQueryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountQueryServiceImpl implements AccountQueryService {

    private final AccountQueryRepository accountQueryRepository;
    private final AccountAppDataMapper accountAppDataMapper;

    @Override
    public Mono<AccountQueryResponse> getByAccountId(UUID accountId) {
        return accountQueryRepository
                .findById(accountId)
                .map(accountAppDataMapper::accountViewToAccountQueryResponse);
    }

}
