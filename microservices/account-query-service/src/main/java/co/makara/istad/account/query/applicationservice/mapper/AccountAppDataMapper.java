package co.makara.istad.account.query.applicationservice.mapper;

import co.istad.makara.common.domain.event.AccountCreatedEvent;
import co.makara.istad.account.query.applicationservice.dto.AccountQueryResponse;
import co.makara.istad.account.query.domain.entity.AccountView;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;


@Mapper(componentModel = "spring")
public interface AccountAppDataMapper {

    AccountQueryResponse accountViewToAccountQueryResponse(AccountView accountView);

    @Mapping(target = "accountId", source = "accountId.value")
    @Mapping(target = "customerId", source = "customerId.value")
    @Mapping(target = "branchId", source = "branchId.value")
    @Mapping(target = "money", source = "initialBalance")
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AccountView toAccountView(AccountCreatedEvent accountCreatedEvent);
}
