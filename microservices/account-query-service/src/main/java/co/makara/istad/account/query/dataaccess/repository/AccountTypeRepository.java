package co.makara.istad.account.query.dataaccess.repository;

import co.istad.makara.common.domain.valueobject.AccountTypeCode;
import co.makara.istad.account.query.dataaccess.entity.AccountTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountTypeRepository extends JpaRepository<AccountTypeEntity, UUID> {
    Optional<AccountTypeEntity> findByAccountTypeCode(AccountTypeCode accountTypeCode);
}
