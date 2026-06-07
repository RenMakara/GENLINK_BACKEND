package kh.edu.acleda.campuslink.modules.entertainment.repository;

import kh.edu.acleda.campuslink.modules.entertainment.entity.XpHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface XpHistoryRepository extends JpaRepository<XpHistory, UUID> {}
