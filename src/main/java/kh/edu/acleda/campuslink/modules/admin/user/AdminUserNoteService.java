package kh.edu.acleda.campuslink.modules.admin.user;

import java.util.Set;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditService;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminUserNoteRequest;
import kh.edu.acleda.campuslink.modules.admin.user.dto.response.AdminUserNoteResponse;
import kh.edu.acleda.campuslink.modules.admin.user.entity.AdminUserNote;
import kh.edu.acleda.campuslink.modules.admin.user.repository.AdminUserNoteRepository;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.exception.UnauthorizedException;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Staff-only, append-only notes for an Admin user detail record (ADM-083). */
@Service
public class AdminUserNoteService {

    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(25, 50, 100);
    private static final String ACTION_CREATE = "USER_NOTE_CREATE";
    private static final String TARGET_TYPE_USER = "USER";

    private final UserRepository userRepository;
    private final AdminUserRepository adminUserRepository;
    private final AdminUserNoteRepository noteRepository;
    private final AdminAuditService adminAuditService;

    public AdminUserNoteService(
        UserRepository userRepository,
        AdminUserRepository adminUserRepository,
        AdminUserNoteRepository noteRepository,
        AdminAuditService adminAuditService) {
        this.userRepository = userRepository;
        this.adminUserRepository = adminUserRepository;
        this.noteRepository = noteRepository;
        this.adminAuditService = adminAuditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserNoteResponse> list(UUID userId, int page, int size) {
        requireUser(userId);
        Pageable pageable = pageRequest(page, size);
        return PageResponse.from(
            noteRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId, pageable),
            AdminUserNoteService::toResponse);
    }

    @Transactional
    public AdminUserNoteResponse create(UUID userId, AdminUserNoteRequest request) {
        String noteText = requireNote(request);
        User user = requireUser(userId);
        AdminUser actor = currentAdmin();

        AdminUserNote note = noteRepository.save(AdminUserNote.builder()
            .user(user)
            .adminUser(actor)
            .note(noteText)
            .build());

        adminAuditService.recordSuccess(
            ACTION_CREATE, TARGET_TYPE_USER, userId, "Internal staff note created");
        return toResponse(note);
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));
    }

    private AdminUser currentAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
            || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new UnauthorizedException("Your session is no longer valid.");
        }
        return adminUserRepository.findActiveByUserEmail(authentication.getName())
            .orElseThrow(() -> new UnauthorizedException("Your session is no longer valid."));
    }

    private static String requireNote(AdminUserNoteRequest request) {
        if (request == null || request.note() == null || request.note().trim().isEmpty()) {
            throw new ApiException("note: must not be blank", "VALIDATION_ERROR");
        }
        if (request.note().trim().length() > 4_000) {
            throw new ApiException("note: size must be between 1 and 4000", "VALIDATION_ERROR");
        }
        return request.note().trim();
    }

    private static Pageable pageRequest(int page, int size) {
        if (page < 0) {
            throw new ApiException("page must be zero or greater", "VALIDATION_ERROR");
        }
        if (!ALLOWED_PAGE_SIZES.contains(size)) {
            throw new ApiException("size must be one of 25, 50, or 100", "VALIDATION_ERROR");
        }
        return PageRequest.of(page, size);
    }

    private static AdminUserNoteResponse toResponse(AdminUserNote note) {
        return new AdminUserNoteResponse(
            note.getId(),
            note.getUser().getId(),
            note.getAdminUser().getId(),
            note.getNote(),
            note.getCreatedAt());
    }
}
