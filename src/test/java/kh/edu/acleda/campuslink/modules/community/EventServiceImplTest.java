package kh.edu.acleda.campuslink.modules.community;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.dto.request.EventCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.EventRegistrationRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.RsvpResponse;
import kh.edu.acleda.campuslink.modules.community.entity.Event;
import kh.edu.acleda.campuslink.modules.community.entity.EventRsvp;
import kh.edu.acleda.campuslink.modules.community.enums.EventStatus;
import kh.edu.acleda.campuslink.modules.community.enums.EventMode;
import kh.edu.acleda.campuslink.modules.community.enums.RsvpStatus;
import kh.edu.acleda.campuslink.modules.community.repository.EventRepository;
import kh.edu.acleda.campuslink.modules.community.repository.EventRsvpRepository;
import kh.edu.acleda.campuslink.modules.community.service.impl.EventServiceImpl;
import kh.edu.acleda.campuslink.modules.entertainment.service.RewardService;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoTransaction;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoWallet;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionDirection;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionType;
import kh.edu.acleda.campuslink.modules.finance.repository.DemoWalletRepository;
import kh.edu.acleda.campuslink.modules.finance.service.LedgerService;
import kh.edu.acleda.campuslink.modules.notification.event.NotificationEventListener;
import kh.edu.acleda.campuslink.modules.notification.event.NotificationRequestedEvent;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationChannel;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationWriter;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID ORGANIZER_ID = UUID.randomUUID();

    @Mock private EventRepository eventRepository;
    @Mock private EventRsvpRepository rsvpRepository;
    @Mock private DemoWalletRepository demoWalletRepository;
    @Mock private LedgerService ledgerService;
    @Mock private RewardService rewardService;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private NotificationEmitter notificationEmitter;
    @Mock private NotificationWriter notificationWriter;

    private EventServiceImpl service;
    private User user;
    private User organizer;
    private Event event;

    @BeforeEach
    void setUp() {
        service = new EventServiceImpl(eventRepository, rsvpRepository, demoWalletRepository,
            ledgerService, rewardService, userRepository, currentUserProvider, notificationEmitter);

        user = new User();
        user.setEmail("user@example.com");
        user.setFullName("User");
        user.setUsername("user");
        user.setTotalXp(50);
        ReflectionTestUtils.setField(user, "id", USER_ID);

        organizer = new User();
        organizer.setFullName("Event Organizer");
        organizer.setUsername("organizer");
        ReflectionTestUtils.setField(organizer, "id", ORGANIZER_ID);

        event = Event.builder().organizer(organizer).title("Flutter Workshop")
            .dateTime(LocalDateTime.now().plusDays(7))
            .attendeeCount(3).attendeeLimit(50)
            .status(EventStatus.PUBLISHED).isPaid(false).build();
        ReflectionTestUtils.setField(event, "id", EVENT_ID);

        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(eventRepository.findByIdAndDeletedAtIsNull(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventRepository.save(any(Event.class))).thenAnswer(i -> {
            Event e = i.getArgument(0);
            if (e.getId() == null) {
                ReflectionTestUtils.setField(e, "id", EVENT_ID);
            }
            return e;
        });
        when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(eventRepository.reserveSeat(EVENT_ID)).thenReturn(1);
    }

    @Test
    void rsvpJoinsIncrementsTheCountAndAwardsXp() {
        RsvpResponse response = service.rsvp(EVENT_ID);

        assertThat(response.status()).isEqualTo(RsvpStatus.JOINED);
        assertThat(response.attendeeCount()).isEqualTo(4);
        assertThat(response.xpAwarded()).isEqualTo(10);
        verify(eventRepository).reserveSeat(EVENT_ID);
        verify(rewardService).award(any(), eq("EVENT_RSVP"), eq(EVENT_ID), eq(10));
    }

    @Test
    void successfulRsvpNotifiesTheOrganizerWithNavigationAndDedupeData() {
        service.rsvp(EVENT_ID);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationEmitter).emit(captor.capture());
        NotificationRequest request = captor.getValue();
        assertThat(request.recipientUserId()).isEqualTo(ORGANIZER_ID);
        assertThat(request.templateKey()).isEqualTo("COMMUNITY_EVENT_RSVP_ORGANIZER");
        assertThat(request.type()).isEqualTo("COMMUNITY_EVENT_RSVP");
        assertThat(request.parameters())
            .containsEntry("attendeeName", "User")
            .containsEntry("eventTitle", "Flutter Workshop");
        assertThat(request.referenceType()).isEqualTo("EVENT");
        assertThat(request.referenceId()).isEqualTo(EVENT_ID);
        assertThat(request.dedupeKey()).isEqualTo("event-rsvp:" + EVENT_ID + ":" + USER_ID);
        assertThat(request.channels()).containsExactlyInAnyOrder(
            NotificationChannel.IN_APP, NotificationChannel.MOBILE_PUSH);
    }

    @Test
    void organizerSelfRsvpDoesNotCreateAnOrganizerNotification() {
        event.setOrganizer(user);

        service.rsvp(EVENT_ID);

        verify(notificationEmitter, never()).emit(any(NotificationRequest.class));
    }

    @Test
    void rsvpPersistsTheFullRegistrationForm() {
        EventRegistrationRequest registration = new EventRegistrationRequest(
            "Registered Student", "registered@example.com", "012345678",
            "ACLEDA University of Business", "Computer Science", "Year 3", true);

        service.rsvp(EVENT_ID, registration);

        ArgumentCaptor<EventRsvp> captor = ArgumentCaptor.forClass(EventRsvp.class);
        verify(rsvpRepository).save(captor.capture());
        EventRsvp saved = captor.getValue();
        assertThat(saved.getFullName()).isEqualTo("Registered Student");
        assertThat(saved.getEmail()).isEqualTo("registered@example.com");
        assertThat(saved.getPhoneNumber()).isEqualTo("012345678");
        assertThat(saved.getUniversity()).isEqualTo("ACLEDA University of Business");
        assertThat(saved.getFaculty()).isEqualTo("Computer Science");
        assertThat(saved.getStudyYear()).isEqualTo("Year 3");
        assertThat(saved.getTermsAccepted()).isTrue();
    }

    @Test
    void rsvpRejectsRegistrationWhenTermsAreNotAccepted() {
        EventRegistrationRequest registration = new EventRegistrationRequest(
            "Registered Student", "registered@example.com", "012345678",
            "ACLEDA University of Business", "Computer Science", "Year 3", false);

        assertThatThrownBy(() -> service.rsvp(EVENT_ID, registration))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("accept the event terms");
        verify(eventRepository, never()).reserveSeat(any());
    }

    /** RSVP backs a button and (event_id, user_id) is unique. */
    @Test
    void rsvpIsIdempotentAndDoesNotAwardXpTwice() {
        when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID))
            .thenReturn(Optional.of(EventRsvp.builder().status(RsvpStatus.JOINED).build()));

        RsvpResponse response = service.rsvp(EVENT_ID);

        assertThat(response.attendeeCount()).isEqualTo(3);
        assertThat(response.xpAwarded()).isZero();
        verify(eventRepository, never()).reserveSeat(any());
        verify(rewardService, never()).award(any(), any(), any(), anyInt());
        verify(notificationEmitter, never()).emit(any(NotificationRequest.class));
    }

    @Test
    void notificationWriterFailureDoesNotUndoRsvpState() {
        service = new EventServiceImpl(eventRepository, rsvpRepository, demoWalletRepository,
            ledgerService, rewardService, userRepository, currentUserProvider,
            emitterWithWriterFailure());

        RsvpResponse response = service.rsvp(EVENT_ID);

        assertThat(response.status()).isEqualTo(RsvpStatus.JOINED);
        verify(rsvpRepository).save(any(EventRsvp.class));
        verify(rewardService).award(any(), eq("EVENT_RSVP"), eq(EVENT_ID), eq(10));
        verify(notificationWriter).writeInNewTransaction(any(NotificationRequestedEvent.class));
    }

    @Test
    void rsvpIsRejectedWhenTheEventIsFull() {
        when(eventRepository.reserveSeat(EVENT_ID)).thenReturn(0);

        assertThatThrownBy(() -> service.rsvp(EVENT_ID))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("full");
    }

    /**
     * The ordering that matters: charging first and then discovering the event is full
     * takes someone's money for nothing.
     */
    @Test
    void aFullPaidEventIsRejectedBeforeTheWalletIsTouched() {
        event.setIsPaid(true);
        event.setPrice(new BigDecimal("5.00"));
        event.setCurrency(Currency.USD);
        when(eventRepository.reserveSeat(EVENT_ID)).thenReturn(0);

        assertThatThrownBy(() -> service.rsvp(EVENT_ID)).isInstanceOf(ApiException.class);

        verify(demoWalletRepository, never()).save(any(DemoWallet.class));
        verify(ledgerService, never()).debit(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void paidRsvpDebitsTheDemoWalletThroughTheLedger() {
        event.setIsPaid(true);
        event.setPrice(new BigDecimal("5.00"));
        event.setCurrency(Currency.USD);
        DemoWallet wallet = DemoWallet.builder().user(user)
            .balanceUsd(new BigDecimal("20.00"))
            .balanceKhr(new BigDecimal("0.00"))
            .build();
        when(demoWalletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(wallet));

        RsvpResponse response = service.rsvp(EVENT_ID);

        assertThat(response.amountPaid()).isEqualByComparingTo("5.00");
        // The client must be able to label the screen as a simulation.
        assertThat(response.simulatedPayment()).isTrue();

        // Balance movement lives in the ledger now: a DEBIT of type EVENT_PAYMENT, linked to the event.
        verify(ledgerService).debit(eq(wallet), eq(TransactionType.EVENT_PAYMENT), eq(Currency.USD),
            eq(new BigDecimal("5.00")), any(), eq("EVENT"), any());
    }

    @Test
    void paidRsvpEmitsAPaymentReceiptForTheLedgerCharge() {
        event.setIsPaid(true);
        event.setPrice(new BigDecimal("5.00"));
        event.setCurrency(Currency.USD);
        DemoWallet wallet = DemoWallet.builder().user(user)
            .balanceUsd(new BigDecimal("20.00"))
            .balanceKhr(new BigDecimal("0.00"))
            .build();
        UUID transactionId = UUID.randomUUID();
        DemoTransaction transaction = DemoTransaction.builder().wallet(wallet).build();
        ReflectionTestUtils.setField(transaction, "id", transactionId);
        when(demoWalletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(wallet));
        when(ledgerService.debit(eq(wallet), eq(TransactionType.EVENT_PAYMENT), eq(Currency.USD),
            eq(new BigDecimal("5.00")), any(), eq("EVENT"), eq(EVENT_ID))).thenReturn(transaction);

        service.rsvp(EVENT_ID);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationEmitter, times(2)).emit(captor.capture());
        NotificationRequest receipt = captor.getAllValues().stream()
            .filter(request -> "COMMUNITY_EVENT_PAYMENT_RECEIPT".equals(request.templateKey()))
            .findFirst().orElseThrow();
        assertThat(receipt.recipientUserId()).isEqualTo(USER_ID);
        assertThat(receipt.type()).isEqualTo("COMMUNITY_EVENT_PAYMENT");
        assertThat(receipt.parameters())
            .containsEntry("amount", "5")
            .containsEntry("currency", "USD")
            .containsEntry("eventTitle", "Flutter Workshop");
        assertThat(receipt.referenceType()).isEqualTo("EVENT");
        assertThat(receipt.referenceId()).isEqualTo(EVENT_ID);
        assertThat(receipt.dedupeKey()).isEqualTo("event-payment-receipt:" + transactionId);
    }

    @Test
    void paidRsvpDebitsTheKhrWalletWhenTheEventUsesKhr() {
        event.setIsPaid(true);
        event.setPrice(new BigDecimal("5000"));
        event.setCurrency(Currency.KHR);
        DemoWallet wallet = DemoWallet.builder().user(user)
            .balanceUsd(new BigDecimal("20.00"))
            .balanceKhr(new BigDecimal("10000"))
            .build();
        when(demoWalletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(wallet));

        RsvpResponse response = service.rsvp(EVENT_ID);

        assertThat(response.amountPaid()).isEqualByComparingTo("5000");
        verify(ledgerService).debit(eq(wallet), eq(TransactionType.EVENT_PAYMENT), eq(Currency.KHR),
            eq(new BigDecimal("5000")), any(), eq("EVENT"), any());
    }

    @Test
    void paidRsvpPropagatesTheLedgersInsufficientFundsError() {
        event.setIsPaid(true);
        event.setPrice(new BigDecimal("50.00"));
        event.setCurrency(Currency.USD);
        when(demoWalletRepository.findByUserId(USER_ID))
            .thenReturn(Optional.of(DemoWallet.builder()
                .balanceUsd(new BigDecimal("5.00"))
                .balanceKhr(new BigDecimal("0.00"))
                .build()));
        org.mockito.Mockito.doThrow(new ApiException("Demo wallet balance is too low for this payment",
                "DEMO_WALLET_INSUFFICIENT_BALANCE"))
            .when(ledgerService).debit(any(), any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> service.rsvp(EVENT_ID))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("balance is too low");

        verify(rsvpRepository, never()).save(any(EventRsvp.class));
        verify(notificationEmitter, never()).emit(any(NotificationRequest.class));
    }

    @Test
    void freeRsvpNeverTouchesTheWallet() {
        service.rsvp(EVENT_ID);

        verify(demoWalletRepository, never()).findByUserId(any());
        verify(ledgerService, never()).debit(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rsvpIsRejectedForACancelledEvent() {
        event.setStatus(EventStatus.CANCELLED);

        assertThatThrownBy(() -> service.rsvp(EVENT_ID))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("cancelled");
    }

    @Test
    void rsvpIsRejectedForAnEventInThePast() {
        event.setDateTime(LocalDateTime.now().minusDays(1));

        assertThatThrownBy(() -> service.rsvp(EVENT_ID))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("already taken place");
    }

    /** The unique constraint forbids a second row, so a cancelled RSVP is reused. */
    @Test
    void rejoiningAfterCancellingReusesTheRsvpRow() {
        EventRsvp cancelled = EventRsvp.builder().status(RsvpStatus.CANCELLED).build();
        when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.of(cancelled));

        service.rsvp(EVENT_ID);

        assertThat(cancelled.getStatus()).isEqualTo(RsvpStatus.JOINED);
        verify(eventRepository).reserveSeat(EVENT_ID);
    }

    @Test
    void cancellingDecrementsTheCount() {
        when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID))
            .thenReturn(Optional.of(EventRsvp.builder().status(RsvpStatus.JOINED).build()));

        service.cancelRsvp(EVENT_ID);

        verify(eventRepository).releaseSeat(EVENT_ID);
    }

    @Test
    void cancellingTwiceDoesNotDecrementTwice() {
        when(rsvpRepository.findByEventIdAndUserId(EVENT_ID, USER_ID))
            .thenReturn(Optional.of(EventRsvp.builder().status(RsvpStatus.CANCELLED).build()));

        service.cancelRsvp(EVENT_ID);

        verify(eventRepository, never()).releaseSeat(any());
    }

    @Test
    void anEventWithNoLimitIsNeverFull() {
        event.setAttendeeLimit(null);
        event.setAttendeeCount(9999);

        assertThat(service.rsvp(EVENT_ID).status()).isEqualTo(RsvpStatus.JOINED);
    }

    /** Backs up chk_events_paid_requires_price with a readable error. */
    @Test
    void creatingAPaidEventWithoutAPriceIsRejected() {
        assertThatThrownBy(() -> service.create(new EventCreateRequest(
            "Paid", null, null, null, LocalDateTime.now().plusDays(1), null, null,
            EventStatus.PUBLISHED, null, true, null)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("price above zero");
    }

    @Test
    void creatingAPaidEventWithoutCurrencyIsRejected() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);

        assertThatThrownBy(() -> service.create(new EventCreateRequest(
            "Paid", null, null, null, start, start.plusHours(1), EventMode.IN_PERSON,
            null, "Campus", null, null, null, EventStatus.PUBLISHED, null, true,
            new BigDecimal("5.00"), null)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("currency");
    }

    @Test
    void creatingAPaidEventStoresTheSelectedCurrency() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);

        service.create(new EventCreateRequest(
            "KHR workshop", null, "WORKSHOP", null, start, start.plusHours(2),
            EventMode.IN_PERSON, null, "Campus", null, null, 50,
            EventStatus.PUBLISHED, null, true, new BigDecimal("5000"), Currency.KHR));

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(captor.capture());
        assertThat(captor.getValue().getIsPaid()).isTrue();
        assertThat(captor.getValue().getPrice()).isEqualByComparingTo("5000");
        assertThat(captor.getValue().getCurrency()).isEqualTo(Currency.KHR);
    }

    @Test
    void aFreeEventDoesNotKeepAStalePrice() {
        service.create(new EventCreateRequest("Free", null, null, null,
            LocalDateTime.now().plusDays(1), null, null, EventStatus.PUBLISHED, null, false,
            new BigDecimal("9.99")));

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(captor.capture());
        assertThat(captor.getValue().getPrice()).isNull();
        assertThat(captor.getValue().getCurrency()).isNull();
    }

    @Test
    void creatingAnOnlineEventStoresScheduleModeMeetingUrlAndCoordinates() {
        LocalDateTime start = LocalDateTime.now().plusDays(2);
        LocalDateTime end = start.plusHours(2);

        service.create(new EventCreateRequest(
            "Online session", "Description", "Workshop", null,
            start, end, EventMode.ONLINE, "https://meet.google.com/example",
            null, new BigDecimal("11.55"), new BigDecimal("104.92"), 50,
            EventStatus.PUBLISHED, null, false, null));

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(captor.capture());
        Event saved = captor.getValue();
        assertThat(saved.getDateTime()).isEqualTo(start);
        assertThat(saved.getEndDateTime()).isEqualTo(end);
        assertThat(saved.getMode()).isEqualTo(EventMode.ONLINE);
        assertThat(saved.getMeetingUrl()).isEqualTo("https://meet.google.com/example");
        assertThat(saved.getLatitude()).isEqualByComparingTo("11.55");
        assertThat(saved.getLongitude()).isEqualByComparingTo("104.92");
    }

    @Test
    void creatingAnEventWithEndBeforeStartIsRejected() {
        LocalDateTime start = LocalDateTime.now().plusDays(2);

        assertThatThrownBy(() -> service.create(new EventCreateRequest(
            "Invalid schedule", null, null, null, start, start,
            EventMode.IN_PERSON, null, "Campus", null, null, null,
            EventStatus.PUBLISHED, null, false, null)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("end time must be after start time");
    }

    @Test
    void updatingSomeoneElsesEventReadsAsNotFound() {
        when(eventRepository.findByIdAndOrganizerIdAndDeletedAtIsNull(EVENT_ID, USER_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(EVENT_ID, new EventCreateRequest(
            "Hijack", null, null, null, LocalDateTime.now().plusDays(1), null, null, null, null, false, null)))
            .isInstanceOf(NotFoundException.class);
    }

    private NotificationEmitter emitterWithWriterFailure() {
        NotificationEventListener listener = new NotificationEventListener(notificationWriter);
        doThrow(new IllegalStateException("notification store unavailable"))
            .when(notificationWriter).writeInNewTransaction(any(NotificationRequestedEvent.class));
        return request -> listener.onNotificationRequested(new NotificationRequestedEvent(request));
    }
}
