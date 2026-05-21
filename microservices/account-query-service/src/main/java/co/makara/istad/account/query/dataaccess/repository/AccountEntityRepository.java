package co.makara.istad.account.query.dataaccess.repository;

import co.makara.istad.account.query.dataaccess.entity.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AccountEntityRepository extends JpaRepository<AccountEntity, UUID> {
}
