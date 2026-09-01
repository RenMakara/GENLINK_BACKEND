package kh.edu.acleda.campuslink.modules.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import kh.edu.acleda.campuslink.modules.finance.entity.DemoWallet;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionDirection;
import kh.edu.acleda.campuslink.modules.finance.enums.TransactionType;
import kh.edu.acleda.campuslink.modules.finance.repository.DemoTransactionRepository;
import kh.edu.acleda.campuslink.modules.finance.repository.DemoWalletRepository;
import kh.edu.acleda.campuslink.modules.finance.service.LedgerService;
import kh.edu.acleda.campuslink.modules.finance.service.DemoWalletService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class WalletConcurrencyIntegrationTest {

    /**
     * CI supplies a disposable PostgreSQL URL so these mutation races run against
     * the same database engine used in production. Local runs without the variable
     * continue to use the H2 test profile.
     */
    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        String url = System.getenv("GENLINK_TEST_POSTGRES_URL");
        if (url != null && !url.isBlank()) {
            registry.add("spring.datasource.url", () -> url);
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
            registry.add("spring.datasource.username",
                () -> envOrDefault("GENLINK_TEST_POSTGRES_USERNAME", "postgres"));
            registry.add("spring.datasource.password",
                () -> envOrDefault("GENLINK_TEST_POSTGRES_PASSWORD", "postgres"));
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
            registry.add("spring.liquibase.enabled", () -> "false");
            registry.add("spring.flyway.enabled", () -> "false");
        }
    }

    @Autowired private LedgerService ledgerService;
    @Autowired private DemoWalletService walletService;
    @Autowired private DemoWalletRepository walletRepository;
    @Autowired private DemoTransactionRepository transactionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void concurrentCreditsDoNotLoseUpdates() throws Exception {
        DemoWallet wallet = walletWithUsd("credit", new BigDecimal("100.00"));
        int workers = 4;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<Future<?>> futures = java.util.stream.IntStream.range(0, workers)
                .mapToObj(i -> executor.submit(() -> {
                    awaitTogether(ready, start);
                    TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
                    txTemplate.execute(status -> {
                        DemoWallet detached = walletRepository.findById(wallet.getId()).orElseThrow();
                        ledgerService.credit(detached, TransactionType.DEPOSIT, Currency.USD,
                            new BigDecimal("10.00"), "Concurrent credit " + i);
                        return null;
                    });
                }))
                    .collect(Collectors.toUnmodifiableList());
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        DemoWallet refreshed = walletRepository.findById(wallet.getId()).orElseThrow();
        assertThat(refreshed.getBalanceUsd()).isEqualByComparingTo("140.00");
        assertThat(transactionRepository.countByWalletIdAndDirection(
            wallet.getId(), TransactionDirection.CREDIT)).isEqualTo(workers);
    }

    @Test
    void concurrentDebitsCannotOverspend() throws Exception {
        DemoWallet wallet = walletWithUsd("debit", new BigDecimal("100.00"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> debitTogether(wallet.getId(), ready, start));
            Future<Boolean> second = executor.submit(() -> debitTogether(wallet.getId(), ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }

        DemoWallet refreshed = walletRepository.findById(wallet.getId()).orElseThrow();
        assertThat(refreshed.getBalanceUsd()).isEqualByComparingTo("20.00");
        assertThat(transactionRepository.countByWalletIdAndDirection(
            wallet.getId(), TransactionDirection.DEBIT)).isEqualTo(1);
    }

    @Test
    void concurrentFirstAccessReturnsTheSameWallet() throws Exception {
        String suffix = UUID.randomUUID().toString();
        User user = new User();
        user.setEmail("first-access-" + suffix + "@example.com");
        user.setUsername("first-access-" + suffix);
        user.setFullName("First access " + suffix);
        User persistedUser = userRepository.saveAndFlush(user);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<UUID>> futures = java.util.stream.IntStream.range(0, 2)
                .mapToObj(i -> executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("First wallet access did not start");
                    }
                    return walletService.getOrCreateWalletEntity(persistedUser).getId();
                }))
                .collect(Collectors.toUnmodifiableList());
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<UUID> ids = futures.stream().map(future -> {
                try {
                    return future.get(10, TimeUnit.SECONDS);
                } catch (Exception ex) {
                    throw new AssertionError("Concurrent first wallet access failed", ex);
                }
            }).toList();
            assertThat(ids).containsOnly(ids.get(0));
            assertThat(walletRepository.countByUserId(persistedUser.getId())).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean debitTogether(UUID walletId, CountDownLatch ready, CountDownLatch start) {
        awaitTogether(ready, start);
        try {
            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
            return txTemplate.execute(status -> {
                DemoWallet detached = walletRepository.findById(walletId).orElseThrow();
                ledgerService.debit(detached, TransactionType.EXPENSE, Currency.USD,
                    new BigDecimal("80.00"), "Concurrent debit");
                return true;
            });
        } catch (ApiException ex) {
            assertThat(ex.getErrorCode()).isEqualTo("DEMO_WALLET_INSUFFICIENT_BALANCE");
            return false;
        }
    }

    private void awaitTogether(CountDownLatch ready, CountDownLatch start) {
        try {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent wallet test did not start");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private DemoWallet walletWithUsd(String prefix, BigDecimal balance) {
        String suffix = UUID.randomUUID().toString();
        User user = new User();
        user.setEmail(prefix + "-" + suffix + "@example.com");
        user.setUsername(prefix + "-" + suffix);
        user.setFullName("Wallet " + suffix);
        user = userRepository.saveAndFlush(user);
        return walletRepository.saveAndFlush(DemoWallet.builder()
            .user(user)
            .balanceUsd(balance)
            .balanceKhr(BigDecimal.ZERO)
            .build());
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }
}
