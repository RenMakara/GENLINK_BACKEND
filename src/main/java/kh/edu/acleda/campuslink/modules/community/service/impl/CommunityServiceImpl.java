package kh.edu.acleda.campuslink.modules.community.service.impl;

import java.text.Normalizer;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
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
public class CommunityServiceImpl implements CommunityService {

    private static final Set<CommunityRole> CAN_MANAGE = EnumSet.of(CommunityRole.OWNER, CommunityRole.ADMIN);
    private static final String JOIN_REQUEST_NOTIFICATION_TEMPLATE = "COMMUNITY_JOIN_REQUEST_OWNER";
    private static final String JOIN_REQUEST_NOTIFICATION_TYPE = "COMMUNITY_JOIN_REQUEST";

    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final NotificationEmitter notificationEmitter;

    public CommunityServiceImpl(CommunityRepository communityRepository,
                                CommunityMemberRepository memberRepository,
                                UserRepository userRepository,
                                CurrentUserProvider currentUserProvider,
                                NotificationEmitter notificationEmitter) {
        this.communityRepository = communityRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
        this.notificationEmitter = notificationEmitter;
    }

    @Override
    @Transactional
    public CommunityResponse create(CommunityCreateRequest request) {
        User creator = loadCurrentUser();
        log.debug("Creating community: name='{}' privacy={} by user={}",
            request.name(), request.privacy(), creator.getId());
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

        log.info("Community created: id={} slug={} privacy={} by owner={}",
            community.getId(), community.getSlug(), community.getPrivacy(), creator.getId());
        return toResponse(community, CommunityRole.OWNER, false);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommunityResponse> discover(String category, Pageable pageable) {
        UUID userId = currentUserProvider.getCurrentUserId();
        log.debug("Discovering communities: category={} page={} size={} for user={}",
            category, pageable.getPageNumber(), pageable.getPageSize(), userId);
        // Private communities are not browsable: you reach them by direct link.
        Page<Community> page = communityRepository.findDiscoverable(CommunityPrivacy.PUBLIC, category, pageable);
        // Resolve every row's role in one query rather than one lookup per community (the N+1
        // discovery had), matching the batched pattern used by the feed and saved items.
        Map<UUID, CommunityRole> myRoles = activeRolesFor(userId, page.getContent());
        return PageResponse.from(page,
            community -> toResponse(community, myRoles.get(community.getId()), false));
    }

    /** The viewer's ACTIVE role in each of the given communities, batched into one query. */
    private Map<UUID, CommunityRole> activeRolesFor(UUID userId, List<Community> communities) {
        if (communities.isEmpty()) {
            return Map.of();
        }
        Set<UUID> communityIds = communities.stream().map(Community::getId).collect(Collectors.toSet());
        Map<UUID, CommunityRole> roles = new HashMap<>();
        // getCommunity().getId() reads the FK off the lazy proxy without a load, so this stays one query.
        for (CommunityMember member :
            memberRepository.findByUserIdAndCommunityIdInAndStatus(userId, communityIds, MemberStatus.ACTIVE)) {
            roles.put(member.getCommunity().getId(), member.getRole());
        }
        return roles;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommunityResponse> getMine(Pageable pageable) {
        UUID userId = currentUserProvider.getCurrentUserId();
        log.debug("Listing communities for user {}", userId);
        return PageResponse.from(
            memberRepository.findMyCommunities(userId, MemberStatus.ACTIVE, pageable),
            member -> toResponse(member.getCommunity(), member.getRole(), false));
    }

    @Override
    @Transactional(readOnly = true)
    public CommunityResponse getBySlug(String slug) {
        log.debug("Fetching community by slug: {}", slug);
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
        log.debug("Updating community {} by user {}", communityId, currentUserProvider.getCurrentUserId());
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
        CommunityResponse response = toResponse(communityRepository.save(community), myRole(communityId), false);
        log.info("Community updated: id={} by user={}", communityId, currentUserProvider.getCurrentUserId());
        return response;
    }

    @Override
    @Transactional
    public void delete(UUID communityId) {
        Community community = loadCommunity(communityId);
        log.debug("Delete requested for community {} by user {}", communityId, currentUserProvider.getCurrentUserId());
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
        log.debug("Join requested: community={} user={} requiresApproval={}", communityId, userId, requiresApproval);

        Optional<CommunityMember> existing = memberRepository.findByCommunityIdAndUserId(communityId, userId);
        if (existing.isPresent()) {
            CommunityMember member = existing.get();
            // Idempotent: Join backs a button and (community_id, user_id) is unique.
            if (member.getStatus() == MemberStatus.ACTIVE) {
                log.debug("Join is a no-op, user {} already active in community {}", userId, communityId);
                return toResponse(community, member.getRole(), false);
            }
            if (member.getStatus() == MemberStatus.PENDING) {
                // The request already exists; do not duplicate it or count them as a member.
                log.debug("Join is a no-op, request already pending: community={} user={}", communityId, userId);
                return toResponse(community, null, true);
            }
            // Rejoining reuses the row - the unique constraint forbids a second one - but a
            // PRIVATE community still routes the return through approval.
            member.setRole(CommunityRole.MEMBER);
            if (requiresApproval) {
                User requester = loadCurrentUser();
                member.setStatus(MemberStatus.PENDING);
                memberRepository.save(member);
                emitJoinRequestNotification(communityId, community, requester);
                log.info("Join request re-opened (pending approval): community={} user={}", communityId, userId);
                return toResponse(community, null, true);
            }
            member.setStatus(MemberStatus.ACTIVE);
            member.setJoinedAt(Instant.now());
            memberRepository.save(member);
            communityRepository.adjustMemberCount(communityId, 1);
            community.setMemberCount(community.getMemberCount() + 1);
            log.info("User {} rejoined community {}", userId, communityId);
            return toResponse(community, member.getRole(), false);
        }

        // A pending request is not a membership yet, so memberCount and joinedAt only move
        // once it is approved.
        User requester = loadCurrentUser();
        memberRepository.save(CommunityMember.builder()
            .community(community)
            .user(requester)
            .role(CommunityRole.MEMBER)
            .status(requiresApproval ? MemberStatus.PENDING : MemberStatus.ACTIVE)
            .joinedAt(requiresApproval ? null : Instant.now())
            .build());
        if (requiresApproval) {
            emitJoinRequestNotification(communityId, community, requester);
            log.info("Join request created (pending approval): community={} user={}", communityId, userId);
            return toResponse(community, null, true);
        }
        communityRepository.adjustMemberCount(communityId, 1);
        community.setMemberCount(community.getMemberCount() + 1);
        log.info("User {} joined community {}", userId, communityId);
        return toResponse(community, CommunityRole.MEMBER, false);
    }

    @Override
    @Transactional
    public void leave(UUID communityId) {
        UUID userId = currentUserProvider.getCurrentUserId();
        log.debug("Leave requested: community={} user={}", communityId, userId);
        CommunityMember member = memberRepository.findByCommunityIdAndUserId(communityId, userId)
            .orElseThrow(() -> new NotFoundException("Membership not found"));

        if (member.getStatus() != MemberStatus.ACTIVE) {
            log.debug("Leave is a no-op, user {} is not active in community {}", userId, communityId);
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
        log.info("User {} left community {}", userId, communityId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommunityMemberResponse> members(UUID communityId, Pageable pageable) {
        loadCommunity(communityId);
        log.debug("Listing active members of community {}", communityId);
        return PageResponse.from(
            memberRepository.findMembers(communityId, MemberStatus.ACTIVE, pageable),
            this::toMemberResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommunityMemberResponse> joinRequests(UUID communityId, Pageable pageable) {
        loadManageable(communityId);
        log.debug("Listing pending join requests for community {} by manager {}",
            communityId, currentUserProvider.getCurrentUserId());
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
        log.info("Join request approved: community={} user={} by manager={}",
            communityId, userId, currentUserProvider.getCurrentUserId());
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
        log.info("Join request rejected: community={} user={} by manager={}",
            communityId, userId, currentUserProvider.getCurrentUserId());
    }

    private CommunityMember pendingRequest(UUID communityId, UUID userId) {
        return memberRepository.findByCommunityIdAndUserId(communityId, userId)
            .filter(member -> member.getStatus() == MemberStatus.PENDING)
            .orElseThrow(() -> new NotFoundException("Join request not found"));
    }

    @Override
    @Transactional
    public CommunityMemberResponse changeMemberRole(UUID communityId, UUID userId, CommunityRole newRole) {
        loadCommunity(communityId);
        UUID callerId = currentUserProvider.getCurrentUserId();
        CommunityRole callerRole = roleIn(communityId, callerId);

        // Managing roles is an owner/admin action; everyone else must not even learn the
        // community exists through this endpoint.
        if (!CAN_MANAGE.contains(callerRole)) {
            throw new NotFoundException("Community not found");
        }
        // OWNER is granted only through transferOwnership, so it is never a valid target here.
        if (newRole == null || newRole == CommunityRole.OWNER) {
            throw new ApiException("Use transfer ownership to change the owner", "COMMUNITY_ROLE_INVALID");
        }
        if (callerId.equals(userId)) {
            throw new ApiException("You cannot change your own role", "COMMUNITY_ROLE_SELF");
        }

        CommunityMember target = memberRepository.findByCommunityIdAndUserId(communityId, userId)
            .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
            .orElseThrow(() -> new NotFoundException("Member not found"));

        // The owner's role is immutable here; it only moves via transferOwnership.
        if (target.getRole() == CommunityRole.OWNER) {
            throw new ApiException("The owner's role cannot be changed here", "COMMUNITY_ROLE_OWNER_IMMUTABLE");
        }
        // An admin may only manage moderators and members - it must not mint a peer admin or
        // demote another admin. Administering admins is reserved to the owner.
        if (callerRole == CommunityRole.ADMIN
            && (target.getRole() == CommunityRole.ADMIN || newRole == CommunityRole.ADMIN)) {
            throw new ApiException("Only the owner can assign or change the admin role",
                "COMMUNITY_ROLE_ADMIN_FORBIDDEN");
        }

        if (target.getRole() == newRole) {
            // Idempotent: setting the role it already has is a no-op, not an error.
            return toMemberResponse(target);
        }
        CommunityRole previous = target.getRole();
        target.setRole(newRole);
        memberRepository.save(target);
        log.info("Community role changed: community={} user={} {}->{} by {}",
            communityId, userId, previous, newRole, callerId);
        return toMemberResponse(target);
    }

    @Override
    @Transactional
    public CommunityResponse transferOwnership(UUID communityId, UUID newOwnerUserId) {
        Community community = loadCommunity(communityId);
        UUID callerId = currentUserProvider.getCurrentUserId();

        // Only the current owner can hand over ownership. Not-found for everyone else so the
        // endpoint never confirms the community to a non-owner.
        CommunityMember currentOwner = memberRepository.findByCommunityIdAndUserId(communityId, callerId)
            .filter(member -> member.getStatus() == MemberStatus.ACTIVE && member.getRole() == CommunityRole.OWNER)
            .orElseThrow(() -> new NotFoundException("Community not found"));

        if (callerId.equals(newOwnerUserId)) {
            throw new ApiException("You are already the owner", "COMMUNITY_ALREADY_OWNER");
        }
        CommunityMember newOwner = memberRepository.findByCommunityIdAndUserId(communityId, newOwnerUserId)
            .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
            .orElseThrow(() -> new NotFoundException("Member not found"));

        // The outgoing owner stays on as an ADMIN. Dropping them to MEMBER would lock them out
        // of the community they built and could leave nobody able to help administer it.
        currentOwner.setRole(CommunityRole.ADMIN);
        newOwner.setRole(CommunityRole.OWNER);
        memberRepository.save(currentOwner);
        memberRepository.save(newOwner);
        log.info("Community ownership transferred: community={} from={} to={}",
            communityId, callerId, newOwnerUserId);
        return toResponse(community, CommunityRole.ADMIN, false);
    }

    private Community loadCommunity(UUID communityId) {
        return communityRepository.findByIdAndDeletedAtIsNull(communityId)
            .orElseThrow(() -> new NotFoundException("Community not found"));
    }

    private void emitJoinRequestNotification(UUID communityId, Community community, User requester) {
        User owner = community.getCreatedByUser();
        if (owner == null || owner.getId() == null || requester == null || requester.getId() == null
            || owner.getId().equals(requester.getId())) {
            return;
        }

        String requesterName = requester.getFullName();
        if (isBlank(requesterName)) {
            requesterName = !isBlank(requester.getUsername()) ? requester.getUsername() : requester.getEmail();
        }
        if (isBlank(requesterName) || isBlank(community.getName()) || communityId == null) {
            log.warn("Skipping community join-request notification because details are incomplete: community={} user={}",
                communityId, requester.getId());
            return;
        }

        notificationEmitter.emit(new NotificationRequest(
            owner.getId(),
            JOIN_REQUEST_NOTIFICATION_TEMPLATE,
            JOIN_REQUEST_NOTIFICATION_TYPE,
            Map.of("requesterName", requesterName.strip(), "communityName", community.getName()),
            "COMMUNITY",
            communityId,
            "community-join-request:" + communityId + ":" + requester.getId()));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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
