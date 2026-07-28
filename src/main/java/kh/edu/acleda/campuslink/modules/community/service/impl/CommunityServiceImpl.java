package kh.edu.acleda.campuslink.modules.community.service.impl;

import java.text.Normalizer;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.dto.request.CommunityCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.CommunityMemberResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.CommunityResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.PostResponse;
import kh.edu.acleda.campuslink.modules.community.entity.Community;
import kh.edu.acleda.campuslink.modules.community.entity.CommunityMember;
import kh.edu.acleda.campuslink.modules.community.enums.CommunityPrivacy;
import kh.edu.acleda.campuslink.modules.community.enums.CommunityRole;
import kh.edu.acleda.campuslink.modules.community.enums.MemberStatus;
import kh.edu.acleda.campuslink.modules.community.repository.CommunityMemberRepository;
import kh.edu.acleda.campuslink.modules.community.repository.CommunityRepository;
import kh.edu.acleda.campuslink.modules.community.service.CommunityService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class CommunityServiceImpl implements CommunityService {

    private static final Set<CommunityRole> CAN_MANAGE = EnumSet.of(CommunityRole.OWNER, CommunityRole.ADMIN);

    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public CommunityServiceImpl(CommunityRepository communityRepository,
                                CommunityMemberRepository memberRepository,
                                UserRepository userRepository,
                                CurrentUserProvider currentUserProvider) {
        this.communityRepository = communityRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional
    public CommunityResponse create(CommunityCreateRequest request) {
        User creator = loadCurrentUser();
        Community community = communityRepository.save(Community.builder()
            .name(request.name().strip())
            .slug(uniqueSlug(request.name()))
            .description(request.description())
            .category(request.category())
            .coverImageUrl(request.coverImageUrl())
            .avatarUrl(request.avatarUrl())
            .privacy(request.privacy() == null ? CommunityPrivacy.PUBLIC : request.privacy())
            .createdByUser(creator)
            .memberCount(0)
            .postCount(0)
            .build());

        // The creator is the first member, otherwise the community starts with an owner
        // who is not in it and memberCount disagrees with community_members.
        memberRepository.save(CommunityMember.builder()
            .community(community)
            .user(creator)
            .role(CommunityRole.OWNER)
            .status(MemberStatus.ACTIVE)
            .joinedAt(Instant.now())
            .build());
        communityRepository.adjustMemberCount(community.getId(), 1);
        community.setMemberCount(1);

        return toResponse(community, CommunityRole.OWNER, false);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommunityResponse> discover(String category, Pageable pageable) {
        UUID userId = currentUserProvider.getCurrentUserId();
        // Private communities are not browsable: you reach them by direct link.
        return PageResponse.from(
            communityRepository.findDiscoverable(CommunityPrivacy.PUBLIC, category, pageable),
            community -> toResponse(community, roleIn(community.getId(), userId), false));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommunityResponse> getMine(Pageable pageable) {
        UUID userId = currentUserProvider.getCurrentUserId();
        return PageResponse.from(
            memberRepository.findMyCommunities(userId, MemberStatus.ACTIVE, pageable),
            member -> toResponse(member.getCommunity(), member.getRole(), false));
    }

    @Override
    @Transactional(readOnly = true)
    public CommunityResponse getBySlug(String slug) {
        Community community = communityRepository.findBySlugAndDeletedAtIsNull(slug)
            .orElseThrow(() -> new NotFoundException("Community not found"));
        // A PRIVATE community is reachable by direct link, so the viewer may be a pending
        // requester here even though it never appears in discovery.
        Optional<CommunityMember> membership = memberRepository
            .findByCommunityIdAndUserId(community.getId(), currentUserProvider.getCurrentUserId());
        CommunityRole role = membership
            .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
            .map(CommunityMember::getRole)
            .orElse(null);
        boolean pending = membership
            .map(member -> member.getStatus() == MemberStatus.PENDING)
            .orElse(false);
        return toResponse(community, role, pending);
    }

    @Override
    @Transactional
    public CommunityResponse update(UUID communityId, CommunityCreateRequest request) {
        Community community = loadManageable(communityId);
        community.setName(request.name().strip());
        community.setDescription(request.description());
        community.setCategory(request.category());
        community.setCoverImageUrl(request.coverImageUrl());
        community.setAvatarUrl(request.avatarUrl());
        if (request.privacy() != null) {
            community.setPrivacy(request.privacy());
        }
        // The slug is deliberately not regenerated: it is a public URL, and silently
        // changing it would break every link already shared.
        return toResponse(communityRepository.save(community), myRole(communityId), false);
    }

    @Override
    @Transactional
    public void delete(UUID communityId) {
        Community community = loadCommunity(communityId);
        if (myRole(communityId) != CommunityRole.OWNER) {
            throw new NotFoundException("Community not found");
        }
        community.setDeletedAt(Instant.now());
        communityRepository.save(community);

        // Destructive and irreversible from the API: it takes the community away from
        // every member at once, and a soft delete leaves nothing else to point at when
        // someone asks where it went. memberCount is included because that is the blast
        // radius.
        log.info("Community soft-deleted: id={} slug={} members={} by user={}",
            communityId, community.getSlug(), community.getMemberCount(),
            currentUserProvider.getCurrentUserId());
    }

    @Override
    @Transactional
    public CommunityResponse join(UUID communityId) {
        Community community = loadCommunity(communityId);
        UUID userId = currentUserProvider.getCurrentUserId();
        // A PRIVATE community must not be joinable by anyone holding its id: membership is
        // requested and an owner/admin approves it. Without this gate PRIVATE only hid the
        // community from discovery while letting anyone in through the direct link.
        boolean requiresApproval = community.getPrivacy() == CommunityPrivacy.PRIVATE;

        Optional<CommunityMember> existing = memberRepository.findByCommunityIdAndUserId(communityId, userId);
        if (existing.isPresent()) {
            CommunityMember member = existing.get();
            // Idempotent: Join backs a button and (community_id, user_id) is unique.
            if (member.getStatus() == MemberStatus.ACTIVE) {
                return toResponse(community, member.getRole(), false);
            }
            if (member.getStatus() == MemberStatus.PENDING) {
                // The request already exists; do not duplicate it or count them as a member.
                return toResponse(community, null, true);
            }
            // Rejoining reuses the row - the unique constraint forbids a second one - but a
            // PRIVATE community still routes the return through approval.
            member.setRole(CommunityRole.MEMBER);
            if (requiresApproval) {
                member.setStatus(MemberStatus.PENDING);
                memberRepository.save(member);
                return toResponse(community, null, true);
            }
            member.setStatus(MemberStatus.ACTIVE);
            member.setJoinedAt(Instant.now());
            memberRepository.save(member);
            communityRepository.adjustMemberCount(communityId, 1);
            community.setMemberCount(community.getMemberCount() + 1);
            return toResponse(community, member.getRole(), false);
        }

        // A pending request is not a membership yet, so memberCount and joinedAt only move
        // once it is approved.
        memberRepository.save(CommunityMember.builder()
            .community(community)
            .user(loadCurrentUser())
            .role(CommunityRole.MEMBER)
            .status(requiresApproval ? MemberStatus.PENDING : MemberStatus.ACTIVE)
            .joinedAt(requiresApproval ? null : Instant.now())
            .build());
        if (requiresApproval) {
            return toResponse(community, null, true);
        }
        communityRepository.adjustMemberCount(communityId, 1);
        community.setMemberCount(community.getMemberCount() + 1);
        return toResponse(community, CommunityRole.MEMBER, false);
    }

    @Override
    @Transactional
    public void leave(UUID communityId) {
        UUID userId = currentUserProvider.getCurrentUserId();
        CommunityMember member = memberRepository.findByCommunityIdAndUserId(communityId, userId)
            .orElseThrow(() -> new NotFoundException("Membership not found"));

        if (member.getStatus() != MemberStatus.ACTIVE) {
            return;
        }
        // Otherwise a community can be orphaned with nobody able to administer it.
        if (member.getRole() == CommunityRole.OWNER) {
            throw new ApiException("Transfer ownership before leaving the community",
                "COMMUNITY_OWNER_CANNOT_LEAVE");
        }

        member.setStatus(MemberStatus.LEFT);
        memberRepository.save(member);
        communityRepository.adjustMemberCount(communityId, -1);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommunityMemberResponse> members(UUID communityId, Pageable pageable) {
        loadCommunity(communityId);
        return PageResponse.from(
            memberRepository.findMembers(communityId, MemberStatus.ACTIVE, pageable),
            this::toMemberResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommunityMemberResponse> joinRequests(UUID communityId, Pageable pageable) {
        loadManageable(communityId);
        return PageResponse.from(
            memberRepository.findMembers(communityId, MemberStatus.PENDING, pageable),
            this::toMemberResponse);
    }

    @Override
    @Transactional
    public CommunityMemberResponse approveJoinRequest(UUID communityId, UUID userId) {
        loadManageable(communityId);
        CommunityMember member = pendingRequest(communityId, userId);
        member.setStatus(MemberStatus.ACTIVE);
        member.setJoinedAt(Instant.now());
        memberRepository.save(member);
        // The count moves only now: a pending request never incremented it.
        communityRepository.adjustMemberCount(communityId, 1);
        return toMemberResponse(member);
    }

    @Override
    @Transactional
    public void rejectJoinRequest(UUID communityId, UUID userId) {
        loadManageable(communityId);
        CommunityMember member = pendingRequest(communityId, userId);
        // REMOVED rather than deleted: the row is the record that a request was declined,
        // and the unique (community_id, user_id) constraint forbids a fresh row anyway.
        // A later join re-opens it as PENDING through the rejoin path.
        member.setStatus(MemberStatus.REMOVED);
        memberRepository.save(member);
    }

    private CommunityMember pendingRequest(UUID communityId, UUID userId) {
        return memberRepository.findByCommunityIdAndUserId(communityId, userId)
            .filter(member -> member.getStatus() == MemberStatus.PENDING)
            .orElseThrow(() -> new NotFoundException("Join request not found"));
    }

    private Community loadCommunity(UUID communityId) {
        return communityRepository.findByIdAndDeletedAtIsNull(communityId)
            .orElseThrow(() -> new NotFoundException("Community not found"));
    }

    /** Not-found rather than forbidden, so the API does not confirm what it holds. */
    private Community loadManageable(UUID communityId) {
        Community community = loadCommunity(communityId);
        if (!CAN_MANAGE.contains(myRole(communityId))) {
            throw new NotFoundException("Community not found");
        }
        return community;
    }

    private CommunityRole myRole(UUID communityId) {
        return roleIn(communityId, currentUserProvider.getCurrentUserId());
    }

    private CommunityRole roleIn(UUID communityId, UUID userId) {
        return memberRepository.findByCommunityIdAndUserId(communityId, userId)
            .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
            .map(CommunityMember::getRole)
            .orElse(null);
    }

    private User loadCurrentUser() {
        return userRepository.findById(currentUserProvider.getCurrentUserId())
            .orElseThrow(() -> new NotFoundException("User not found"));
    }

    /**
     * Slugs are derived from the name, never supplied by the client, and collisions get
     * a numeric suffix. Khmer names normalise away to nothing, so those fall back to a
     * generated slug rather than producing an empty one.
     */
    private String uniqueSlug(String name) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFKD)
            .replaceAll("[^\\p{Alnum}\\s-]", "")
            .strip()
            .toLowerCase(Locale.ROOT)
            .replaceAll("\\s+", "-");
        if (base.isBlank()) {
            base = "community";
        }
        if (base.length() > 100) {
            base = base.substring(0, 100);
        }
        String candidate = base;
        int suffix = 2;
        while (communityRepository.existsBySlug(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private CommunityResponse toResponse(Community community, CommunityRole myRole, boolean requestPending) {
        return new CommunityResponse(
            community.getId(),
            community.getName(),
            community.getSlug(),
            community.getDescription(),
            community.getCategory(),
            community.getCoverImageUrl(),
            community.getAvatarUrl(),
            community.getPrivacy(),
            community.getMemberCount(),
            community.getPostCount(),
            myRole != null,
            myRole,
            requestPending,
            community.getCreatedAt()
        );
    }

    private CommunityMemberResponse toMemberResponse(CommunityMember member) {
        User user = member.getUser();
        return new CommunityMemberResponse(
            new PostResponse.AuthorSummary(user.getId(), user.getFullName(), user.getUsername(), user.getAvatarUrl()),
            member.getRole(),
            member.getJoinedAt()
        );
    }
}
