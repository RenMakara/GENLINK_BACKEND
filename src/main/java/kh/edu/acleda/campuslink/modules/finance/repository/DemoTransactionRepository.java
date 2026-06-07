package kh.edu.acleda.campuslink.modules.finance.repository;

import kh.edu.acleda.campuslink.modules.finance.entity.DemoTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DemoTransactionRepository extends JpaRepository<DemoTransaction, UUID> {}
