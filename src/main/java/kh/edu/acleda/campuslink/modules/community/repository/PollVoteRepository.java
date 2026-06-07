package kh.edu.acleda.campuslink.modules.community.repository;

import kh.edu.acleda.campuslink.modules.community.entity.PollVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PollVoteRepository extends JpaRepository<PollVote, UUID> {}
