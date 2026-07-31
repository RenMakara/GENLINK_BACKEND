package kh.edu.acleda.campuslink.modules.finance;

import java.math.BigDecimal;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.modules.finance.payment.MockKhqrProvider;
import kh.edu.acleda.campuslink.modules.finance.payment.PaymentInstruction;
import kh.edu.acleda.campuslink.modules.finance.payment.PaymentResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockKhqrProviderTest {

    private final MockKhqrProvider provider = new MockKhqrProvider();

    @Test
    void approvesTheChargeAndReturnsTheMockIdentity() {
        PaymentResult result = provider.charge(
            new PaymentInstruction(new BigDecimal("10.00"), Currency.USD, "Brown Coffee", "iced latte"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.provider()).isEqualTo("MOCK_KHQR");
        assertThat(result.providerRef()).startsWith("MOCK-");
        assertThat(result.failureReason()).isNull();
    }

    @Test
    void mintsAFreshReferenceEachTime() {
        PaymentInstruction instruction =
            new PaymentInstruction(new BigDecimal("5.00"), Currency.KHR, "Shop", null);

        assertThat(provider.charge(instruction).providerRef())
            .isNotEqualTo(provider.charge(instruction).providerRef());
    }
}
