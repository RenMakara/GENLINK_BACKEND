package kh.edu.acleda.campuslink.modules.admin.user.repository;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.user.entity.AdminUserNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminUserNoteRepository extends JpaRepository<AdminUserNote, UUID> {

    /** The notes thread for one user, newest first. Staff-only; never served to the mobile API. */
    Page<AdminUserNote> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
