package io.salesdoctor.spring_mcp.mcp;

import io.salesdoctor.spring_mcp.dto.CustomerDto;
import io.salesdoctor.spring_mcp.dto.DeletedDto;
import io.salesdoctor.spring_mcp.repository.CustomerRepository;
import io.salesdoctor.spring_mcp.repository.TerritoryRepository;
import io.salesdoctor.spring_mcp.support.Require;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class CustomerMcpTools {

    private final CustomerRepository customerRepository;
    private final TerritoryRepository territoryRepository;

    public CustomerMcpTools(CustomerRepository customerRepository, TerritoryRepository territoryRepository) {
        this.customerRepository = customerRepository;
        this.territoryRepository = territoryRepository;
    }

    @McpTool(name = "createCustomer",
            description = "Yangi mijoz (savdo nuqtasi) yaratadi va uning ID sini qaytaradi")
    public CustomerDto createCustomer(
            @McpToolParam(description = "Mijoz nomi") String name,
            @McpToolParam(description = "Manzil") String address,
            @McpToolParam(description = "Telefon raqami") String phone,
            @McpToolParam(description = "Hudud ID si (ixtiyoriy)", required = false) Long territoryId
    ) {
        Require.notBlank(name, "Mijoz nomi bo'sh bo'lishi mumkin emas.");
        if (territoryId != null) {
            Require.found(territoryRepository.findById(territoryId), "Hudud topilmadi: ID=" + territoryId);
        }
        return CustomerDto.from(customerRepository.create(name, address, phone, territoryId));
    }

    @McpTool(name = "listCustomers",
            description = "Barcha faol mijozlar ro'yxatini qaytaradi")
    public List<CustomerDto> listCustomers() {
        return customerRepository.findAll().stream().map(CustomerDto::from).toList();
    }

    @McpTool(name = "searchCustomers",
            description = "Mijozlarni nomi bo'yicha qidiradi")
    public List<CustomerDto> searchCustomers(
            @McpToolParam(description = "Qidiruv so'zi") String query
    ) {
        return customerRepository.searchByName(query).stream().map(CustomerDto::from).toList();
    }

    @McpTool(name = "getCustomer",
            description = "Mijozni ID bo'yicha topadi")
    public CustomerDto getCustomer(
            @McpToolParam(description = "Mijoz ID si") Long id
    ) {
        return CustomerDto.from(Require.found(customerRepository.findById(id), "Mijoz topilmadi: ID=" + id));
    }

    @McpTool(name = "updateDebt",
            description = "Mijoz qarzini yangilaydi")
    public CustomerDto updateCustomerDebt(
            @McpToolParam(description = "Mijoz ID si") Long id,
            @McpToolParam(description = "Yangi qarz summasi (0 yoki katta)") BigDecimal newDebt
    ) {
        Require.that(newDebt != null && newDebt.signum() >= 0, "Qarz summasi noto'g'ri: " + newDebt);
        Require.that(customerRepository.updateDebt(id, newDebt) > 0, "Mijoz topilmadi: ID=" + id);
        return getCustomer(id);
    }

    @McpTool(name = "deleteCustomer",
            description = "Mijozni o'chiradi (soft delete)")
    public DeletedDto deleteCustomer(
            @McpToolParam(description = "Mijoz ID si") Long id
    ) {
        Require.that(customerRepository.softDelete(id) > 0, "Mijoz topilmadi: ID=" + id);
        return new DeletedDto("customer", id);
    }
}
