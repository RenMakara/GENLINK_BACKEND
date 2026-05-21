package co.makara.istad.account.query.dataaccess.mapper;

import co.istad.makara.common.domain.valueobject.Money;
import co.makara.istad.account.query.dataaccess.entity.AccountEntity;
import co.makara.istad.account.query.domain.entity.AccountView;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = Money.class)
public interface AccountDataAccessMapper {

    @Mapping(target = "balance", source = "money.amount")
    @Mapping(target = "currency", source = "money.currency")
    @Mapping(target = "typeCode", source = "accountTypeCode")
    @Mapping(target = "accountTypeId", ignore = true)
    @Mapping(target = "newEntity", ignore = true)
    AccountEntity toAccountEntity(AccountView accountView);

    @Mapping(target = "money", expression = "java(new Money(accountEntity.getBalance(), accountEntity.getCurrency()))")
    @Mapping(target = "accountTypeCode", source = "typeCode")
    AccountView toAccountView(AccountEntity accountEntity);
}
