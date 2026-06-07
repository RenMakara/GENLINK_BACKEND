package kh.edu.acleda.campuslink.modules.entertainment.repository;

import kh.edu.acleda.campuslink.modules.entertainment.entity.ShortContent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ShortContentRepository extends JpaRepository<ShortContent, UUID> {}
