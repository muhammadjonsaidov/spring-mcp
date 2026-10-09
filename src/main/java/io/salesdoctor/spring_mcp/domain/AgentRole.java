package io.salesdoctor.spring_mcp.domain;

/**
 * Agent rollari. Bazada (agents.role) name() ko'rinishida saqlanadi.
 */
public enum AgentRole {

    /** Savdo agenti: buyurtma oladi, KPI hisobotlarida ko'rinadi */
    AGENT,
    SUPERVISOR,
    EXPEDITOR;

    /** Tool tavsiflari uchun; AgentRoleTest enum bilan mosligini tekshiradi. */
    public static final String ALLOWED = "AGENT, SUPERVISOR, EXPEDITOR";

    public static final AgentRole DEFAULT = AGENT;

    /**
     * Bo'sh qiymat uchun DEFAULT qaytaradi.
     */
    public static AgentRole parseOrDefault(String value) {
        return Enums.parse(AgentRole.class, value, DEFAULT, "rol");
    }

    public static AgentRole parse(String value) {
        return Enums.parse(AgentRole.class, value, null, "rol");
    }
}
