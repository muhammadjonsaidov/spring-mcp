package io.salesdoctor.spring_mcp.mcp;

import io.salesdoctor.spring_mcp.jooq.tables.records.ProductsRecord;
import io.salesdoctor.spring_mcp.repository.ProductRepository;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ProductMcpTools {

    private final ProductRepository productRepository;

    public ProductMcpTools(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @McpTool(name = "createProduct",
            description = "Yangi mahsulot yaratadi (kategoriya ID si bilan)")
    public String createProduct(
            @McpToolParam(description = "Mahsulot nomi") String name,
            @McpToolParam(description = "SKU (unikal kod)") String sku,
            @McpToolParam(description = "Narxi (so'mda)") BigDecimal price,
            @McpToolParam(description = "Kategoriya ID si (ixtiyoriy)") Long categoryId
    ) {
        ProductsRecord product = productRepository.createProduct(name, sku, price, categoryId);
        return format(product);
    }

    @McpTool(name = "listProducts",
            description = "Barcha faol mahsulotlar ro'yxatini qaytaradi")
    public String listProducts() {
        List<ProductsRecord> products = productRepository.findAll();
        if (products.isEmpty()) return "Mahsulotlar topilmadi.";

        return products.stream()
                .map(this::format)
                .collect(Collectors.joining("\n"));
    }

    @McpTool(name = "searchProducts",
            description = "Mahsulotlarni nomi bo'yicha qidiradi")
    public String searchProducts(
            @McpToolParam(description = "Qidirv so'zi") String query
    ) {
        List<ProductsRecord> products = productRepository.searchByName(query);
        if (products.isEmpty()) return "'" + query + "' bo'yicha mahsulot topilmadi.";
        return products.stream()
                .map(this::format)
                .collect(Collectors.joining("\n"));
    }

    @McpTool(name = "getProductById",
            description = "Mahsulotni ID bo'yicha topadi")
    public String getProductById(
            @McpToolParam(description = "Mahsulot ID si") Long id
    ) {
        ProductsRecord product = productRepository.findById(id);
        if (product == null) return "Mahsulot topilmadi: ID=" + id;
        return format(product);
    }

    @McpTool(name = "getProductBySku",
            description = "Mahsulotni SKU bo'yicha topadi")
    public String getProductBySku(
            @McpToolParam(description = "Mahsulot SKU si") String sku
    ) {
        ProductsRecord product = productRepository.findBySku(sku);
        if (product == null) return "Mahsulot topilmadi: SKU=" + sku;
        return format(product);
    }

    @McpTool(name = "updateProductPrice",
            description = "Mahsulot narxini yangilaydi")
    public String updateProductPrice(
            @McpToolParam(description = "Mahsulot ID si") Long id,
            @McpToolParam(description = "Yangi narx") BigDecimal newPrice
    ) {
        if (newPrice == null || newPrice.signum() < 0) return "Yangi narx noto'g'ri: " + newPrice;

        int updated = productRepository.updatePrice(id, newPrice);
        if (updated == 0) return "Mahsulot topilmadi: ID=" + id;

        ProductsRecord product = productRepository.findById(id);
        return product != null ? format(product) : "Mahsulot narxi yangilandi: ID=" + id;
    }

    @McpTool(name = "deleteProduct",
    description = "Mahsulotni o'chiradi (soft delete)")
    public String deleteProduct(
            @McpToolParam(description = "Mahsulot ID si") Long id
    ) {
        int deleted = productRepository.softDelete(id);
        return deleted > 0 ? "Mahsulot o'chirildi: ID=" + id : "Mahsulot topilmadi: ID=" + id;
    }

    private String format(ProductsRecord p) {
        return String.format(
                "{\"id\": %d, \"name\": \"%s\", \"sku\": \"%s\", \"price\": %s}",
                p.getId(), esc(p.getName()), esc(p.getSku()), p.getPrice()
        );
    }

    private String esc(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}
