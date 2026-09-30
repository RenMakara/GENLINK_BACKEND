package kh.edu.acleda.campuslink.modules.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import kh.edu.acleda.campuslink.modules.finance.entity.DemoWallet;
import kh.edu.acleda.campuslink.modules.finance.repository.DemoWalletRepository;
import kh.edu.acleda.campuslink.modules.finance.service.DemoWalletService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** PostgreSQL-only regression test for first wallet access. */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "GENLINK_TEST_POSTGRES_URL", matches = ".+")
class PostgresWalletCreationConcurrencyIntegrationTest {

    @Autowired private DemoWalletService walletService;
    @Autowired private DemoWalletRepository walletRepository;
    @Autowired private UserRepository userRepository;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredEnv("GENLINK_TEST_POSTGRES_URL"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.username", () -> envOrDefault("GENLINK_TEST_POSTGRES_USERNAME", "postgres"));
        registry.add("spring.datasource.password", () -> envOrDefault("GENLINK_TEST_POSTGRES_PASSWORD", "postgres"));
        // Run the real changelog before starting JPA.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.change-log",
            () -> "classpath:db/changelog/db.changelog-master.yaml");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Test
    void concurrentFirstAccessReturnsOneLogicalWallet() throws Exception {
        String suffix = UUID.randomUUID().toString();
        User user = new User();
        user.setEmail("wallet-race-" + suffix + "@example.com");
        user.setUsername("wallet-race-" + suffix);
        user.setFullName("Wallet race " + suffix);
        User persistedUser = userRepository.saveAndFlush(user);

        int workers = 2;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<Future<UUID>> futures = java.util.stream.IntStream.range(0, workers)
                .mapToObj(i -> executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Wallet race did not start");
                    }
                    DemoWallet wallet = walletService.getOrCreateWalletEntity(persistedUser);
                    return wallet.getId();
                }))
                .collect(Collectors.toUnmodifiableList());

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<UUID> walletIds = futures.stream().map(this::get).toList();
            assertThat(walletIds).hasSize(workers).containsOnly(walletIds.get(0));
            assertThat(walletRepository.countByUserId(persistedUser.getId())).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private UUID get(Future<UUID> future) {
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new AssertionError("Concurrent wallet request failed", ex);
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set for the PostgreSQL integration test");
        }
        return value;
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }
}
