package io.salesdoctor.spring_mcp.mcp;

import io.salesdoctor.spring_mcp.dto.CategoryDto;
import io.salesdoctor.spring_mcp.dto.DeletedDto;
import io.salesdoctor.spring_mcp.error.ToolException;
import io.salesdoctor.spring_mcp.repository.CategoryRepository;
import io.salesdoctor.spring_mcp.support.Require;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CategoryMcpTools {

    private final CategoryRepository categoryRepository;

    public CategoryMcpTools(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @McpTool(name = "createCategory",
            description = "Yangi mahsulot kategoriyasi yaratadi. parentId bo'sh bo'lsa, asosiy kategoriya bo'ladi.")
    public CategoryDto createCategory(
            @McpToolParam(description = "Kategoriya nomi") String name,
            @McpToolParam(description = "Ota-kategoriya ID si (ixtiyoriy)", required = false) Long parentId) {

        Require.notBlank(name, "Kategoriya nomi bo'sh bo'lishi mumkin emas.");
        if (parentId != null) {
            Require.found(categoryRepository.findById(parentId), "Ota-kategoriya topilmadi: ID=" + parentId);
        }
        return CategoryDto.from(categoryRepository.create(name, parentId));
    }

    @McpTool(name = "listCategories",
            description = "Barcha kategoriyalarni ro'yxatini qaytaradi")
    public List<CategoryDto> listCategories() {
        return categoryRepository.findAll().stream().map(CategoryDto::from).toList();
    }

    @McpTool(name = "listRootCategories",
            description = "Faqat asosiy (ota-kategoriyasiz) kategoriyalarni ko'rsatadi")
    public List<CategoryDto> listRootCategories() {
        return categoryRepository.findRoots().stream().map(CategoryDto::from).toList();
    }

    @McpTool(name = "listChildCategories",
            description = "Berilgan kategoriyaning ichki kategoriyalarini ko'rsatadi")
    public List<CategoryDto> listChildCategories(
            @McpToolParam(description = "Ota-kategoriya ID si") Long parentId) {
        return categoryRepository.findChildren(parentId).stream().map(CategoryDto::from).toList();
    }

    @McpTool(name = "getCategory",
            description = "Kategoriyani ID bo'yicha topadi")
    public CategoryDto getCategory(
            @McpToolParam(description = "Kategoriya ID si") Long id) {
        return CategoryDto.from(Require.found(categoryRepository.findById(id), "Kategoriya topilmadi: ID=" + id));
    }

    @McpTool(name = "searchCategories",
            description = "Kategoriyalarni nomi bo'yicha qidiradi")
    public List<CategoryDto> searchCategories(
            @McpToolParam(description = "Qidiruv so'zi") String query) {
        return categoryRepository.searchByName(query).stream().map(CategoryDto::from).toList();
    }

    @McpTool(name = "renameCategory",
            description = "Kategoriya nomini o'zgartiradi")
    public CategoryDto renameCategory(
            @McpToolParam(description = "Kategoriya ID si") Long id,
            @McpToolParam(description = "Yangi nom") String newName) {
        Require.notBlank(newName, "Kategoriya nomi bo'sh bo'lishi mumkin emas.");
        Require.affected(categoryRepository.rename(id, newName), "Kategoriya topilmadi: ID=" + id);
        return getCategory(id);
    }

    @McpTool(name = "deleteCategory",
            description = "Kategoriyani o'chiradi (agar mahsulotlari va ichki kategoriyalari bo'lmasa)")
    public DeletedDto deleteCategory(
            @McpToolParam(description = "Kategoriya ID si") Long id) {

        int productCount = categoryRepository.countProducts(id);
        if (productCount > 0) {
            throw ToolException.conflict("Bu kategoriyada " + productCount +
                    " ta mahsulot bor. Avval ularni boshqa kategoriyaga o'tkazing.");
        }

        int deleted;
        try {
            deleted = categoryRepository.delete(id);
        } catch (DataIntegrityViolationException e) {
            throw ToolException.conflict("Kategoriyani o'chirib bo'lmadi: ichki kategoriyalar mavjud. ID=" + id);
        }
        Require.affected(deleted, "Kategoriya topilmadi: ID=" + id);
        return new DeletedDto("category", id);
    }
}
