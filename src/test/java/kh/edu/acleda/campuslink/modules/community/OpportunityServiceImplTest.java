package kh.edu.acleda.campuslink.modules.community;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.dto.request.OpportunityCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.OpportunityResponse;
import kh.edu.acleda.campuslink.modules.community.entity.Opportunity;
import kh.edu.acleda.campuslink.modules.community.repository.OpportunityRepository;
import kh.edu.acleda.campuslink.modules.community.service.impl.OpportunityServiceImpl;
import kh.edu.acleda.campuslink.modules.profile.enums.SavedItemType;
import kh.edu.acleda.campuslink.modules.profile.repository.SavedItemRepository;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OpportunityServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OPP_ID = UUID.randomUUID();

    @Mock private OpportunityRepository opportunityRepository;
    @Mock private SavedItemRepository savedItemRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    private OpportunityServiceImpl service;
    private Opportunity opportunity;

    @BeforeEach
    void setUp() {
        service = new OpportunityServiceImpl(opportunityRepository, savedItemRepository, currentUserProvider);
        opportunity = Opportunity.builder().title("Backend Internship").organization("ACLEDA")
            .type("INTERNSHIP").deadline(LocalDate.now().plusDays(30)).build();
        ReflectionTestUtils.setField(opportunity, "id", OPP_ID);

        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(opportunityRepository.findByIdAndDeletedAtIsNull(OPP_ID)).thenReturn(Optional.of(opportunity));
        when(opportunityRepository.save(any(Opportunity.class))).thenAnswer(i -> {
            Opportunity o = i.getArgument(0);
            if (o.getId() == null) {
                ReflectionTestUtils.setField(o, "id", OPP_ID);
            }
            return o;
        });
        when(savedItemRepository.findSavedItemIds(any(), any(), anyCollection())).thenReturn(List.of());
    }

    @Test
    void createPersistsTheOpportunity() {
        OpportunityResponse response = service.create(new OpportunityCreateRequest(
            "Backend Internship", "ACLEDA", "INTERNSHIP", "Great role", LocalDate.now().plusDays(30),
            "Phnom Penh", "https://apply.example.com"));

        verify(opportunityRepository).save(any(Opportunity.class));
        assertThat(response.title()).isEqualTo("Backend Internship");
        assertThat(response.type()).isEqualTo("INTERNSHIP");
    }

    /** activeFrom = today when expired ones are excluded; null when included. */
    @Test
    void browseWithoutExpiredFiltersFromToday() {
        when(opportunityRepository.browse(eq("INTERNSHIP"), any(LocalDate.class), any()))
            .thenReturn(new PageImpl<>(List.of(opportunity)));

        PageResponse<OpportunityResponse> page = service.browse("INTERNSHIP", false, PageRequest.of(0, 20));

        assertThat(page.content()).hasSize(1);
        verify(opportunityRepository).browse(eq("INTERNSHIP"), any(LocalDate.class), any());
    }

    @Test
    void browseWithExpiredPassesNullActiveFrom() {
        when(opportunityRepository.browse(isNull(), isNull(), any()))
            .thenReturn(new PageImpl<>(List.of(opportunity)));

        service.browse(null, true, PageRequest.of(0, 20));

        verify(opportunityRepository).browse(isNull(), isNull(), any());
    }

    /** savedByMe is resolved server-side, batched one query per page. */
    @Test
    void browseMarksSavedOpportunities() {
        when(opportunityRepository.browse(any(), any(), any())).thenReturn(new PageImpl<>(List.of(opportunity)));
        when(savedItemRepository.findSavedItemIds(eq(USER_ID), eq(SavedItemType.OPPORTUNITY), anyCollection()))
            .thenReturn(List.of(OPP_ID));

        OpportunityResponse row = service.browse(null, false, PageRequest.of(0, 20)).content().get(0);

        assertThat(row.savedByMe()).isTrue();
    }

    @Test
    void pastDeadlineIsFlaggedExpired() {
        opportunity.setDeadline(LocalDate.now().minusDays(1));

        assertThat(service.getById(OPP_ID).expired()).isTrue();
    }

    @Test
    void openEndedOpportunityIsNeverExpired() {
        opportunity.setDeadline(null);

        assertThat(service.getById(OPP_ID).expired()).isFalse();
    }

    @Test
    void getByIdRejectsUnknownOpportunity() {
        when(opportunityRepository.findByIdAndDeletedAtIsNull(OPP_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(OPP_ID)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteIsASoftDelete() {
        service.delete(OPP_ID);

        assertThat(opportunity.getDeletedAt()).isNotNull();
    }
}
