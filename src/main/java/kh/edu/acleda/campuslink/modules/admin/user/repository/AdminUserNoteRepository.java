package kh.edu.acleda.campuslink.modules.admin.user.repository;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.user.entity.AdminUserNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Staff-only note storage, always excluding soft-deleted notes. */
public interface AdminUserNoteRepository extends JpaRepository<AdminUserNote, UUID> {

    Page<AdminUserNote> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
        UUID userId,
        Pageable pageable);
}
