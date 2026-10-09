package io.salesdoctor.spring_mcp.mcp;

import io.salesdoctor.spring_mcp.dto.DeletedDto;
import io.salesdoctor.spring_mcp.dto.ProductDto;
import io.salesdoctor.spring_mcp.repository.CategoryRepository;
import io.salesdoctor.spring_mcp.repository.ProductRepository;
import io.salesdoctor.spring_mcp.support.Require;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class ProductMcpTools {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductMcpTools(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @McpTool(name = "createProduct",
            description = "Yangi mahsulot yaratadi (kategoriya ID si bilan)")
    public ProductDto createProduct(
            @McpToolParam(description = "Mahsulot nomi") String name,
            @McpToolParam(description = "SKU (unikal kod)") String sku,
            @McpToolParam(description = "Narxi (so'mda)") BigDecimal price,
            @McpToolParam(description = "Kategoriya ID si (ixtiyoriy)", required = false) Long categoryId
    ) {
        Require.notBlank(name, "Mahsulot nomi bo'sh bo'lishi mumkin emas.");
        Require.notBlank(sku, "SKU bo'sh bo'lishi mumkin emas.");
        Require.that(price != null && price.signum() >= 0, "Narx noto'g'ri: " + price);
        requireCategory(categoryId);
        Require.that(productRepository.findBySku(sku) == null, "Bu SKU bilan mahsulot allaqachon mavjud: " + sku);

        return ProductDto.from(productRepository.createProduct(name, sku, price, categoryId));
    }

    @McpTool(name = "listProducts",
            description = "Barcha faol mahsulotlar ro'yxatini qaytaradi")
    public List<ProductDto> listProducts() {
        return productRepository.findAll().stream().map(ProductDto::from).toList();
    }

    @McpTool(name = "searchProducts",
            description = "Mahsulotlarni nomi bo'yicha qidiradi")
    public List<ProductDto> searchProducts(
            @McpToolParam(description = "Qidiruv so'zi") String query
    ) {
        return productRepository.searchByName(query).stream().map(ProductDto::from).toList();
    }

    @McpTool(name = "getProductById",
            description = "Mahsulotni ID bo'yicha topadi")
    public ProductDto getProductById(
            @McpToolParam(description = "Mahsulot ID si") Long id
    ) {
        return ProductDto.from(Require.found(productRepository.findById(id), "Mahsulot topilmadi: ID=" + id));
    }

    @McpTool(name = "getProductBySku",
            description = "Mahsulotni SKU bo'yicha topadi")
    public ProductDto getProductBySku(
            @McpToolParam(description = "Mahsulot SKU si") String sku
    ) {
        return ProductDto.from(Require.found(productRepository.findBySku(sku), "Mahsulot topilmadi: SKU=" + sku));
    }

    @McpTool(name = "updateProductPrice",
            description = "Mahsulot narxini yangilaydi")
    public ProductDto updateProductPrice(
            @McpToolParam(description = "Mahsulot ID si") Long id,
            @McpToolParam(description = "Yangi narx") BigDecimal newPrice
    ) {
        Require.that(newPrice != null && newPrice.signum() >= 0, "Yangi narx noto'g'ri: " + newPrice);
        Require.that(productRepository.updatePrice(id, newPrice) > 0, "Mahsulot topilmadi: ID=" + id);
        return getProductById(id);
    }

    @McpTool(name = "updateProductCategory",
            description = "Mahsulotni kategoriyaga biriktiradi (categoryId bo'sh bo'lsa kategoriyadan chiqaradi)")
    public ProductDto updateProductCategory(
            @McpToolParam(description = "Mahsulot ID si") Long id,
            @McpToolParam(description = "Kategoriya ID si (ixtiyoriy)", required = false) Long categoryId
    ) {
        requireCategory(categoryId);
        Require.that(productRepository.updateCategory(id, categoryId) > 0, "Mahsulot topilmadi: ID=" + id);
        return getProductById(id);
    }

    @McpTool(name = "deleteProduct",
            description = "Mahsulotni o'chiradi (soft delete)")
    public DeletedDto deleteProduct(
            @McpToolParam(description = "Mahsulot ID si") Long id
    ) {
        Require.that(productRepository.softDelete(id) > 0, "Mahsulot topilmadi: ID=" + id);
        return new DeletedDto("product", id);
    }

    private void requireCategory(Long categoryId) {
        if (categoryId != null) {
            Require.found(categoryRepository.findById(categoryId), "Kategoriya topilmadi: ID=" + categoryId);
        }
    }
}
