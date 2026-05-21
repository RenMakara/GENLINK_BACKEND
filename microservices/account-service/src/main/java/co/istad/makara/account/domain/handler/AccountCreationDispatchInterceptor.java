package co.istad.makara.account.domain.handler;


import co.istad.makara.account.domain.command.CreateAccountCommand;
import co.istad.makara.account.client.adapter.CustomerClientImpl;
import co.istad.makara.account.domain.exception.AccountDomainException;
import co.istad.makara.common.domain.valueobject.CustomerId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.BiFunction;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountCreationDispatchInterceptor implements MessageDispatchInterceptor<CommandMessage<?>> {

    private final CustomerClientImpl customerClient;

    @Override
    public BiFunction<Integer, CommandMessage<?>, CommandMessage<?>> handle(List<? extends CommandMessage<?>> messages) {
        return (index, command) -> {

            // first check command, is Command for create account?
            if (CreateAccountCommand.class.equals(command.getPayloadType())) {

                CreateAccountCommand createCommand = (CreateAccountCommand) command.getPayload();

                CustomerId customerId = createCommand.customerId();

                log.info("Interceptor: Checking Customer ID: {} ...", customerId);

                try{
                    customerClient.getCustomer(customerId);
                    log.info("😍😍 Interceptor: Customer ID: {} found", customerId);
                }catch (AccountDomainException e){
                    log.error("Interceptor: Customer ID: {} not found", customerId);
                    throw new AccountDomainException("⚠️😭 Customer not found");
                }
            }
            return command;
        };
    }
}