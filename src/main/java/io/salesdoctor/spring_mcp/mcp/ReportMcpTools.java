package io.salesdoctor.spring_mcp.mcp;

import io.salesdoctor.spring_mcp.repository.ReportRepository;
import org.jooq.Record;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ReportMcpTools {

    private final ReportRepository reportRepository;

    public ReportMcpTools(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    @McpTool(name = "dailySalesReport",
            description = "Kunlik savdo hisoboti: har bir agent bo'yicha buyurtmalar soni va summasi")
    public String dailySalesReport(
            @McpToolParam(description = "Sana (YYYY-MM-DD), bo'sh bo'lsa bugun") String date) {

        LocalDate targetDate = (date == null || date.isBlank())
                ? LocalDate.now()
                : LocalDate.parse(date);

        List<Record> rows = reportRepository.dailySales(targetDate);
        if (rows.isEmpty()) {
            return "Bu sanada savdo bo'lmagan: " + targetDate;
        }

        String body = rows.stream()
                .map(r -> String.format(
                        "{\"agentId\": %d, \"agentName\": \"%s\", " +
                                "\"orderCount\": %d, \"totalAmount\": %s}",
                        r.get("agent_id", Long.class),
                        esc(r.get("full_name", String.class)),
                        r.get("orderCount", Integer.class),
                        r.get("totalAmount", BigDecimal.class)
                ))
                .collect(Collectors.joining("\n"));

        return "Sana: " + targetDate + "\n" + body;
    }

    @McpTool(name = "topProductsReport",
            description = "Eng ko'p sotilgan mahsulotlar (default: oxirgi 7 kun, top 10)")
    public String topProductsReport(
            @McpToolParam(description = "Boshlanish sanasi (YYYY-MM-DD), bo'sh bo'lsa 7 kun oldin") String from,
            @McpToolParam(description = "Tugash sanasi (YYYY-MM-DD), bo'sh bo'lsa bugun") String to,
            @McpToolParam(description = "Nechta mahsulot (default 10)") Integer limit) {

        LocalDate fromDate = (from == null || from.isBlank())
                ? LocalDate.now().minusDays(7)
                : LocalDate.parse(from);
        LocalDate toDate = (to == null || to.isBlank())
                ? LocalDate.now()
                : LocalDate.parse(to);
        int topN = (limit == null || limit <= 0) ? 10 : limit;

        List<Record> rows = reportRepository.topProducts(fromDate, toDate, topN);
        if (rows.isEmpty()) {
            return "Bu davrda savdo bo'lmagan: " + fromDate + " — " + toDate;
        }

        String body = rows.stream()
                .map(r -> String.format(
                        "{\"productId\": %d, \"name\": \"%s\", " +
                                "\"totalQuantity\": %s, \"totalRevenue\": %s}",
                        r.get("id", Long.class),
                        esc(r.get("name", String.class)),
                        r.get("totalQuantity", BigDecimal.class),
                        r.get("totalRevenue", BigDecimal.class)
                ))
                .collect(Collectors.joining("\n"));

        return String.format("Davr: %s — %s (top %d)\n%s", fromDate, toDate, topN, body);
    }

    @McpTool(name = "agentKpiReport",
            description = "Agentlar bo'yicha savdo (KPI) hisoboti")
    public String agentKpiReport(
            @McpToolParam(description = "Boshlanish sanasi (YYYY-MM-DD), bo'sh bo'lsa shu oyning boshi") String from,
            @McpToolParam(description = "Tugash sanasi (YYYY-MM-DD), bo'sh bo'lsa bugun") String to) {

        LocalDate fromDate = (from == null || from.isBlank())
                ? LocalDate.now().withDayOfMonth(1)
                : LocalDate.parse(from);
        LocalDate toDate = (to == null || to.isBlank())
                ? LocalDate.now()
                : LocalDate.parse(to);

        List<Record> rows = reportRepository.agentSales(fromDate, toDate);
        if (rows.isEmpty()) {
            return "Bu davrda agentlar ma'lumotlari yo'q.";
        }

        String body = rows.stream()
                .map(r -> {
                    BigDecimal amount = r.get("totalAmount", BigDecimal.class);
                    return String.format(
                            "{\"agentId\": %d, \"fullName\": \"%s\", " +
                                    "\"orderCount\": %d, \"totalAmount\": %s}",
                            r.get("id", Long.class),
                            esc(r.get("full_name", String.class)),
                            r.get("orderCount", Integer.class),
                            amount == null ? "0" : amount
                    );
                })
                .collect(Collectors.joining("\n"));

        return String.format("KPI davri: %s — %s\n%s", fromDate, toDate, body);
    }

    @McpTool(name = "debtorsReport",
            description = "Qarzdor mijozlar ro'yxati (default: 0 dan katta qarz)")
    public String debtorsReport(
            @McpToolParam(description = "Minimal qarz miqdori (default 0)") BigDecimal minDebt) {

        BigDecimal threshold = (minDebt == null) ? BigDecimal.ZERO : minDebt;
        List<Record> rows = reportRepository.debtors(threshold);
        if (rows.isEmpty()) {
            return "Qarzdor mijozlar yo'q.";
        }

        String body = rows.stream()
                .map(r -> String.format(
                        "{\"customerId\": %d, \"name\": \"%s\", " +
                                "\"phone\": \"%s\", \"debt\": %s}",
                        r.get("id", Long.class),
                        esc(r.get("name", String.class)),
                        esc(r.get("phone", String.class)),
                        r.get("debt_amount", BigDecimal.class)
                ))
                .collect(Collectors.joining("\n"));

        return "Qarzdorlar:\n" + body;
    }

    @McpTool(name = "salesByTerritoryReport",
            description = "Hududlar bo'yicha savdo hisoboti")
    public String salesByTerritoryReport(
            @McpToolParam(description = "Boshlanish sanasi (YYYY-MM-DD), bo'sh bo'lsa shu oyning boshi") String from,
            @McpToolParam(description = "Tugash sanasi (YYYY-MM-DD), bo'sh bo'lsa bugun") String to) {

        LocalDate fromDate = (from == null || from.isBlank())
                ? LocalDate.now().withDayOfMonth(1)
                : LocalDate.parse(from);
        LocalDate toDate = (to == null || to.isBlank())
                ? LocalDate.now()
                : LocalDate.parse(to);

        List<Record> rows = reportRepository.salesByTerritory(fromDate, toDate);
        if (rows.isEmpty()) {
            return "Bu davrda hududlar bo'yicha ma'lumot yo'q.";
        }

        String body = rows.stream()
                .map(r -> String.format(
                        "{\"territoryId\": %d, \"territoryName\": \"%s\", " +
                                "\"orderCount\": %d, \"totalAmount\": %s}",
                        r.get("id", Long.class),
                        esc(r.get("name", String.class)),
                        r.get("orderCount", Integer.class),
                        r.get("totalAmount", BigDecimal.class)
                ))
                .collect(Collectors.joining("\n"));

        return String.format("Hududlar: %s — %s\n%s", fromDate, toDate, body);
    }

    @McpTool(name = "overallStatsReport",
            description = "Umumiy savdo statistikasi: buyurtmalar soni, umumiy summa, o'rtacha chek")
    public String overallStatsReport(
            @McpToolParam(description = "Boshlanish sanasi (YYYY-MM-DD), bo'sh bo'lsa shu oyning boshi") String from,
            @McpToolParam(description = "Tugash sanasi (YYYY-MM-DD), bo'sh bo'lsa bugun") String to) {

        LocalDate fromDate = (from == null || from.isBlank())
                ? LocalDate.now().withDayOfMonth(1)
                : LocalDate.parse(from);
        LocalDate toDate = (to == null || to.isBlank())
                ? LocalDate.now()
                : LocalDate.parse(to);

        Record r = reportRepository.overallStats(fromDate, toDate);
        if (r == null) return "Ma'lumot yo'q.";

        return String.format(
                "{\"from\": \"%s\", \"to\": \"%s\", " +
                        "\"totalOrders\": %d, \"totalRevenue\": %s, " +
                        "\"avgOrderValue\": %s, \"uniqueCustomers\": %d}",
                fromDate, toDate,
                r.get("totalOrders", Integer.class),
                r.get("totalRevenue", BigDecimal.class),
                r.get("avgOrderValue", BigDecimal.class),
                r.get("uniqueCustomers", Integer.class)
        );
    }

    @McpTool(name = "customerSummaryReport",
            description = "Bitta mijozning umumiy xulosasi: buyurtmalar soni, qarz, umumiy xaridlar")
    public String customerSummaryReport(
            @McpToolParam(description = "Mijoz ID si") Long customerId) {

        Record r = reportRepository.customerSummary(customerId);
        if (r == null) return "Mijoz topilmadi: ID=" + customerId;

        return String.format(
                "{\"customerId\": %d, \"name\": \"%s\", \"debt\": %s, " +
                        "\"orderCount\": %d, \"totalPurchases\": %s}",
                r.get("id", Long.class),
                esc(r.get("name", String.class)),
                r.get("debt_amount", BigDecimal.class),
                r.get("orderCount", Integer.class),
                r.get("totalPurchases", BigDecimal.class)
        );
    }

    private String esc(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}
