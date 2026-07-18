package kh.edu.acleda.campuslink.modules.profile.repository;

import kh.edu.acleda.campuslink.modules.profile.entity.PortfolioItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortfolioItemRepository extends JpaRepository<PortfolioItem, UUID> {

    List<PortfolioItem> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);

    Optional<PortfolioItem> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
}
