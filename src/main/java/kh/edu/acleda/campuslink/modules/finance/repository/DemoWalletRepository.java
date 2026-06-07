package kh.edu.acleda.campuslink.modules.finance.repository;

import kh.edu.acleda.campuslink.modules.finance.entity.DemoWallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DemoWalletRepository extends JpaRepository<DemoWallet, UUID> {}
