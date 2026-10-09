package io.salesdoctor.spring_mcp.mcp;

import io.salesdoctor.spring_mcp.dto.DeletedDto;
import io.salesdoctor.spring_mcp.dto.TerritoryDto;
import io.salesdoctor.spring_mcp.repository.TerritoryRepository;
import io.salesdoctor.spring_mcp.support.Require;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TerritoryMcpTools {

    private final TerritoryRepository territoryRepository;

    public TerritoryMcpTools(TerritoryRepository territoryRepository) {
        this.territoryRepository = territoryRepository;
    }

    @McpTool(name = "createTerritory",
            description = "Yangi hudud yaratadi. parentID bo'sh bo'lsa, asosiy hudud bo'ladi.")
    public TerritoryDto createTerritory(
            @McpToolParam(description = "Hudud nomi") String name,
            @McpToolParam(description = "Ota-hudud ID si (ixtiyoriy)", required = false) Long parentId
    ) {
        Require.notBlank(name, "Hudud nomi bo'sh bo'lishi mumkin emas.");
        if (parentId != null) {
            Require.found(territoryRepository.findById(parentId), "Ota-hudud topilmadi: ID=" + parentId);
        }
        return TerritoryDto.from(territoryRepository.create(name, parentId));
    }

    @McpTool(name = "listTerritories",
            description = "Barcha hududlarni ro'yxatini qaytaradi")
    public List<TerritoryDto> listTerritories() {
        return territoryRepository.findAll().stream().map(TerritoryDto::from).toList();
    }

    @McpTool(name = "listRootTerritories",
            description = "Faqat asosiy (parent-territorysiz) hududlarni ko'rsatadi")
    public List<TerritoryDto> listRootTerritories() {
        return territoryRepository.findRoots().stream().map(TerritoryDto::from).toList();
    }

    @McpTool(name = "listChildTerritories",
            description = "Berilgan hududning ichki hududlarini ko'rsatadi")
    public List<TerritoryDto> listChildTerritories(
            @McpToolParam(description = "Parent-territory ID si") Long parentId
    ) {
        return territoryRepository.findChildren(parentId).stream().map(TerritoryDto::from).toList();
    }

    @McpTool(name = "getTerritory",
            description = "Hududni ID bo'yicha topadi")
    public TerritoryDto getTerritory(
            @McpToolParam(description = "Hudud ID si") Long id
    ) {
        return TerritoryDto.from(Require.found(territoryRepository.findById(id), "Hudud topilmadi: ID=" + id));
    }

    @McpTool(name = "renameTerritory",
            description = "Hudud nomini o'zgartiradi")
    public TerritoryDto renameTerritory(
            @McpToolParam(description = "Hudud ID si") Long id,
            @McpToolParam(description = "Yangi nom") String newName
    ) {
        Require.notBlank(newName, "Hudud nomi bo'sh bo'lishi mumkin emas.");
        Require.that(territoryRepository.rename(id, newName) > 0, "Hudud topilmadi: ID=" + id);
        return getTerritory(id);
    }

    @McpTool(name = "deleteTerritory",
            description = "Hududni o'chiradi (agar ichki hududlari, agentlari yoki mijozlari bo'lmasa)")
    public DeletedDto deleteTerritory(
            @McpToolParam(description = "Hudud ID si") Long id
    ) {
        int deleted;
        try {
            deleted = territoryRepository.delete(id);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException(
                    "Hududni o'chirib bo'lmadi: unga ichki hududlar, agentlar yoki mijozlar bog'langan. ID=" + id);
        }
        Require.that(deleted > 0, "Hudud topilmadi: ID=" + id);
        return new DeletedDto("territory", id);
    }
}
