package co.makara.istad.account.query.applicationservice;

import co.istad.makara.common.domain.event.AccountCreatedEvent;
import co.makara.istad.account.query.applicationservice.mapper.AccountAppDataMapper;
import co.makara.istad.account.query.applicationservice.ports.input.message.listener.AccountMessageListener;
import co.makara.istad.account.query.applicationservice.ports.output.repository.AccountQueryRepository;
import co.makara.istad.account.query.domain.entity.AccountView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountMessageListenerImpl implements AccountMessageListener {

    private final AccountQueryRepository accountQueryRepository;
    private final AccountAppDataMapper accountAppDataMapper;

    @Override
    public void onAccountCreatedEvent(AccountCreatedEvent accountCreatedEvent) {
        AccountView accountView = accountAppDataMapper.toAccountView(accountCreatedEvent);
        accountQueryRepository.save(accountView)
                .doOnSuccess(data -> log.info("✅ Account : {} created", accountCreatedEvent.accountId()))
                .doOnError(err -> log.error("⚠️ Failed to save account: {}", accountCreatedEvent.accountId(), err))
                .block();
    }
}
