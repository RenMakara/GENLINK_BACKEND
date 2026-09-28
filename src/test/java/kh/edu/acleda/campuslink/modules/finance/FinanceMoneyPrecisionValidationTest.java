package kh.edu.acleda.campuslink.modules.finance;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import kh.edu.acleda.campuslink.modules.finance.dto.request.ExpenseCreateRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FinanceMoneyPrecisionValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void rejectsAmountsWithMoreFractionalDigitsThanTheDatabaseScale() {
        ExpenseCreateRequest request = new ExpenseCreateRequest(
            "Lunch", new BigDecimal("1.001"), null, null, LocalDate.of(2026, 8, 20), null, false, null);

        assertThat(validator.validate(request))
            .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("amount"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.001", "10.123"})
    void rejectsRepresentativeAmountsWithThreeFractionalDigits(String amount) {
        ExpenseCreateRequest request = new ExpenseCreateRequest(
            "Lunch", new BigDecimal(amount), null, null, LocalDate.of(2026, 8, 20), null, false, null);

        assertThat(validator.validate(request))
            .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("amount"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "1.00", "1.99", "10.50", "999.99"})
    void acceptsRepresentativePositiveValuesAtDatabaseScale(String amount) {
        ExpenseCreateRequest request = new ExpenseCreateRequest(
            "Lunch", new BigDecimal(amount), null, null, LocalDate.of(2026, 8, 20), null, false, null);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void zeroHasValidScaleButRemainsInvalidForPositiveTransactionInput() {
        ExpenseCreateRequest request = new ExpenseCreateRequest(
            "Lunch", new BigDecimal("0.00"), null, null, LocalDate.of(2026, 8, 20), null, false, null);

        assertThat(validator.validate(request))
            .extracting(violation -> violation.getConstraintDescriptor().getAnnotation()
                .annotationType().getSimpleName())
            .contains("Positive")
            .doesNotContain("Digits");
    }

    @Test
    void rejectsAmountsThatExceedTheDatabasePrecision() {
        ExpenseCreateRequest request = new ExpenseCreateRequest(
            "Lunch", new BigDecimal("100000000000000000.00"), null, null,
            LocalDate.of(2026, 8, 20), null, false, null);

        assertThat(validator.validate(request))
            .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("amount"));
    }

    @Test
    void acceptsTheLargestAmountRepresentableByNumeric19Scale2() {
        ExpenseCreateRequest request = new ExpenseCreateRequest(
            "Lunch", new BigDecimal("99999999999999999.99"), null, null,
            LocalDate.of(2026, 8, 20), null, false, null);

        assertThat(validator.validate(request)).isEmpty();
    }
}
