package kh.edu.acleda.campuslink.modules.auth.repository;

import kh.edu.acleda.campuslink.modules.auth.entity.Provider;
import kh.edu.acleda.campuslink.modules.auth.entity.SocialAccount;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SocialAccountRepository extends JpaRepository<SocialAccount, UUID> {
    Optional<SocialAccount> findByProviderAndProviderUserId(Provider provider, String providerUserId);
    boolean existsByUserAndProvider(User user, Provider provider);

    /**
     * Used by account anonymisation: these rows hold the provider's email and its
     * stable user id, both of which identify a person, so scrubbing the user row
     * alone would leave the identity recoverable.
     */
    void deleteByUser(User user);
}
