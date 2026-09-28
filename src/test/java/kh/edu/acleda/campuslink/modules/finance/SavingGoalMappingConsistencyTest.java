package kh.edu.acleda.campuslink.modules.finance;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.JoinColumn;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import kh.edu.acleda.campuslink.modules.finance.entity.SavingGoal;
import org.junit.jupiter.api.Test;

class SavingGoalMappingConsistencyTest {

    @Test
    void ownerRelationshipIsNotNullableInEntityAndFinanceMigration() throws IOException {
        JoinColumn ownerColumn;
        try {
            ownerColumn = SavingGoal.class.getDeclaredField("user").getAnnotation(JoinColumn.class);
        } catch (NoSuchFieldException exception) {
            throw new AssertionError("SavingGoal owner relationship is missing", exception);
        }

        assertThat(ownerColumn).isNotNull();
        assertThat(ownerColumn.nullable()).isFalse();

        try (var input = getClass().getClassLoader().getResourceAsStream(
                "db/changelog/changes/0008-create-finance-tables.sql")) {
            assertThat(input).isNotNull();
            String migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(migration)
                .containsPattern("(?s)CREATE TABLE IF NOT EXISTS saving_goals\\s*\\(.*?user_id UUID NOT NULL");
        }
    }
}
