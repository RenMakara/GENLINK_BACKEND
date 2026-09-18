package kh.edu.acleda.campuslink.modules.notification.controller;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationService;
import kh.edu.acleda.campuslink.shared.exception.ForbiddenException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    private NotificationController controller;

    @BeforeEach
    void setUp() {
        controller = new NotificationController(notificationService);
    }

    @Test
    void deleteDelegatesToServiceAndReturnsSuccess() {
        UUID id = UUID.randomUUID();

        ResponseEntity<ApiResponse<Void>> response = controller.delete(id);

        verify(notificationService).delete(id);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getMessage()).isEqualTo("Notification deleted");
    }

    @Test
    void deletePropagatesNotFoundExceptionWhenMissing() {
        UUID id = UUID.randomUUID();
        doThrow(new NotFoundException("Notification not found"))
            .when(notificationService).delete(id);

        assertThatThrownBy(() -> controller.delete(id))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Notification not found");
    }

    @Test
    void deletePropagatesForbiddenExceptionWhenNotOwned() {
        UUID id = UUID.randomUUID();
        doThrow(new ForbiddenException("You do not have permission to delete this notification", "NOTIFICATION_FORBIDDEN"))
            .when(notificationService).delete(id);

        assertThatThrownBy(() -> controller.delete(id))
            .isInstanceOf(ForbiddenException.class)
            .hasFieldOrPropertyWithValue("errorCode", "NOTIFICATION_FORBIDDEN");
    }
}
