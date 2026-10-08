package io.salesdoctor.spring_mcp.mcp;

import io.salesdoctor.spring_mcp.jooq.tables.records.CustomersRecord;
import io.salesdoctor.spring_mcp.repository.CustomerRepository;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class CustomerMcpTools {

    private final CustomerRepository customerRepository;

    public CustomerMcpTools(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @McpTool(name = "createCustomer",
            description = "Yangi mijoz (savdo nuqtasi) yaratadi va uning ID sini qaytaradi")
    public String createCustomer(
            @McpToolParam(description = "Mijoz nomi") String name,
            @McpToolParam(description = "Manzil") String address,
            @McpToolParam(description = "Telefon raqami") String phone
    ) {
        CustomersRecord customer = customerRepository.create(name, address, phone, null);
        return formatCustomer(customer);
    }

    @McpTool(name = "listCustomers",
            description = "Barcha faol mijozlar ro'yxatini qaytaradi")
    public String listCustomers() {
        List<CustomersRecord> customers = customerRepository.findAll();
        if (customers.isEmpty()) return "Mijozlar topilmadi.";

        return customers.stream()
                .map(this::formatCustomer)
                .collect(Collectors.joining("\n"));
    }

    @McpTool(name = "searchCustomers",
            description = "Mijozlarni nomi bo'yicha qidiradi")
    public String searchCustomers(
            @McpToolParam(description = "Qidiruv so'zi") String query
    ) {
        List<CustomersRecord> customers = customerRepository.searchByName(query);
        if (customers.isEmpty()) return "'" + query + "' bo'yicha mijoz topilmadi.";

        return customers.stream()
                .map(this::formatCustomer)
                .collect(Collectors.joining("\n"));
    }

    @McpTool(name = "getCustomer",
            description = "Mijozni ID bo'yicha topadi")
    public String getCustomer(
            @McpToolParam(description = "Mijoz ID si") Long id
    ) {
        CustomersRecord customer = customerRepository.findById(id);
        if (customer == null) return "Mijoz topilmadi: ID=" + id;

        return formatCustomer(customer);
    }

    @McpTool(name = "updateDebt",
            description = "Mijoz qarzini yangilaydi")
    public String updateCustomerDebt(
            @McpToolParam(description = "Mijoz ID si") Long id,
            @McpToolParam(description = "Yangi qarz summasi") BigDecimal newDebt
    ) {
        if (newDebt == null || newDebt.signum() < 0) return "Qarz summasi noto'g'ri: " + newDebt;

        int updated = customerRepository.updateDebt(id, newDebt);
        if (updated == 0) return "Mijoz topilmadi: ID=" + id;

        CustomersRecord customer = customerRepository.findById(id);
        return customer != null ? formatCustomer(customer) : "Mijoz qarzi yangilandi: ID=" + id;
    }

    @McpTool(name = "deleteCustomer",
            description = "Mijozni o'chiradi (soft delete)")
    public String deleteCustomer(
            @McpToolParam(description = "Mijoz ID si") Long id
    ) {
        int deleted = customerRepository.softDelete(id);
        return deleted > 0 ? "Mijoz o'chirildi: ID=" + id : "Mijoz topilmadi: ID=" + id;
    }


    private String formatCustomer(CustomersRecord c) {
        return String.format(
                "{\"id\": %d, \"name\": \"%s\", \"address\": \"%s\", " +
                        "\"phone\": \"%s\", \"debt\": %s}",
                c.getId(),
                escape(c.getName()),
                escape(c.getAddress()),
                escape(c.getPhone()),
                c.getDebtAmount()
        );
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}
