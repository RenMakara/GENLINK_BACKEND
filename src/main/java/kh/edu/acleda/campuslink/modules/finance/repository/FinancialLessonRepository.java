package kh.edu.acleda.campuslink.modules.finance.repository;

import kh.edu.acleda.campuslink.modules.finance.entity.FinancialLesson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FinancialLessonRepository extends JpaRepository<FinancialLesson, UUID> {}
