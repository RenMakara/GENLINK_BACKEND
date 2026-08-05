package kh.edu.acleda.campuslink.modules.community;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.dto.request.EventCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.RsvpResponse;
import kh.edu.acleda.campuslink.modules.community.entity.Event;
import kh.edu.acleda.campuslink.modules.community.entity.EventRsvp;
import kh.edu.acleda.campuslink.modules.community.enums.EventStatus;
import kh.edu.acleda.campuslink.modules.community.enums.RsvpStatus;
import kh.edu.acleda.campuslink.modules.community.repository.EventRepository;
import kh.edu.acleda.campuslink.modules.community.repository.EventRsvpRepository;
import kh.edu.acleda.campuslink.modules.community.service.impl.EventServiceImpl;
import kh.edu.acleda.campuslink.modules.entertainment.service.RewardService;
import kh.edu.acleda.campuslink.modules.finance.entity.DemoWallet;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionDirection;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionType;
import kh.edu.acleda.campuslink.modules.finance.repository.DemoWalletRepository;
import kh.edu.acleda.campuslink.modules.finance.service.LedgerService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();

    @Mock private EventRepository eventRepository;
    @Mock private EventRsvpRepository rsvpRepository;
    @Mock private DemoWalletRepository demoWalletRepository;
    @Mock private LedgerService ledgerService;
    @Mock private RewardService rewardService;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private EventServiceImpl service;
    private User user;
    private Event event;

    @BeforeEach
    void setUp() {
        service = new EventServiceImpl(eventRepository, rsvpRepository, demoWalletRepository,
            ledgerService, rewardService, userRepository, currentUserProvider);

        user = new User();
        user.setEmail("user@example.com");
        user.setFullName("User");
        user.setUsername("user");
        user.setTotalXp(50);
        ReflectionTestUtils.setField(user, "id", USER_ID);

        event = Event.builder().organizer(user).title("Flutter Workshop")
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
        when(eventRepository.reserveSeat(EVENT_ID)).thenReturn(0);

        assertThatThrownBy(() -> service.rsvp(EVENT_ID)).isInstanceOf(ApiException.class);

        verify(demoWalletRepository, never()).save(any(DemoWallet.class));
        verify(ledgerService, never()).debit(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void paidRsvpDebitsTheDemoWalletThroughTheLedger() {
        event.setIsPaid(true);
        event.setPrice(new BigDecimal("5.00"));
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
    void paidRsvpPropagatesTheLedgersInsufficientFundsError() {
        event.setIsPaid(true);
        event.setPrice(new BigDecimal("50.00"));
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
    void aFreeEventDoesNotKeepAStalePrice() {
        service.create(new EventCreateRequest("Free", null, null, null,
            LocalDateTime.now().plusDays(1), null, null, EventStatus.PUBLISHED, null, false,
            new BigDecimal("9.99")));

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository).save(captor.capture());
        assertThat(captor.getValue().getPrice()).isNull();
    }

    @Test
    void updatingSomeoneElsesEventReadsAsNotFound() {
        when(eventRepository.findByIdAndOrganizerIdAndDeletedAtIsNull(EVENT_ID, USER_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(EVENT_ID, new EventCreateRequest(
            "Hijack", null, null, null, LocalDateTime.now().plusDays(1), null, null, null, null, false, null)))
            .isInstanceOf(NotFoundException.class);
    }
}
