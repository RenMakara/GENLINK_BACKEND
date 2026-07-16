package kh.edu.acleda.campuslink.modules.profile.service.impl;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.profile.dto.request.SocialLinkUpsertRequest;
import kh.edu.acleda.campuslink.modules.profile.dto.response.SocialLinkResponse;
import kh.edu.acleda.campuslink.modules.profile.entity.UserSocialLink;
import kh.edu.acleda.campuslink.modules.profile.enums.SocialPlatform;
import kh.edu.acleda.campuslink.modules.profile.repository.UserSocialLinkRepository;
import kh.edu.acleda.campuslink.modules.profile.service.SocialLinkService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SocialLinkServiceImpl implements SocialLinkService {

    private final UserSocialLinkRepository userSocialLinkRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public SocialLinkServiceImpl(UserSocialLinkRepository userSocialLinkRepository,
                                 UserRepository userRepository,
                                 CurrentUserProvider currentUserProvider) {
        this.userSocialLinkRepository = userSocialLinkRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SocialLinkResponse> getMine() {
        return getByUserId(currentUserProvider.getCurrentUserId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SocialLinkResponse> getByUserId(UUID userId) {
        return userSocialLinkRepository.findByUserIdOrderByPlatformAsc(userId)
            .stream()
            .map(link -> new SocialLinkResponse(link.getPlatform(), link.getUrl()))
            .toList();
    }

    @Override
    @Transactional
    public SocialLinkResponse upsert(SocialLinkUpsertRequest request) {
        String url = normalizeUrl(request.url());
        UUID userId = currentUserProvider.getCurrentUserId();

        UserSocialLink link = userSocialLinkRepository.findByUserIdAndPlatform(userId, request.platform())
            .orElseGet(() -> {
                User user = userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("User not found"));
                return UserSocialLink.builder().user(user).platform(request.platform()).build();
            });

        link.setUrl(url);
        UserSocialLink saved = userSocialLinkRepository.save(link);
        return new SocialLinkResponse(saved.getPlatform(), saved.getUrl());
    }

    @Override
    @Transactional
    public void delete(SocialPlatform platform) {
        UUID userId = currentUserProvider.getCurrentUserId();
        UserSocialLink link = userSocialLinkRepository.findByUserIdAndPlatform(userId, platform)
            .orElseThrow(() -> new NotFoundException("Social link not found"));
        userSocialLinkRepository.delete(link);
    }

    /**
     * Profile links are rendered as tappable links, so only http(s) is accepted.
     * Without this a javascript: or data: URL could be stored and opened.
     */
    private String normalizeUrl(String rawUrl) {
        String url = rawUrl.trim();
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new ApiException("Social link must start with http:// or https://", "VALIDATION_ERROR");
        }
        return url;
    }
}
