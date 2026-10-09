package io.salesdoctor.spring_mcp.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tool tavsiflaridagi konstanta matnlar enum'ga yangi qiymat qo'shilganda eskirib qolmasligi uchun.
 */
class DomainEnumsTest {

    @Test
    void descriptionConstantsMatchEnumValues() {
        assertThat(OrderStatus.ALLOWED).isEqualTo(Enums.allowed(OrderStatus.class));
        assertThat(ReturnStatus.ALLOWED).isEqualTo(Enums.allowed(ReturnStatus.class));
        assertThat(AgentRole.ALLOWED).isEqualTo(Enums.allowed(AgentRole.class));
        assertThat(PaymentMethod.ALLOWED).isEqualTo(Enums.allowed(PaymentMethod.class));
    }

    @Test
    void orderTransitionsTextMatchesRules() {
        assertThat(OrderStatus.TRANSITIONS_TEXT).isEqualTo(OrderStatus.describeTransitions());
    }

    @Test
    void orderTransitions() {
        assertThat(OrderStatus.NEW.canTransitionTo(OrderStatus.CONFIRMED)).isTrue();
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.NEW)).isFalse();
        assertThat(OrderStatus.DELIVERED.isFinal()).isTrue();
        assertThat(OrderStatus.CANCELLED.isFinal()).isTrue();
        assertThat(OrderStatus.active()).doesNotContain(OrderStatus.CANCELLED);
    }

    @Test
    void returnTransitions() {
        assertThat(ReturnStatus.PENDING.canTransitionTo(ReturnStatus.APPROVED)).isTrue();
        assertThat(ReturnStatus.APPROVED.canTransitionTo(ReturnStatus.REJECTED)).isFalse();
    }

    @Test
    void parsingIsCaseInsensitiveAndListsAllowedValues() {
        assertThat(OrderStatus.parse(" cancelled ")).isEqualTo(OrderStatus.CANCELLED);
        assertThat(PaymentMethod.parseOrDefault(null)).isEqualTo(PaymentMethod.CASH);
        assertThat(AgentRole.parseOrDefault("")).isEqualTo(AgentRole.AGENT);

        assertThatThrownBy(() -> AgentRole.parse("xyz"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AGENT, SUPERVISOR, EXPEDITOR");
        assertThatThrownBy(() -> OrderStatus.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stockLocations() {
        assertThat(StockLocation.main()).isEqualTo("MAIN");
        assertThat(StockLocation.van(3L)).isEqualTo("VAN-3");
    }
}
