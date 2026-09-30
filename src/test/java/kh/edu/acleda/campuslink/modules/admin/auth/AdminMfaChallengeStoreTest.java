package kh.edu.acleda.campuslink.modules.admin.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** ADM-023 AC#4, plus the single-use decision recorded in {@link AdminMfaChallengeStore}. */
class AdminMfaChallengeStoreTest {

    private final AdminMfaChallengeStore store = new AdminMfaChallengeStore();

    @Test
    void anUnseenChallengeIsUsableWithAFullBudget() {
        assertThat(store.isUsable("fresh")).isTrue();
        assertThat(store.remainingAttempts("fresh")).isEqualTo(5);
    }

    /** AC#4: five wrong codes void the challenge. */
    @Test
    void fiveFailuresVoidTheChallenge() {
        for (int attempt = 1; attempt <= 4; attempt++) {
            store.recordFailure("c1");
            assertThat(store.isUsable("c1"))
                .as("still usable after %d failures", attempt)
                .isTrue();
        }

        store.recordFailure("c1");

        assertThat(store.isUsable("c1")).isFalse();
        assertThat(store.remainingAttempts("c1")).isZero();
    }

    @Test
    void countsDownRemainingAttempts() {
        store.recordFailure("c1");
        store.recordFailure("c1");
        assertThat(store.remainingAttempts("c1")).isEqualTo(3);
    }

    /** Budgets are per challenge - a fresh sign-in must not inherit the last one's failures. */
    @Test
    void challengesDoNotShareABudget() {
        for (int i = 0; i < 5; i++) {
            store.recordFailure("c1");
        }

        assertThat(store.isUsable("c1")).isFalse();
        assertThat(store.isUsable("c2")).isTrue();
        assertThat(store.remainingAttempts("c2")).isEqualTo(5);
    }

    /** The single-use decision. */
    @Test
    void aSpentChallengeCannotBeUsedAgain() {
        store.spend("c1");

        assertThat(store.isUsable("c1")).isFalse();
        assertThat(store.remainingAttempts("c1")).isZero();
    }

    /** Spending must survive earlier failures, not reset them. */
    @Test
    void spendingAfterSomeFailuresStillRetiresIt() {
        store.recordFailure("c1");
        store.recordFailure("c1");
        store.spend("c1");

        assertThat(store.isUsable("c1")).isFalse();
    }

    /** Retired, not forgotten. */
    @Test
    void failingAgainstASpentChallengeDoesNotReviveIt() {
        store.spend("c1");
        store.recordFailure("c1");

        assertThat(store.isUsable("c1")).isFalse();
    }

    @Test
    void survivesConcurrentFailuresWithoutLosingCount() throws InterruptedException {
        // The map is shared across request threads; a lost update here is a free extra guess.
        Thread[] threads = new Thread[5];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> store.recordFailure("shared"));
            threads[i].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        assertThat(store.isUsable("shared")).isFalse();
    }
}
