package io.salesdoctor.spring_mcp.mcp;

import io.salesdoctor.spring_mcp.jooq.tables.records.TerritoriesRecord;
import io.salesdoctor.spring_mcp.repository.TerritoryRepository;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class TerritoryMcpTools {

    private final TerritoryRepository territoryRepository;

    public TerritoryMcpTools(TerritoryRepository territoryRepository) {
        this.territoryRepository = territoryRepository;
    }

    @McpTool(name = "createTerritory",
            description = "Yangi hudud yaratadi. parentID bo'sh bo'lsa, asosiy hudud bo'ladi.")
    public String createTerritory(
            @McpToolParam(description = "Hudud nomi") String name,
            @McpToolParam(description = "Ota-hudud ID si (iixtiyoriy)") Long parentId
    ) {
        TerritoriesRecord territory = territoryRepository.create(name, parentId);
        return format(territory);
    }

    @McpTool(name = "listTerritories",
            description = "Barcha hududlarni ro'yxatini qaytaradi")
    public String listTerritories() {
        List<TerritoriesRecord> territories = territoryRepository.findAll();
        if (territories.isEmpty()) return "Hududlar topilmadi.";
        return territories.stream().map(this::format).collect(Collectors.joining("\n"));
    }

    @McpTool(name = "listRootTerritories",
            description = "Faqat asosiy (parent-territorysiz) hududlarni ko'rsatadi")
    public String listRootTerritories() {
        List<TerritoriesRecord> territories = territoryRepository.findRoots();
        if (territories.isEmpty()) return "Asosiy hududlar topilmadi.";
        return territories.stream().map(this::format).collect(Collectors.joining("\n"));
    }

    @McpTool(name = "listChildTerritories",
            description = "Berilgan hududning ichki hududlarini ko'rsatadi")
    public String listChildTerritories(
            @McpToolParam(description = "Parent-territory ID si") Long parentId
    ) {
        List<TerritoriesRecord> territories = territoryRepository.findChildren(parentId);
        if (territories.isEmpty()) return "Ichki hududlar topilmadi: parentId=" + parentId;
        return territories.stream().map(this::format).collect(Collectors.joining("\n"));
    }

    @McpTool(name = "getTerritory",
            description = "Hududni ID bo'yicha topadi")
    public String getTerritory(
            @McpToolParam(description = "Hudud ID si") Long id
    ) {
        TerritoriesRecord territory = territoryRepository.findById(id);
        if (territory == null) return "Hudud topilmadi: ID=" + id;
        return format(territory);
    }

    @McpTool(name = "renameTerritory",
            description = "Hudud nomini o'zgartiradi")
    public String renameTerritory(
            @McpToolParam(description = "Hudud ID si") Long id,
            @McpToolParam(description = "Yangi nom") String newName
    ) {
        int updated = territoryRepository.rename(id, newName);
        return updated > 0 ? "Hudud nomi o'zgartirildi: ID=" + id
                : "Hudud topilmadi: ID=" + id;
    }

    @McpTool(name = "deleteTerritory",
            description = "Hududni o'chiradi (agar ichki hududlari bo'lmasa)")
    public String deleteTerritory(
            @McpToolParam(description = "Hudud ID si") Long id
    ) {
        try {
            int deleted = territoryRepository.delete(id);
            return deleted > 0 ? "Hudud o'chirildi: ID=" + id
                    : "Hudud topilmadi: ID=" + id;
        } catch (Exception e) {
            return "Xatolik: hududni o'chirib bo'lmadi (ichki hududlar yoki agentlar mavjud)";
        }
    }

    private String format(TerritoriesRecord t) {
        return String.format(
                "{\"id\": %d, \"name\": \"%s\", \"parentId\": %s}",
                t.getId(),
                esc(t.getName()),
                t.getParentId() == null ? "null" : t.getParentId().toString()
        );
    }

    private String esc(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}
