package io.salesdoctor.spring_mcp.mcp;

import io.salesdoctor.spring_mcp.jooq.tables.records.CategoriesRecord;
import io.salesdoctor.spring_mcp.repository.CategoryRepository;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class CategoryMcpTools {

    private final CategoryRepository categoryRepository;


    public CategoryMcpTools(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @McpTool(name = "createCategory",
            description = "Yangi mahsulot kategoriyasi yaratadi. parentId bo'sh bo'lsa, asosiy kategoriya bo'ladi.")
    public String createCategory(
            @McpToolParam(description = "Kategoriya nomi") String name,
            @McpToolParam(description = "Ota-kategoriya ID si (ixtiyoriy)") Long parentId) {

        CategoriesRecord category = categoryRepository.create(name, parentId);
        return format(category);
    }

    @McpTool(name = "listCategories",
            description = "Barcha kategoriyalarni ro'yxatini qaytaradi")
    public String listCategories() {
        List<CategoriesRecord> categories = categoryRepository.findAll();
        if (categories.isEmpty()) return "Kategoriyalar topilmadi.";
        return categories.stream().map(this::format).collect(Collectors.joining("\n"));
    }

    @McpTool(name = "listRootCategories",
            description = "Faqat asosiy (ota-kategoriyasiz) kategoriyalarni ko'rsatadi")
    public String listRootCategories() {
        List<CategoriesRecord> categories = categoryRepository.findRoots();
        if (categories.isEmpty()) return "Asosiy kategoriyalar topilmadi.";
        return categories.stream().map(this::format).collect(Collectors.joining("\n"));
    }

    @McpTool(name = "listChildCategories",
            description = "Berilgan kategoriyaning ichki kategoriyalarini ko'rsatadi")
    public String listChildCategories(
            @McpToolParam(description = "Ota-kategoriya ID si") Long parentId) {
        List<CategoriesRecord> categories = categoryRepository.findChildren(parentId);
        if (categories.isEmpty()) return "Ichki kategoriyalar topilmadi: parentId=" + parentId;
        return categories.stream().map(this::format).collect(Collectors.joining("\n"));
    }

    @McpTool(name = "getCategory",
            description = "Kategoriyani ID bo'yicha topadi")
    public String getCategory(
            @McpToolParam(description = "Kategoriya ID si") Long id) {
        CategoriesRecord category = categoryRepository.findById(id);
        if (category == null) return "Kategoriya topilmadi: ID=" + id;
        return format(category);
    }

    @McpTool(name = "searchCategories",
            description = "Kategoriyalarni nomi bo'yicha qidiradi")
    public String searchCategories(
            @McpToolParam(description = "Qidiruv so'zi") String query) {
        List<CategoriesRecord> categories = categoryRepository.searchByName(query);
        if (categories.isEmpty()) return "'" + query + "' bo'yicha kategoriya topilmadi.";
        return categories.stream().map(this::format).collect(Collectors.joining("\n"));
    }

    @McpTool(name = "renameCategory",
            description = "Kategoriya nomini o'zgartiradi")
    public String renameCategory(
            @McpToolParam(description = "Kategoriya ID si") Long id,
            @McpToolParam(description = "Yangi nom") String newName) {
        int updated = categoryRepository.rename(id, newName);
        return updated > 0 ? "Kategoriya nomi o'zgartirildi: ID=" + id
                : "Kategoriya topilmadi: ID=" + id;
    }

    @McpTool(name = "deleteCategory",
            description = "Kategoriyani o'chiradi (agar mahsulotlari va ichki kategoriyalari bo'lmasa)")
    public String deleteCategory(
            @McpToolParam(description = "Kategoriya ID si") Long id) {

        // Mahsulotlar borligini tekshirish
        int productCount = categoryRepository.countProducts(id);
        if (productCount > 0) {
            return "Xatolik: bu kategoriyada " + productCount + " ta mahsulot bor. Avval ularni boshqa kategoriyaga o'tkazing.";
        }

        try {
            int deleted = categoryRepository.delete(id);
            return deleted > 0 ? "Kategoriya o'chirildi: ID=" + id
                    : "Kategoriya topilmadi: ID=" + id;
        } catch (Exception e) {
            return "Xatolik: kategoriyani o'chirib bo'lmadi (ichki kategoriyalar mavjud)";
        }
    }

    private String format(CategoriesRecord c) {
        return String.format(
                "{\"id\": %d, \"name\": \"%s\", \"parentId\": %s}",
                c.getId(),
                esc(c.getName()),
                c.getParentId() == null ? "null" : c.getParentId().toString()
        );
    }

    private String esc(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}
