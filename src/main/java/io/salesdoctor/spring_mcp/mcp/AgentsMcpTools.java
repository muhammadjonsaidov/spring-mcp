package io.salesdoctor.spring_mcp.mcp;

import io.salesdoctor.spring_mcp.jooq.tables.records.AgentsRecord;
import io.salesdoctor.spring_mcp.repository.AgentRepository;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class AgentsMcpTools {

    private final AgentRepository agentRepository;

    public AgentsMcpTools(AgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    @McpTool(name = "createAgent",
            description = "Yangi savdo agenti yaratadi. Rollar: AGENT, SUPERVISOR, EXPEDITOR")
    public String createAgent(
            @McpToolParam(description = "To'liq ism") String fullName,
            @McpToolParam(description = "Telefon raqami") String phone,
            @McpToolParam(description = "Email (unikal)") String email,
            @McpToolParam(description = "Rol: AGENT, SUPERVISOR, EXPEDITOR") String role,
            @McpToolParam(description = "Hudud ID si (ixtiyoriy)") Long territoryId) {

        try {
            AgentsRecord agent = agentRepository.create(fullName, phone, email, role, territoryId);
            return format(agent);
        } catch (Exception e) {
            return "Xatolik: " + e.getMessage();
        }
    }

    @McpTool(name = "listAgents",
            description = "Barcha faol agentlar ro'yxatini qaytaradi")
    public String listAgents() {
        List<AgentsRecord> agents = agentRepository.findAllActive();
        if (agents.isEmpty()) return "Agentlar topilmadi.";
        return agents.stream().map(this::format).collect(Collectors.joining("\n"));
    }

    @McpTool(name = "listAgentsByTerritory",
            description = "Berilgan hududdagi agentlarni ko'rsatadi")
    public String listAgentsByTerritory(
            @McpToolParam(description = "Hudud ID si") Long territoryId) {
        List<AgentsRecord> agents = agentRepository.findByTerritory(territoryId);
        if (agents.isEmpty()) return "Bu hududda agentlar topilmadi.";
        return agents.stream().map(this::format).collect(Collectors.joining("\n"));
    }

    @McpTool(name = "listAgentsByRole",
            description = "Rol bo'yicha agentlarni ko'rsatadi (AGENT, SUPERVISOR, EXPEDITOR)")
    public String listAgentsByRole(
            @McpToolParam(description = "Rol") String role) {
        List<AgentsRecord> agents = agentRepository.findByRole(role);
        if (agents.isEmpty()) return "'" + role + "' rolidagi agentlar topilmadi.";
        return agents.stream().map(this::format).collect(Collectors.joining("\n"));
    }

    @McpTool(name = "getAgent",
            description = "Agentni ID bo'yicha topadi")
    public String getAgent(
            @McpToolParam(description = "Agent ID si") Long id) {
        AgentsRecord agent = agentRepository.findById(id);
        if (agent == null) return "Agent topilmadi: ID=" + id;
        return format(agent);
    }

    @McpTool(name = "getAgentByEmail",
            description = "Agentni Email bo'yicha topadi")
    public String getAgentByEmail(
            @McpToolParam(description = "Agent Emaili") String email
    ) {
        AgentsRecord agent = agentRepository.findByEmail(email);
        if (agent == null) return "Agent topilmadi: Email=" + email;
        return format(agent);
    }

    @McpTool(name = "searchAgents",
            description = "Agentlarni ismi bo'yicha qidiradi")
    public String searchAgents(
            @McpToolParam(description = "Qidiruv so'zi") String query) {
        List<AgentsRecord> agents = agentRepository.searchByName(query);
        if (agents.isEmpty()) return "'" + query + "' bo'yicha agent topilmadi.";
        return agents.stream().map(this::format).collect(Collectors.joining("\n"));
    }

    @McpTool(name = "updateAgentTerritory",
            description = "Agentning hududini o'zgartiradi")
    public String updateAgentTerritory(
            @McpToolParam(description = "Agent ID si") Long agentId,
            @McpToolParam(description = "Yangi hudud ID si") Long territoryId) {
        int updated = agentRepository.updateTerritory(agentId, territoryId);
        return updated > 0
                ? "Agent hududi o'zgartirildi: agentId=" + agentId + ", territoryId=" + territoryId
                : "Agent topilmadi: ID=" + agentId;
    }

    @McpTool(name = "updateAgentRole",
    description = "Agentning rolini o'zgartiradi")
    public String updateAgentRole(
            @McpToolParam(description = "Agent ID si") Long agentId,
            @McpToolParam(description = "Yangi role") String newRole
    ) {
        int updated = agentRepository.updateRole(agentId, newRole);

        return updated > 0 ? "Role o'zgartirildi: ID=" + agentId
                : "Role o'zgartirib bo'lmadi: ID=" + agentId;
    }

    @McpTool(name = "deactivateAgent",
            description = "Agentni faolsizlantiradi (soft delete)")
    public String deactivateAgent(
            @McpToolParam(description = "Agent ID si") Long id) {
        int updated = agentRepository.deactivate(id);
        return updated > 0 ? "Agent faolsizlantirildi: ID=" + id
                : "Agent topilmadi: ID=" + id;
    }

    private String format(AgentsRecord a) {
        return String.format(
                "{\"id\": %d, \"fullName\": \"%s\", \"phone\": \"%s\", " +
                        "\"email\": \"%s\", \"role\": \"%s\", \"territoryId\": %s}",
                a.getId(),
                esc(a.getFullName()),
                esc(a.getPhone()),
                esc(a.getEmail()),
                a.getRole(),
                a.getTerritoryId() == null ? "null" : a.getTerritoryId().toString()
        );
    }

    private String esc(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}
