package co.istad.makara.customer.data.repository;

import co.istad.makara.customer.data.entity.CustomerSegmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
@Repository
public interface CustomerSegmentRepository extends
        JpaRepository<CustomerSegmentEntity, UUID> {

}
