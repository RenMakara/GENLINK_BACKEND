package kh.edu.acleda.campuslink.modules.notification.service.impl;

import java.time.LocalDateTime;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.notification.dto.response.NotificationResponse;
import kh.edu.acleda.campuslink.modules.notification.entity.Notification;
import kh.edu.acleda.campuslink.modules.notification.repository.NotificationRepository;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationService;
import kh.edu.acleda.campuslink.shared.exception.ForbiddenException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final CurrentUserProvider currentUserProvider;

    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                   CurrentUserProvider currentUserProvider) {
        this.notificationRepository = notificationRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getMine(boolean unreadOnly, Pageable pageable) {
        UUID userId = currentUserProvider.getCurrentUserId();
        // Rows already store their rendered title/message + language, so no localization on read.
        var page = unreadOnly
            ? notificationRepository.findByUserIdAndReadStatusFalseOrderByCreatedAtDesc(userId, pageable)
            : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.from(page, this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount() {
        return notificationRepository.countByUserIdAndReadStatusFalse(currentUserProvider.getCurrentUserId());
    }

    @Override
    @Transactional
    public void markRead(UUID notificationId) {
        Notification notification = notificationRepository
            .findByIdAndUserId(notificationId, currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("Notification not found"));
        // Idempotent: only stamp the first time so read_at reflects when it was actually read.
        if (!Boolean.TRUE.equals(notification.getReadStatus())) {
            notification.setReadStatus(true);
            notification.setReadAt(LocalDateTime.now());
            notificationRepository.save(notification);
        }
    }

    @Override
    @Transactional
    public int markAllRead() {
        return notificationRepository.markAllReadForUser(
            currentUserProvider.getCurrentUserId(), LocalDateTime.now());
    }

    @Override
    @Transactional
    public void delete(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new NotFoundException("Notification not found"));
        UUID currentUserId = currentUserProvider.getCurrentUserId();
        if (notification.getUser() == null || !currentUserId.equals(notification.getUser().getId())) {
            throw new ForbiddenException("You do not have permission to delete this notification", "NOTIFICATION_FORBIDDEN");
        }
        notificationRepository.delete(notification);
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getTitle(), n.getMessage(), n.getLanguage(),
            n.getType(), n.getReferenceType(), n.getReferenceId(), n.getReadStatus(), n.getReadAt(),
            n.getCreatedAt());
    }
}
