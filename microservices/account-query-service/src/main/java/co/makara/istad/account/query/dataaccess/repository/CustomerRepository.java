package co.makara.istad.account.query.dataaccess.repository;

import co.makara.istad.account.query.dataaccess.entity.CustomerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CustomerRepository extends JpaRepository<CustomerEntity, UUID> {
}
