package kh.edu.acleda.campuslink.modules.community.controller;

import java.util.Map;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.service.ChatService;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatService chatService;

    private ChatController controller;

    @BeforeEach
    void setUp() {
        controller = new ChatController(chatService);
    }

    @Test
    void deleteConversationDelegatesToDeleteConversationAndReturnsSuccess() {
        UUID roomId = UUID.randomUUID();

        ResponseEntity<ApiResponse<Void>> response = controller.deleteConversation(roomId);

        verify(chatService).deleteConversation(roomId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getMessage()).isEqualTo("Conversation deleted");
    }

    @Test
    void deleteConversationPropagatesNotFoundExceptionWhenRoomMissingOrNotActiveMember() {
        UUID roomId = UUID.randomUUID();
        doThrow(new NotFoundException("Chat not found")).when(chatService).deleteConversation(roomId);

        assertThatThrownBy(() -> controller.deleteConversation(roomId))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Chat not found");
    }

    @Test
    void leaveDelegatesToLeaveRoomAndReturnsSuccess() {
        UUID roomId = UUID.randomUUID();

        ResponseEntity<ApiResponse<Void>> response = controller.leave(roomId);

        verify(chatService).leaveRoom(roomId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getMessage()).isEqualTo("Left chat");
    }

    @Test
    void muteDelegatesToToggleMuteAndReturnsCurrentStatus() {
        UUID roomId = UUID.randomUUID();
        when(chatService.toggleMute(roomId)).thenReturn(true);

        ResponseEntity<ApiResponse<Map<String, Boolean>>> response = controller.mute(roomId);

        verify(chatService).toggleMute(roomId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getMessage()).isEqualTo("Chat muted");
        assertThat(response.getBody().getData()).containsEntry("isMuted", true);
    }

    @Test
    void unmuteDelegatesToUnmuteRoomAndReturnsSuccess() {
        UUID roomId = UUID.randomUUID();

        ResponseEntity<ApiResponse<Map<String, Boolean>>> response = controller.unmute(roomId);

        verify(chatService).unmuteRoom(roomId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getMessage()).isEqualTo("Chat unmuted");
        assertThat(response.getBody().getData()).containsEntry("isMuted", false);
    }

    @Test
    void archiveDelegatesToArchiveRoomAndReturnsSuccess() {
        UUID roomId = UUID.randomUUID();

        ResponseEntity<ApiResponse<Void>> response = controller.archive(roomId);

        verify(chatService).archiveRoom(roomId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getMessage()).isEqualTo("Chat archived");
    }

    @Test
    void unarchiveDelegatesToUnarchiveRoomAndReturnsSuccess() {
        UUID roomId = UUID.randomUUID();

        ResponseEntity<ApiResponse<Void>> response = controller.unarchive(roomId);

        verify(chatService).unarchiveRoom(roomId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getMessage()).isEqualTo("Chat unarchived");
    }
}
