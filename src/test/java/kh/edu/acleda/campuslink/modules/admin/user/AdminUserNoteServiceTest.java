package kh.edu.acleda.campuslink.modules.admin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminUserNoteServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final Instant CREATED_AT = Instant.parse("2026-09-24T05:00:00Z");

    @Mock
    private UserRepository userRepository;
    @Mock
    private AdminUserRepository adminUserRepository;
    @Mock
    private AdminUserNoteRepository noteRepository;
    @Mock
    private AdminAuditService adminAuditService;

    private User user;
    private AdminUser admin;
    private AdminUserNoteService service;

    @BeforeEach
    void setUp() {
        user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        admin = AdminUser.builder().active(true).build();
        ReflectionTestUtils.setField(admin, "id", ADMIN_ID);
        service = new AdminUserNoteService(
            userRepository, adminUserRepository, noteRepository, adminAuditService);
        lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        lenient().when(adminUserRepository.findActiveByUserEmail("admin@example.com"))
            .thenReturn(Optional.of(admin));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("admin@example.com", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsAnAppendOnlyStaffNoteAndAuditsIt() {
        AdminUserNote saved = AdminUserNote.builder()
            .user(user)
            .adminUser(admin)
            .note("Reviewed the support conversation and attached evidence.")
            .build();
        ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(saved, "createdAt", CREATED_AT);
        when(noteRepository.save(any(AdminUserNote.class))).thenReturn(saved);

        AdminUserNoteResponse response = service.create(USER_ID,
            new AdminUserNoteRequest("Reviewed the support conversation and attached evidence."));

        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.adminUserId()).isEqualTo(ADMIN_ID);
        assertThat(response.note()).contains("Reviewed the support conversation");
        assertThat(response.createdAt()).isEqualTo(CREATED_AT);
        verify(adminAuditService).recordSuccess(
            "USER_NOTE_CREATE", "USER", USER_ID, "Internal staff note created");

        ArgumentCaptor<AdminUserNote> note = ArgumentCaptor.forClass(AdminUserNote.class);
        verify(noteRepository).save(note.capture());
        assertThat(note.getValue().getUser()).isSameAs(user);
        assertThat(note.getValue().getAdminUser()).isSameAs(admin);
    }

    @Test
    void listsOnlyNonDeletedNotesWithThePublishedPageContract() {
        AdminUserNote note = AdminUserNote.builder()
            .user(user).adminUser(admin).note("Internal context").build();
        PageRequest pageRequest = PageRequest.of(0, 25);
        when(noteRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(USER_ID, pageRequest))
            .thenReturn(new PageImpl<>(List.of(note), pageRequest, 1));

        PageResponse<AdminUserNoteResponse> response = service.list(USER_ID, 0, 25);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).note()).isEqualTo("Internal context");
        verify(noteRepository).findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(USER_ID, pageRequest);
    }

    @Test
    void rejectsBlankNotesAndUnsupportedPageSizes() {
        assertThatThrownBy(() -> service.create(USER_ID, new AdminUserNoteRequest("  ")))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", "VALIDATION_ERROR");
        assertThatThrownBy(() -> service.list(USER_ID, 0, 10))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", "VALIDATION_ERROR");
    }
}
