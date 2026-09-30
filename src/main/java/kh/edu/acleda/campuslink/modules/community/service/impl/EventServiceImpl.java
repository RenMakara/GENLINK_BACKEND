package kh.edu.acleda.campuslink.modules.community.service.impl;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kh.edu.acleda.campuslink.modules.community.dto.request.EventCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.EventRegistrationRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.CommunityMemberResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.EventResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.PostResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.RsvpResponse;
import kh.edu.acleda.campuslink.modules.community.entity.Event;
import kh.edu.acleda.campuslink.modules.community.entity.EventRsvp;
import kh.edu.acleda.campuslink.modules.community.enums.EventStatus;
import kh.edu.acleda.campuslink.modules.community.enums.EventMode;
import kh.edu.acleda.campuslink.modules.community.enums.RsvpStatus;
import kh.edu.acleda.campuslink.modules.community.repository.EventRepository;
import kh.edu.acleda.campuslink.modules.community.repository.EventRsvpRepository;
import kh.edu.acleda.campuslink.modules.community.service.EventService;
import kh.edu.acleda.campuslink.modules.community.service.EventMeetingUrlAccess;
import kh.edu.acleda.campuslink.modules.community.service.EventVisibility;
import kh.edu.acleda.campuslink.modules.entertainment.service.RewardService;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoWallet;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoTransaction;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionType;
import kh.edu.acleda.campuslink.modules.finance.repository.DemoWalletRepository;
import kh.edu.acleda.campuslink.modules.finance.service.LedgerService;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationChannel;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.modules.user.entity.WorkStatus;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class EventServiceImpl implements EventService {

    private static final int RSVP_XP = 10;
    private static final String XP_SOURCE = "EVENT_RSVP";
    private static final String RSVP_NOTIFICATION_TEMPLATE = "COMMUNITY_EVENT_RSVP_ORGANIZER";
    private static final String RSVP_NOTIFICATION_TYPE = "COMMUNITY_EVENT_RSVP";
    private static final String PAYMENT_RECEIPT_TEMPLATE = "COMMUNITY_EVENT_PAYMENT_RECEIPT";
    private static final String PAYMENT_RECEIPT_TYPE = "COMMUNITY_EVENT_PAYMENT";

    private final EventRepository eventRepository;
    private final EventRsvpRepository rsvpRepository;
    private final DemoWalletRepository demoWalletRepository;
    private final LedgerService ledgerService;
    private final RewardService rewardService;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final NotificationEmitter notificationEmitter;

    public EventServiceImpl(EventRepository eventRepository,
                            EventRsvpRepository rsvpRepository,
                            DemoWalletRepository demoWalletRepository,
                            LedgerService ledgerService,
                            RewardService rewardService,
                            UserRepository userRepository,
                            CurrentUserProvider currentUserProvider,
                            NotificationEmitter notificationEmitter) {
        this.eventRepository = eventRepository;
        this.rsvpRepository = rsvpRepository;
        this.demoWalletRepository = demoWalletRepository;
        this.ledgerService = ledgerService;
        this.rewardService = rewardService;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
        this.notificationEmitter = notificationEmitter;
    }

    @Override
    @Transactional
    public EventResponse create(EventCreateRequest request) {
        validatePricing(request);
        validateEventDetails(request);
        User organizer = loadCurrentUser();
        Event event = eventRepository.save(Event.builder()
            .organizer(organizer)
            .title(request.title().strip())
            .description(request.description())
            .category(request.category())
            .coverImageUrl(request.coverImageUrl())
            .dateTime(request.startDateTime())
            .endDateTime(request.endDateTime())
            .mode(modeOrDefault(request))
            .meetingUrl(normalizeMeetingUrl(request))
            .locationName(normalizeLocation(request))
            .latitude(request.latitude())
            .longitude(request.longitude())
            .attendeeLimit(request.attendeeLimit())
            .attendeeCount(0)
            .status(request.status() == null ? EventStatus.PUBLISHED : request.status())
            .language(request.language())
            .isPaid(Boolean.TRUE.equals(request.paid()))
            .price(Boolean.TRUE.equals(request.paid()) ? request.price() : null)
            .currency(Boolean.TRUE.equals(request.paid()) ? request.currency() : null)
            .build());
        return toResponse(event, null, true);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EventResponse> browse(String category, Pageable pageable) {
        return hydrate(eventRepository.findUpcoming(
            EventStatus.PUBLISHED, LocalDateTime.now(), category, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EventResponse> getMine(Pageable pageable) {
        return hydrate(eventRepository.findByOrganizer(currentUserProvider.getCurrentUserId(), pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public EventResponse getById(UUID eventId) {
        UUID viewerId = currentUserProvider.getCurrentUserId();
        Event event = loadVisibleEvent(eventId, viewerId);
        RsvpStatus myRsvpStatus = rsvpRepository.findByEventIdAndUserId(eventId, viewerId)
            .map(EventRsvp::getStatus)
            .orElse(null);
        return toResponse(event, myRsvpStatus,
            EventMeetingUrlAccess.canViewMeetingUrl(event, viewerId, myRsvpStatus));
    }

    @Override
    @Transactional
    public EventResponse update(UUID eventId, EventCreateRequest request) {
        validatePricing(request);
        validateEventDetails(request);
        Event event = loadOwned(eventId);
        event.setTitle(request.title().strip());
        event.setDescription(request.description());
        event.setCategory(request.category());
        event.setCoverImageUrl(request.coverImageUrl());
        event.setDateTime(request.startDateTime());
        event.setEndDateTime(request.endDateTime());
        event.setMode(modeOrDefault(request));
        event.setMeetingUrl(normalizeMeetingUrl(request));
        event.setLocationName(normalizeLocation(request));
        event.setLatitude(request.latitude());
        event.setLongitude(request.longitude());
        event.setAttendeeLimit(request.attendeeLimit());
        if (request.status() != null) {
            event.setStatus(request.status());
        }
        if (request.language() != null) {
            event.setLanguage(request.language());
        }
        event.setIsPaid(Boolean.TRUE.equals(request.paid()));
        event.setPrice(Boolean.TRUE.equals(request.paid()) ? request.price() : null);
        event.setCurrency(Boolean.TRUE.equals(request.paid()) ? request.currency() : null);
        return toResponse(eventRepository.save(event), myRsvpStatus(eventId), true);
    }

    @Override
    @Transactional
    public void delete(UUID eventId) {
        Event event = loadOwned(eventId);
        event.setDeletedAt(Instant.now());
        eventRepository.save(event);

        // attendeeCount is the blast radius, and on a paid event those people were charged with no refund path.
        log.info("Event soft-deleted: id={} attendees={} paid={} by user={}",
            eventId, event.getAttendeeCount(), Boolean.TRUE.equals(event.getIsPaid()),
            currentUserProvider.getCurrentUserId());
    }

    @Override
    @Transactional
    public RsvpResponse rsvp(UUID eventId) {
        return rsvp(eventId, null);
    }

    @Override
    @Transactional
    public RsvpResponse rsvp(UUID eventId, EventRegistrationRequest registration) {
        User attendee = loadCurrentUser();
        // Serialize all RSVP transitions for this event.
        Event event = loadEventForRsvp(eventId);
        UUID userId = attendee.getId();

        // Drafts are visible to their organizer, but normal attendee registration is published-only.
        if (!EventVisibility.canRsvp(event, userId)) {
            throw new NotFoundException("Event not found");
        }

        validateRegistration(registration, attendee);
        if (event.getDateTime().isBefore(LocalDateTime.now())) {
            throw new ApiException("This event has already taken place", "EVENT_PAST");
        }

        EventRsvp existing = rsvpRepository.findByEventIdAndUserId(eventId, userId).orElse(null);
        if (existing != null && existing.getStatus() == RsvpStatus.JOINED) {
            // Idempotent: RSVP backs a button, and (event_id, user_id) is unique.
            if (registration != null) {
                applyRegistration(existing, registration);
                rsvpRepository.save(existing);
            }
            return new RsvpResponse(eventId, RsvpStatus.JOINED, event.getAttendeeCount(), 0, null, false);
        }

        // Reserve the seat atomically BEFORE payment, and before anything else that could charge or reward.
        if (eventRepository.reserveSeat(eventId) == 0) {
            throw new ApiException("Event is full", "EVENT_FULL");
        }

        PaymentSettlement payment = settlePayment(event, userId);

        if (existing != null) {
            // The unique constraint forbids a second row, so a cancelled RSVP is reused.
            existing.setStatus(RsvpStatus.JOINED);
            applyRegistration(existing, registration);
            rsvpRepository.save(existing);
        } else {
            EventRsvp created = EventRsvp.builder()
                .event(event)
                .user(attendee)
                .status(RsvpStatus.JOINED)
                .checkedIn(false)
                .build();
            applyRegistration(created, registration);
            rsvpRepository.save(created);
        }
        int xp = awardXp(eventId, attendee);
        emitRsvpNotification(event, attendee, registration);
        emitPaymentReceiptNotification(event, attendee, payment);

        return new RsvpResponse(eventId, RsvpStatus.JOINED, event.getAttendeeCount() + 1, xp,
            payment == null ? null : payment.amount(), payment != null);
    }

    @Override
    @Transactional
    public void cancelRsvp(UUID eventId) {
        UUID userId = currentUserProvider.getCurrentUserId();
        // Lock before reading the RSVP so two cancellations cannot both observe JOINED and both release the same seat.
        eventRepository.findByIdForUpdate(eventId);
        EventRsvp rsvp = rsvpRepository.findByEventIdAndUserId(eventId, userId)
            .orElseThrow(() -> new NotFoundException("RSVP not found"));
        if (rsvp.getStatus() != RsvpStatus.JOINED) {
            return;
        }
        // Deliberately no refund: the demo wallet has no refund flow, and inventing one
        // would imply a payment rail that does not exist.
        rsvp.setStatus(RsvpStatus.CANCELLED);
        rsvpRepository.save(rsvp);
        eventRepository.releaseSeat(eventId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommunityMemberResponse> attendees(UUID eventId, Pageable pageable) {
        // Follow the existing owner-scoped Event convention.
        loadOwned(eventId);
        return PageResponse.from(rsvpRepository.findAttendees(eventId, RsvpStatus.JOINED, pageable),
            rsvp -> new CommunityMemberResponse(summary(rsvp.getUser()), null, rsvp.getCreatedAt()));
    }

    /** Simulated payment. */
    private PaymentSettlement settlePayment(Event event, UUID userId) {
        if (!Boolean.TRUE.equals(event.getIsPaid()) || event.getPrice() == null) {
            return null;
        }
        DemoWallet wallet = demoWalletRepository.findByUserId(userId)
            .orElseThrow(() -> new ApiException("Demo wallet not found. Open the wallet first.",
                "DEMO_WALLET_NOT_FOUND"));

        BigDecimal price = event.getPrice();
        // debit enforces sufficient funds (throws DEMO_WALLET_INSUFFICIENT_BALANCE before any write).
        Currency currency = event.getCurrency();
        if (currency == null) {
            // Protect legacy or manually-created rows that predate event currency.
            throw new ApiException("Paid event currency is missing", "EVENT_CURRENCY_MISSING");
        }
        DemoTransaction transaction = ledgerService.debit(wallet, TransactionType.EVENT_PAYMENT, currency, price,
            "Simulated payment for event: " + event.getTitle(), "EVENT", event.getId());

        // The only place a balance moves outside the finance module.
        log.info("SIMULATED wallet debit: user={} event={} currency={} amount={} balanceAfter={}",
            userId, event.getId(), currency, price, balanceFor(wallet, currency));
        return new PaymentSettlement(price, transaction == null ? null : transaction.getId());
    }

    private BigDecimal balanceFor(DemoWallet wallet, Currency currency) {
        return currency == Currency.KHR ? wallet.getBalanceKhr() : wallet.getBalanceUsd();
    }

    private int awardXp(UUID eventId, User attendee) {
        rewardService.award(attendee, XP_SOURCE, eventId, RSVP_XP);
        return RSVP_XP;
    }

    private void emitPaymentReceiptNotification(Event event, User attendee, PaymentSettlement payment) {
        if (payment == null) {
            return;
        }
        if (payment.transactionId() == null || attendee == null || attendee.getId() == null
            || event.getId() == null || event.getTitle() == null || event.getTitle().isBlank()
            || event.getCurrency() == null) {
            log.warn("Skipping event payment receipt because payment/event details are incomplete: event={} user={}",
                event.getId(), attendee == null ? null : attendee.getId());
            return;
        }

        notificationEmitter.emit(new NotificationRequest(
            attendee.getId(),
            PAYMENT_RECEIPT_TEMPLATE,
            PAYMENT_RECEIPT_TYPE,
            Map.of(
                "amount", formatAmount(payment.amount()),
                "currency", event.getCurrency().name(),
                "eventTitle", event.getTitle()),
            "EVENT",
            event.getId(),
            "event-payment-receipt:" + payment.transactionId()));
    }

    private String formatAmount(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }

    /** Notify the organiser only for a newly joined RSVP, not for an idempotent button re-tap. */
    private void emitRsvpNotification(Event event,
                                      User attendee,
                                      EventRegistrationRequest registration) {
        User organizer = event.getOrganizer();
        if (organizer == null || organizer.getId() == null || organizer.getId().equals(attendee.getId())) {
            return;
        }
        String attendeeName = registration == null ? attendee.getFullName() : registration.fullName().strip();
        if (isBlank(attendeeName)) {
            attendeeName = !isBlank(attendee.getUsername()) ? attendee.getUsername() : attendee.getEmail();
        }
        if (isBlank(attendeeName) || isBlank(event.getTitle()) || event.getId() == null) {
            log.warn("Skipping RSVP notification because attendee/event details are incomplete: event={} user={}",
                event.getId(), attendee.getId());
            return;
        }

        notificationEmitter.emit(new NotificationRequest(
            organizer.getId(),
            RSVP_NOTIFICATION_TEMPLATE,
            RSVP_NOTIFICATION_TYPE,
            Map.of("attendeeName", attendeeName, "eventTitle", event.getTitle()),
            "EVENT",
            event.getId(),
            "event-rsvp:" + event.getId() + ":" + attendee.getId(),
            Set.of(NotificationChannel.IN_APP, NotificationChannel.MOBILE_PUSH)));
    }

    private record PaymentSettlement(BigDecimal amount, UUID transactionId) {
    }

    private boolean isFull(Event event) {
        return event.getAttendeeLimit() != null && event.getAttendeeCount() >= event.getAttendeeLimit();
    }

    /** Resolves myRsvpStatus for the whole page in one query rather than per row. */
    private PageResponse<EventResponse> hydrate(Page<Event> page) {
        List<Event> events = page.getContent();
        if (events.isEmpty()) {
            return PageResponse.from(page, event -> toResponse(event, null, false));
        }
        Set<UUID> ids = events.stream().map(Event::getId).collect(Collectors.toSet());
        Map<UUID, RsvpStatus> mine = new HashMap<>();
        UUID viewerId = currentUserProvider.getCurrentUserId();
        rsvpRepository.findByUserIdAndEventIdIn(viewerId, ids)
            .forEach(rsvp -> mine.put(rsvp.getEvent().getId(), rsvp.getStatus()));
        return PageResponse.from(page, event -> {
            RsvpStatus myRsvpStatus = mine.get(event.getId());
            return toResponse(event, myRsvpStatus,
                EventMeetingUrlAccess.canViewMeetingUrl(event, viewerId, myRsvpStatus));
        });
    }

    private RsvpStatus myRsvpStatus(UUID eventId) {
        return rsvpRepository.findByEventIdAndUserId(eventId, currentUserProvider.getCurrentUserId())
            .map(EventRsvp::getStatus)
            .orElse(null);
    }

    /** Backs up chk_events_paid_requires_price with a readable error. */
    private void validatePricing(EventCreateRequest request) {
        if (Boolean.TRUE.equals(request.paid())
            && (request.price() == null || request.price().signum() <= 0)) {
            throw new ApiException("A paid event needs a price above zero", "VALIDATION_ERROR");
        }
        if (Boolean.TRUE.equals(request.paid()) && request.currency() == null) {
            throw new ApiException("A paid event needs a currency", "VALIDATION_ERROR");
        }
    }

    private void validateEventDetails(EventCreateRequest request) {
        if (request.startDateTime() == null || request.endDateTime() == null
            || !request.endDateTime().isAfter(request.startDateTime())) {
            throw new ApiException("Event end time must be after start time", "VALIDATION_ERROR");
        }
        if (request.latitude() == null ^ request.longitude() == null) {
            throw new ApiException("Latitude and longitude must be provided together", "VALIDATION_ERROR");
        }
        if ((request.latitude() != null
                && (request.latitude().compareTo(BigDecimal.valueOf(-90)) < 0
                    || request.latitude().compareTo(BigDecimal.valueOf(90)) > 0))
            || (request.longitude() != null
                && (request.longitude().compareTo(BigDecimal.valueOf(-180)) < 0
                    || request.longitude().compareTo(BigDecimal.valueOf(180)) > 0))) {
            throw new ApiException("Map coordinates are out of range", "VALIDATION_ERROR");
        }

        if (request.mode() == EventMode.ONLINE) {
            if (request.meetingUrl() == null || request.meetingUrl().isBlank()) {
                throw new ApiException("An online event needs a meeting URL", "VALIDATION_ERROR");
            }
            try {
                URI uri = new URI(request.meetingUrl().trim());
                if (!"http".equalsIgnoreCase(uri.getScheme())
                    && !"https".equalsIgnoreCase(uri.getScheme())) {
                    throw new ApiException("Meeting URL must use http or https", "VALIDATION_ERROR");
                }
                if (uri.getHost() == null) {
                    throw new ApiException("Meeting URL is invalid", "VALIDATION_ERROR");
                }
            } catch (URISyntaxException ex) {
                throw new ApiException("Meeting URL is invalid", "VALIDATION_ERROR");
            }
        } else if (request.mode() == EventMode.IN_PERSON
            && (request.locationName() == null || request.locationName().isBlank())) {
            throw new ApiException("An in-person event needs a location", "VALIDATION_ERROR");
        }
    }

    private EventMode modeOrDefault(EventCreateRequest request) {
        return request.mode() == null ? EventMode.IN_PERSON : request.mode();
    }

    private String normalizeMeetingUrl(EventCreateRequest request) {
        return request.mode() == EventMode.ONLINE && request.meetingUrl() != null
            ? request.meetingUrl().trim() : null;
    }

    private String normalizeLocation(EventCreateRequest request) {
        return request.mode() == EventMode.ONLINE ? null : request.locationName();
    }

    private void validateRegistration(EventRegistrationRequest registration, User attendee) {
        if (registration == null) {
            throw new ApiException("Complete event registration is required", "VALIDATION_ERROR");
        }
        if (!registration.termsAccepted()) {
            throw new ApiException("You must accept the event terms", "VALIDATION_ERROR");
        }
        if (isBlank(registration.fullName()) || isBlank(registration.email())
            || isBlank(registration.phoneNumber()) || isBlank(registration.university())
            || isBlank(registration.faculty())) {
            throw new ApiException("Complete event registration is required", "VALIDATION_ERROR");
        }
        if (WorkStatus.STUDENT.equals(attendee.getWorkStatus()) && isBlank(registration.studyYear())) {
            throw new ApiException("Study year is required for student registration", "VALIDATION_ERROR");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void applyRegistration(EventRsvp rsvp, EventRegistrationRequest registration) {
        if (registration == null) {
            return;
        }
        rsvp.setFullName(registration.fullName().strip());
        rsvp.setEmail(registration.email().strip());
        rsvp.setPhoneNumber(registration.phoneNumber().strip());
        rsvp.setUniversity(registration.university().strip());
        rsvp.setFaculty(registration.faculty().strip());
        rsvp.setStudyYear(trimToNull(registration.studyYear()));
        rsvp.setTermsAccepted(true);
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.strip();
    }

    private Event loadEvent(UUID eventId) {
        return eventRepository.findByIdAndDeletedAtIsNull(eventId)
            .orElseThrow(() -> new NotFoundException("Event not found"));
    }

    private Event loadEventForRsvp(UUID eventId) {
        return eventRepository.findByIdAndDeletedAtIsNullForUpdate(eventId)
            .orElseThrow(() -> new NotFoundException("Event not found"));
    }

    private Event loadVisibleEvent(UUID eventId, UUID viewerId) {
        Event event = loadEvent(eventId);
        if (!EventVisibility.canView(event, viewerId)) {
            throw new NotFoundException("Event not found");
        }
        return event;
    }

    private Event loadOwned(UUID eventId) {
        return eventRepository
            .findByIdAndOrganizerIdAndDeletedAtIsNull(eventId, currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("Event not found"));
    }

    private User loadCurrentUser() {
        return userRepository.findById(currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private PostResponse.AuthorSummary summary(User user) {
        return user == null ? null : new PostResponse.AuthorSummary(
            user.getId(), user.getFullName(), user.getUsername(), user.getAvatarUrl());
    }

    private EventResponse toResponse(Event event, RsvpStatus myRsvpStatus, boolean canViewMeetingUrl) {
        return new EventResponse(
            event.getId(),
            summary(event.getOrganizer()),
            event.getTitle(),
            event.getDescription(),
            event.getCategory(),
            event.getCoverImageUrl(),
            event.getDateTime(),
            event.getEndDateTime(),
            event.getMode(),
            canViewMeetingUrl ? event.getMeetingUrl() : null,
            event.getLocationName(),
            event.getLatitude(),
            event.getLongitude(),
            event.getAttendeeLimit(),
            event.getAttendeeCount(),
            event.getStatus(),
            event.getLanguage(),
            Boolean.TRUE.equals(event.getIsPaid()),
            event.getPrice(),
            event.getCurrency(),
            myRsvpStatus,
            isFull(event),
            event.getCreatedAt()
        );
    }
}
