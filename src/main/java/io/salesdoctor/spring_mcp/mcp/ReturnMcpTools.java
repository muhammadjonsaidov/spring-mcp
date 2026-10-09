package io.salesdoctor.spring_mcp.mcp;

import io.salesdoctor.spring_mcp.domain.ReturnStatus;
import io.salesdoctor.spring_mcp.dto.ReturnDto;
import io.salesdoctor.spring_mcp.repository.ReturnRepository;
import io.salesdoctor.spring_mcp.support.Require;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class ReturnMcpTools {

    private final ReturnRepository returnRepository;

    public ReturnMcpTools(ReturnRepository returnRepository) {
        this.returnRepository = returnRepository;
    }

    @McpTool(name = "createReturn",
            description = "Yangi qaytarish yaratadi (PENDING holatida). Buyurtma ko'rsatilsa, u DELIVERED bo'lishi " +
                    "va miqdor buyurtmadagidan oshmasligi kerak. Tasdiqlash uchun approveReturn ishlatiladi.")
    public ReturnDto createReturn(
            @McpToolParam(description = "Mijoz ID si") Long customerId,
            @McpToolParam(description = "Buyurtma ID si (ixtiyoriy)", required = false) Long orderId,
            @McpToolParam(description = "Mahsulot ID si") Long productId,
            @McpToolParam(description = "Qaytariladigan miqdor (0 dan katta)") Integer quantity,
            @McpToolParam(description = "Qaytariladigan summa (ixtiyoriy, bo'sh bo'lsa sotilgan narx x miqdor)", required = false) BigDecimal amount,
            @McpToolParam(description = "Sababi (ixtiyoriy)", required = false) String reason,
            @McpToolParam(description = "Agent ID si (ixtiyoriy, bo'sh bo'lsa buyurtma agenti)", required = false) Long agentId) {

        return ReturnDto.from(returnRepository.createReturn(
                customerId, orderId, productId, quantity, amount, reason, agentId));
    }

    @McpTool(name = "approveReturn",
            description = "Qaytarishni tasdiqlaydi: ombor qoldig'ini oshiradi, mijoz qarzi va agent KPI sini kamaytiradi")
    public ReturnDto approveReturn(
            @McpToolParam(description = "Qaytarish ID si") Long returnId) {
        return ReturnDto.from(returnRepository.approveReturn(returnId));
    }

    @McpTool(name = "rejectReturn",
            description = "Qaytarishni rad etadi")
    public ReturnDto rejectReturn(
            @McpToolParam(description = "Qaytarish ID si") Long returnId,
            @McpToolParam(description = "Rad etish sababi (ixtiyoriy)", required = false) String reason) {
        return ReturnDto.from(returnRepository.rejectReturn(returnId, reason));
    }

    @McpTool(name = "getReturn",
            description = "Qaytarishni ID bo'yicha topadi")
    public ReturnDto getReturn(
            @McpToolParam(description = "Qaytarish ID si") Long returnId) {
        return ReturnDto.from(Require.found(returnRepository.findById(returnId), "Qaytarish topilmadi: ID=" + returnId));
    }

    @McpTool(name = "listReturnsByCustomer",
            description = "Mijozning barcha qaytarishlarini ko'rsatadi")
    public List<ReturnDto> listReturnsByCustomer(
            @McpToolParam(description = "Mijoz ID si") Long customerId) {
        return returnRepository.findByCustomer(customerId).stream().map(ReturnDto::from).toList();
    }

    @McpTool(name = "listReturnsByStatus",
            description = "Holat bo'yicha qaytarishlarni ko'rsatadi (" + ReturnStatus.ALLOWED + ")")
    public List<ReturnDto> listReturnsByStatus(
            @McpToolParam(description = "Holat: " + ReturnStatus.ALLOWED) String status) {
        return returnRepository.findByStatus(ReturnStatus.parse(status)).stream().map(ReturnDto::from).toList();
    }

    @McpTool(name = "listPendingReturns",
            description = "Tasdiqlanmagan (PENDING) qaytarishlarni ko'rsatadi")
    public List<ReturnDto> listPendingReturns() {
        return returnRepository.findByStatus(ReturnStatus.PENDING).stream().map(ReturnDto::from).toList();
    }

    @McpTool(name = "listReturnsByAgent",
            description = "Agent bo'yicha qaytarishlarni ko'rsatadi")
    public List<ReturnDto> listReturnsByAgent(
            @McpToolParam(description = "Agent ID si") Long agentId) {
        return returnRepository.findByAgent(agentId).stream().map(ReturnDto::from).toList();
    }

    @McpTool(name = "listRecentReturns",
            description = "Eng so'nggi 100 ta qaytarishni ko'rsatadi")
    public List<ReturnDto> listRecentReturns() {
        return returnRepository.findAll().stream().map(ReturnDto::from).toList();
    }
}
