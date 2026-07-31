package kh.edu.acleda.campuslink.modules.finance.payment;

import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The default, simulation-only payment provider. It always approves and mints a fake reference -
 * no network, no bank, no KHQR generation, no credentials, no real money. It exists so the wallet
 * has something to call through the {@link PaymentProvider} seam today.
 *
 * <p>A future real provider (ACLEDA, ABA, Wing, ...) is added as another {@code PaymentProvider}
 * bean and selected with {@code app.finance.payment-provider}; nothing in the wallet or ledger
 * changes. This bean is the default when that property is absent.
 */
@Component
@ConditionalOnProperty(name = "app.finance.payment-provider", havingValue = "MOCK_KHQR", matchIfMissing = true)
public class MockKhqrProvider implements PaymentProvider {

    /** Stored on the ledger row's {@code provider} column so history shows who processed it. */
    public static final String PROVIDER_NAME = "MOCK_KHQR";

    @Override
    public PaymentResult charge(PaymentInstruction instruction) {
        // Simulation: approve everything and return a stable-shaped fake reference (MOCK-XXXXXXXX).
        String reference = "MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        return PaymentResult.success(PROVIDER_NAME, reference);
    }
}
