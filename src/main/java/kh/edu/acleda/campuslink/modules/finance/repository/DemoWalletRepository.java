package kh.edu.acleda.campuslink.modules.finance.repository;

import kh.edu.acleda.campuslink.modules.finance.entity.DemoWallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DemoWalletRepository extends JpaRepository<DemoWallet, UUID> {

    Optional<DemoWallet> findByUserId(UUID userId);

    long countByUserId(UUID userId);

    /** Serializes every balance mutation for one wallet. Must be called inside a transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM DemoWallet w WHERE w.id = :id")
    Optional<DemoWallet> findByIdForUpdate(@Param("id") UUID id);
}
