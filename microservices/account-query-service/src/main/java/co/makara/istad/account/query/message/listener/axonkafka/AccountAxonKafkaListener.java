package co.makara.istad.account.query.message.listener.axonkafka;

import co.istad.makara.common.domain.event.AccountCreatedEvent;
import co.makara.istad.account.query.applicationservice.ports.input.message.listener.AccountMessageListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@ProcessingGroup("account-group")
public class AccountAxonKafkaListener {

    private final AccountMessageListener accountMessageListener;

    @EventHandler
    public void onAccountCreatedEvent(AccountCreatedEvent accountCreatedEvent){
        log.info("on AccountCreatedEvent : {}", accountCreatedEvent);
        accountMessageListener.onAccountCreatedEvent(accountCreatedEvent);
    }
}
