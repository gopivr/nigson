package com.nigson.commerce.authorization;
import org.junit.jupiter.api.Test;
import java.util.*;
import com.nigson.shared.problem.Problem;
import static org.assertj.core.api.Assertions.*;
class StaffValuesTest {
    @Test void invalidCommandsAndAliasesAreRejected() {
        for (var values : List.of(List.of(Staff.Role.ADMIN, Staff.Role.ADMIN), Arrays.asList((Staff.Role)null)))
            assertThatThrownBy(() -> new Staff.Change(values, "Synthetic reason")).isInstanceOf(Problem.Failure.class);
        for (String reason : List.of("short", "x".repeat(501), "reason\ninvalid"))
            assertThatThrownBy(() -> new Staff.Change(List.of(), reason)).isInstanceOf(Problem.Failure.class);
        var mapper = new tools.jackson.databind.json.JsonMapper();
        for (String invalid : List.of("{\"roles\":[0],\"reason\":\"Synthetic reason\"}", "{\"roles\":[],\"reason\":1234567890123}", "{\"roles\":null,\"reason\":\"Synthetic reason\"}"))
            assertThatThrownBy(() -> mapper.readValue(invalid, Staff.Change.class)).isInstanceOf(tools.jackson.core.JacksonException.class);
        assertThat(mapper.readValue("{\"roles\":[\"ADMIN\"],\"reason\":\"Synthetic reason\"}", Staff.Change.class).roles()).containsExactly(Staff.Role.ADMIN);
        var roles = new ArrayList<>(List.of(Staff.Role.SALES, Staff.Role.ADMIN)); var change = new Staff.Change(roles, "Synthetic reason"); roles.clear();
        assertThat(change.roles()).containsExactly(Staff.Role.ADMIN, Staff.Role.SALES);
        assertThatThrownBy(() -> change.roles().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
