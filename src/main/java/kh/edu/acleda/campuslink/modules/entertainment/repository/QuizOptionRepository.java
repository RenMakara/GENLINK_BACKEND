package kh.edu.acleda.campuslink.modules.entertainment.repository;

import kh.edu.acleda.campuslink.modules.entertainment.entity.QuizOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface QuizOptionRepository extends JpaRepository<QuizOption, UUID> {}
