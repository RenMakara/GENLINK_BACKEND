package kh.edu.acleda.campuslink.modules.community.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kh.edu.acleda.campuslink.modules.community.dto.request.EventCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.CommunityMemberResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.EventResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.PostResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.RsvpResponse;
import kh.edu.acleda.campuslink.modules.community.entity.Event;
import kh.edu.acleda.campuslink.modules.community.entity.EventRsvp;
import kh.edu.acleda.campuslink.modules.community.enums.EventStatus;
import kh.edu.acleda.campuslink.modules.community.enums.RsvpStatus;
import kh.edu.acleda.campuslink.modules.community.repository.EventRepository;
import kh.edu.acleda.campuslink.modules.community.repository.EventRsvpRepository;
import kh.edu.acleda.campuslink.modules.community.service.EventService;
import kh.edu.acleda.campuslink.modules.entertainment.entity.XpHistory;
import kh.edu.acleda.campuslink.modules.entertainment.repository.XpHistoryRepository;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoTransaction;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoWallet;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionStatus;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionType;
import kh.edu.acleda.campuslink.modules.finance.repository.DemoTransactionRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.DemoWalletRepository;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
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

    private final EventRepository eventRepository;
    private final EventRsvpRepository rsvpRepository;
    private final DemoWalletRepository demoWalletRepository;
    private final DemoTransactionRepository demoTransactionRepository;
    private final XpHistoryRepository xpHistoryRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public EventServiceImpl(EventRepository eventRepository,
                            EventRsvpRepository rsvpRepository,
                            DemoWalletRepository demoWalletRepository,
                            DemoTransactionRepository demoTransactionRepository,
                            XpHistoryRepository xpHistoryRepository,
                            UserRepository userRepository,
                            CurrentUserProvider currentUserProvider) {
        this.eventRepository = eventRepository;
        this.rsvpRepository = rsvpRepository;
        this.demoWalletRepository = demoWalletRepository;
        this.demoTransactionRepository = demoTransactionRepository;
        this.xpHistoryRepository = xpHistoryRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional
    public EventResponse create(EventCreateRequest request) {
        validatePricing(request);
        User organizer = loadCurrentUser();
        Event event = eventRepository.save(Event.builder()
            .organizer(organizer)
            .title(request.title().strip())
            .description(request.description())
            .category(request.category())
            .coverImageUrl(request.coverImageUrl())
            .dateTime(request.dateTime())
            .locationName(request.locationName())
            .attendeeLimit(request.attendeeLimit())
            .attendeeCount(0)
            .status(request.status() == null ? EventStatus.PUBLISHED : request.status())
            .language(request.language())
            .isPaid(Boolean.TRUE.equals(request.paid()))
            .price(Boolean.TRUE.equals(request.paid()) ? request.price() : null)
            .build());
        return toResponse(event, null);
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
        Event event = loadEvent(eventId);
        return toResponse(event, myRsvpStatus(eventId));
    }

    @Override
    @Transactional
    public EventResponse update(UUID eventId, EventCreateRequest request) {
        validatePricing(request);
        Event event = loadOwned(eventId);
        event.setTitle(request.title().strip());
        event.setDescription(request.description());
        event.setCategory(request.category());
        event.setCoverImageUrl(request.coverImageUrl());
        event.setDateTime(request.dateTime());
        event.setLocationName(request.locationName());
        event.setAttendeeLimit(request.attendeeLimit());
        if (request.status() != null) {
            event.setStatus(request.status());
        }
        if (request.language() != null) {
            event.setLanguage(request.language());
        }
        event.setIsPaid(Boolean.TRUE.equals(request.paid()));
        event.setPrice(Boolean.TRUE.equals(request.paid()) ? request.price() : null);
        return toResponse(eventRepository.save(event), myRsvpStatus(eventId));
    }

    @Override
    @Transactional
    public void delete(UUID eventId) {
        Event event = loadOwned(eventId);
        event.setDeletedAt(Instant.now());
        eventRepository.save(event);

        // attendeeCount is the blast radius, and on a paid event those people were
        // charged with no refund path. If anyone ever asks "where did my money go",
        // this line is the trail.
        log.info("Event soft-deleted: id={} attendees={} paid={} by user={}",
            eventId, event.getAttendeeCount(), Boolean.TRUE.equals(event.getIsPaid()),
            currentUserProvider.getCurrentUserId());
    }

    @Override
    @Transactional
    public RsvpResponse rsvp(UUID eventId) {
        Event event = loadEvent(eventId);
        UUID userId = currentUserProvider.getCurrentUserId();

        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new ApiException("This event has been cancelled", "EVENT_CANCELLED");
        }
        if (event.getDateTime().isBefore(LocalDateTime.now())) {
            throw new ApiException("This event has already taken place", "EVENT_PAST");
        }

        EventRsvp existing = rsvpRepository.findByEventIdAndUserId(eventId, userId).orElse(null);
        if (existing != null && existing.getStatus() == RsvpStatus.JOINED) {
            // Idempotent: RSVP backs a button, and (event_id, user_id) is unique.
            return new RsvpResponse(eventId, RsvpStatus.JOINED, event.getAttendeeCount(), 0, null, false);
        }

        // Capacity is checked BEFORE payment. The other order takes someone's money
        // and then tells them the event is full.
        if (isFull(event)) {
            throw new ApiException("Event is full", "EVENT_FULL");
        }

        BigDecimal charged = settlePayment(event, userId);

        if (existing != null) {
            // The unique constraint forbids a second row, so a cancelled RSVP is reused.
            existing.setStatus(RsvpStatus.JOINED);
            rsvpRepository.save(existing);
        } else {
            rsvpRepository.save(EventRsvp.builder()
                .event(event)
                .user(loadCurrentUser())
                .status(RsvpStatus.JOINED)
                .checkedIn(false)
                .build());
        }
        eventRepository.adjustAttendeeCount(eventId, 1);
        int xp = awardXp(eventId);

        return new RsvpResponse(eventId, RsvpStatus.JOINED, event.getAttendeeCount() + 1, xp,
            charged, charged != null);
    }

    @Override
    @Transactional
    public void cancelRsvp(UUID eventId) {
        UUID userId = currentUserProvider.getCurrentUserId();
        EventRsvp rsvp = rsvpRepository.findByEventIdAndUserId(eventId, userId)
            .orElseThrow(() -> new NotFoundException("RSVP not found"));
        if (rsvp.getStatus() != RsvpStatus.JOINED) {
            return;
        }
        // Deliberately no refund: the demo wallet has no refund flow, and inventing one
        // would imply a payment rail that does not exist.
        rsvp.setStatus(RsvpStatus.CANCELLED);
        rsvpRepository.save(rsvp);
        eventRepository.adjustAttendeeCount(eventId, -1);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommunityMemberResponse> attendees(UUID eventId, Pageable pageable) {
        loadEvent(eventId);
        return PageResponse.from(rsvpRepository.findAttendees(eventId, RsvpStatus.JOINED, pageable),
            rsvp -> new CommunityMemberResponse(summary(rsvp.getUser()), null, rsvp.getCreatedAt()));
    }

    /**
     * Simulated payment. Deducts the demo wallet and records a demo_transaction; there
     * is no real KHQR transfer anywhere in this path.
     *
     * @return the amount charged, or null when the event is free
     */
    private BigDecimal settlePayment(Event event, UUID userId) {
        if (!Boolean.TRUE.equals(event.getIsPaid()) || event.getPrice() == null) {
            return null;
        }
        DemoWallet wallet = demoWalletRepository.findByUserId(userId)
            .orElseThrow(() -> new ApiException("Demo wallet not found. Open the wallet first.",
                "DEMO_WALLET_NOT_FOUND"));

        BigDecimal price = event.getPrice();
        if (wallet.getBalance() == null || wallet.getBalance().compareTo(price) < 0) {
            throw new ApiException("Demo wallet balance is too low for this event",
                "DEMO_WALLET_INSUFFICIENT_BALANCE");
        }

        wallet.setBalance(wallet.getBalance().subtract(price));
        demoWalletRepository.save(wallet);
        demoTransactionRepository.save(DemoTransaction.builder()
            .wallet(wallet)
            .transactionType(TransactionType.EVENT_PAYMENT)
            .amount(price)
            .description("Simulated payment for event: " + event.getTitle())
            .status(TransactionStatus.SUCCESS)
            .build());

        // The only place a balance moves outside the finance module. It is simulated,
        // but "my wallet is wrong" is otherwise untraceable: the success path never
        // reaches GlobalExceptionHandler, so nothing else would record it. Says
        // SIMULATED out loud so a log reader never mistakes it for a real payment.
        log.info("SIMULATED wallet debit: user={} event={} amount={} balanceAfter={}",
            userId, event.getId(), price, wallet.getBalance());
        return price;
    }

    private int awardXp(UUID eventId) {
        User user = loadCurrentUser();
        user.setTotalXp(user.getTotalXp() + RSVP_XP);
        userRepository.save(user);
        xpHistoryRepository.save(XpHistory.builder()
            .user(user)
            .sourceType(XP_SOURCE)
            .sourceId(eventId)
            .xpAmount(RSVP_XP)
            .build());
        return RSVP_XP;
    }

    private boolean isFull(Event event) {
        return event.getAttendeeLimit() != null && event.getAttendeeCount() >= event.getAttendeeLimit();
    }

    /** Resolves myRsvpStatus for the whole page in one query rather than per row. */
    private PageResponse<EventResponse> hydrate(Page<Event> page) {
        List<Event> events = page.getContent();
        if (events.isEmpty()) {
            return PageResponse.from(page, event -> toResponse(event, null));
        }
        Set<UUID> ids = events.stream().map(Event::getId).collect(Collectors.toSet());
        Map<UUID, RsvpStatus> mine = new HashMap<>();
        rsvpRepository.findByUserIdAndEventIdIn(currentUserProvider.getCurrentUserId(), ids)
            .forEach(rsvp -> mine.put(rsvp.getEvent().getId(), rsvp.getStatus()));
        return PageResponse.from(page, event -> toResponse(event, mine.get(event.getId())));
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
    }

    private Event loadEvent(UUID eventId) {
        return eventRepository.findByIdAndDeletedAtIsNull(eventId)
            .orElseThrow(() -> new NotFoundException("Event not found"));
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

    private EventResponse toResponse(Event event, RsvpStatus myRsvpStatus) {
        return new EventResponse(
            event.getId(),
            summary(event.getOrganizer()),
            event.getTitle(),
            event.getDescription(),
            event.getCategory(),
            event.getCoverImageUrl(),
            event.getDateTime(),
            event.getLocationName(),
            event.getAttendeeLimit(),
            event.getAttendeeCount(),
            event.getStatus(),
            event.getLanguage(),
            Boolean.TRUE.equals(event.getIsPaid()),
            event.getPrice(),
            myRsvpStatus,
            isFull(event),
            event.getCreatedAt()
        );
    }
}
