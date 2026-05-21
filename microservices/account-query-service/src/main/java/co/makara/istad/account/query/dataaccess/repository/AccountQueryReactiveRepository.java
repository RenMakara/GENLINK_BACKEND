package co.makara.istad.account.query.dataaccess.repository;

import co.makara.istad.account.query.dataaccess.entity.AccountEntity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;

import java.util.UUID;

public interface AccountQueryReactiveRepository extends R2dbcRepository<AccountEntity, UUID> {
}
