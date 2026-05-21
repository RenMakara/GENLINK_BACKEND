package co.makara.istad.account.query.applicationservice.ports.input.message.listener;

import co.istad.makara.common.domain.event.AccountCreatedEvent;

public interface AccountMessageListener {

    void onAccountCreatedEvent(AccountCreatedEvent accountCreatedEvent);

}
