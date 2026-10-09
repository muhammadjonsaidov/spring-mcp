package io.salesdoctor.spring_mcp.mcp;

import io.salesdoctor.spring_mcp.domain.AgentRole;
import io.salesdoctor.spring_mcp.dto.AgentDto;
import io.salesdoctor.spring_mcp.repository.AgentRepository;
import io.salesdoctor.spring_mcp.repository.TerritoryRepository;
import io.salesdoctor.spring_mcp.support.Require;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AgentsMcpTools {

    private final AgentRepository agentRepository;
    private final TerritoryRepository territoryRepository;

    public AgentsMcpTools(AgentRepository agentRepository, TerritoryRepository territoryRepository) {
        this.agentRepository = agentRepository;
        this.territoryRepository = territoryRepository;
    }

    @McpTool(name = "createAgent",
            description = "Yangi savdo agenti yaratadi. Rollar: " + AgentRole.ALLOWED)
    public AgentDto createAgent(
            @McpToolParam(description = "To'liq ism") String fullName,
            @McpToolParam(description = "Telefon raqami") String phone,
            @McpToolParam(description = "Email (unikal)") String email,
            @McpToolParam(description = "Rol: " + AgentRole.ALLOWED + " (default AGENT)", required = false) String role,
            @McpToolParam(description = "Hudud ID si (ixtiyoriy)", required = false) Long territoryId) {

        Require.notBlank(fullName, "Agent ismi bo'sh bo'lishi mumkin emas.");
        AgentRole agentRole = AgentRole.parseOrDefault(role);
        if (territoryId != null) {
            Require.found(territoryRepository.findById(territoryId), "Hudud topilmadi: ID=" + territoryId);
        }
        if (email != null && !email.isBlank()) {
            Require.that(agentRepository.findByEmail(email) == null,
                    "Bu email bilan agent allaqachon mavjud: " + email);
        }

        return AgentDto.from(agentRepository.create(fullName, phone, email, agentRole, territoryId));
    }

    @McpTool(name = "listAgents",
            description = "Barcha faol agentlar ro'yxatini qaytaradi")
    public List<AgentDto> listAgents() {
        return agentRepository.findAllActive().stream().map(AgentDto::from).toList();
    }

    @McpTool(name = "listAgentsByTerritory",
            description = "Berilgan hududdagi agentlarni ko'rsatadi")
    public List<AgentDto> listAgentsByTerritory(
            @McpToolParam(description = "Hudud ID si") Long territoryId) {
        return agentRepository.findByTerritory(territoryId).stream().map(AgentDto::from).toList();
    }

    @McpTool(name = "listAgentsByRole",
            description = "Rol bo'yicha agentlarni ko'rsatadi (" + AgentRole.ALLOWED + ")")
    public List<AgentDto> listAgentsByRole(
            @McpToolParam(description = "Rol: " + AgentRole.ALLOWED) String role) {
        return agentRepository.findByRole(AgentRole.parse(role)).stream().map(AgentDto::from).toList();
    }

    @McpTool(name = "getAgent",
            description = "Agentni ID bo'yicha topadi")
    public AgentDto getAgent(
            @McpToolParam(description = "Agent ID si") Long id) {
        return AgentDto.from(Require.found(agentRepository.findById(id), "Agent topilmadi: ID=" + id));
    }

    @McpTool(name = "getAgentByEmail",
            description = "Agentni Email bo'yicha topadi")
    public AgentDto getAgentByEmail(
            @McpToolParam(description = "Agent Emaili") String email
    ) {
        return AgentDto.from(Require.found(agentRepository.findByEmail(email), "Agent topilmadi: Email=" + email));
    }

    @McpTool(name = "searchAgents",
            description = "Agentlarni ismi bo'yicha qidiradi")
    public List<AgentDto> searchAgents(
            @McpToolParam(description = "Qidiruv so'zi") String query) {
        return agentRepository.searchByName(query).stream().map(AgentDto::from).toList();
    }

    @McpTool(name = "updateAgentTerritory",
            description = "Agentning hududini o'zgartiradi")
    public AgentDto updateAgentTerritory(
            @McpToolParam(description = "Agent ID si") Long agentId,
            @McpToolParam(description = "Yangi hudud ID si") Long territoryId) {
        Require.found(territoryRepository.findById(territoryId), "Hudud topilmadi: ID=" + territoryId);
        Require.that(agentRepository.updateTerritory(agentId, territoryId) > 0, "Agent topilmadi: ID=" + agentId);
        return getAgent(agentId);
    }

    @McpTool(name = "updateAgentRole",
            description = "Agentning rolini o'zgartiradi (" + AgentRole.ALLOWED + ")")
    public AgentDto updateAgentRole(
            @McpToolParam(description = "Agent ID si") Long agentId,
            @McpToolParam(description = "Yangi rol: " + AgentRole.ALLOWED) String newRole
    ) {
        AgentRole role = AgentRole.parse(newRole);
        Require.that(agentRepository.updateRole(agentId, role) > 0, "Agent topilmadi: ID=" + agentId);
        return getAgent(agentId);
    }

    @McpTool(name = "deactivateAgent",
            description = "Agentni faolsizlantiradi (soft delete)")
    public AgentDto deactivateAgent(
            @McpToolParam(description = "Agent ID si") Long id) {
        Require.that(agentRepository.deactivate(id) > 0, "Agent topilmadi: ID=" + id);
        return getAgent(id);
    }
}
