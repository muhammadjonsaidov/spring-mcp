package io.salesdoctor.spring_mcp.mcp;

import io.salesdoctor.spring_mcp.dto.AgentKpiReport;
import io.salesdoctor.spring_mcp.dto.AgentSalesRow;
import io.salesdoctor.spring_mcp.dto.CustomerSummaryDto;
import io.salesdoctor.spring_mcp.dto.DailySalesReport;
import io.salesdoctor.spring_mcp.dto.DebtorRow;
import io.salesdoctor.spring_mcp.dto.OverallStatsDto;
import io.salesdoctor.spring_mcp.dto.TerritorySalesReport;
import io.salesdoctor.spring_mcp.dto.TerritorySalesRow;
import io.salesdoctor.spring_mcp.dto.TopProductRow;
import io.salesdoctor.spring_mcp.dto.TopProductsReport;
import io.salesdoctor.spring_mcp.repository.ReportRepository;
import io.salesdoctor.spring_mcp.support.AppTime;
import io.salesdoctor.spring_mcp.support.Require;
import org.jooq.Record;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Component
public class ReportMcpTools {

    private static final int MAX_TOP_PRODUCTS = 100;

    private final ReportRepository reportRepository;

    public ReportMcpTools(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    @McpTool(name = "dailySalesReport",
            description = "Kunlik savdo hisoboti: har bir agent bo'yicha buyurtmalar soni va summasi")
    public DailySalesReport dailySalesReport(
            @McpToolParam(description = "Sana (YYYY-MM-DD), bo'sh bo'lsa bugun", required = false) String date) {

        LocalDate targetDate = AppTime.parseDate(date, AppTime.today());
        List<AgentSalesRow> rows = reportRepository.dailySales(targetDate).stream()
                .map(r -> new AgentSalesRow(
                        r.get("agent_id", Long.class),
                        r.get("full_name", String.class),
                        r.get("orderCount", Integer.class),
                        r.get("totalAmount", BigDecimal.class)))
                .toList();
        return new DailySalesReport(targetDate, rows);
    }

    @McpTool(name = "topProductsReport",
            description = "Eng ko'p sotilgan mahsulotlar (default: oxirgi 7 kun, top 10)")
    public TopProductsReport topProductsReport(
            @McpToolParam(description = "Boshlanish sanasi (YYYY-MM-DD), bo'sh bo'lsa 7 kun oldin", required = false) String from,
            @McpToolParam(description = "Tugash sanasi (YYYY-MM-DD), bo'sh bo'lsa bugun", required = false) String to,
            @McpToolParam(description = "Nechta mahsulot (default 10, maksimal 100)", required = false) Integer limit) {

        LocalDate fromDate = AppTime.parseDate(from, AppTime.today().minusDays(7));
        LocalDate toDate = AppTime.parseDate(to, AppTime.today());
        requireOrdered(fromDate, toDate);
        int topN = (limit == null || limit <= 0) ? 10 : Math.min(limit, MAX_TOP_PRODUCTS);

        List<TopProductRow> rows = reportRepository.topProducts(fromDate, toDate, topN).stream()
                .map(r -> new TopProductRow(
                        r.get("id", Long.class),
                        r.get("name", String.class),
                        r.get("totalQuantity", BigDecimal.class),
                        r.get("totalRevenue", BigDecimal.class)))
                .toList();
        return new TopProductsReport(fromDate, toDate, topN, rows);
    }

    @McpTool(name = "agentKpiReport",
            description = "Agentlar bo'yicha savdo (KPI) hisoboti")
    public AgentKpiReport agentKpiReport(
            @McpToolParam(description = "Boshlanish sanasi (YYYY-MM-DD), bo'sh bo'lsa shu oyning boshi", required = false) String from,
            @McpToolParam(description = "Tugash sanasi (YYYY-MM-DD), bo'sh bo'lsa bugun", required = false) String to) {

        LocalDate[] period = monthPeriod(from, to);
        List<AgentSalesRow> rows = reportRepository.agentSales(period[0], period[1]).stream()
                .map(r -> new AgentSalesRow(
                        r.get("id", Long.class),
                        r.get("full_name", String.class),
                        r.get("orderCount", Integer.class),
                        orZero(r.get("totalAmount", BigDecimal.class))))
                .toList();
        return new AgentKpiReport(period[0], period[1], rows);
    }

    @McpTool(name = "debtorsReport",
            description = "Qarzdor mijozlar ro'yxati (default: 0 dan katta qarz)")
    public List<DebtorRow> debtorsReport(
            @McpToolParam(description = "Minimal qarz miqdori (default 0)", required = false) BigDecimal minDebt) {

        BigDecimal threshold = (minDebt == null) ? BigDecimal.ZERO : minDebt;
        return reportRepository.debtors(threshold).stream()
                .map(r -> new DebtorRow(
                        r.get("id", Long.class),
                        r.get("name", String.class),
                        r.get("phone", String.class),
                        r.get("debt_amount", BigDecimal.class)))
                .toList();
    }

    @McpTool(name = "salesByTerritoryReport",
            description = "Hududlar bo'yicha savdo hisoboti. Har bir hudud summasi ichki hududlarini ham o'z ichiga oladi; " +
                    "hududi yo'q mijozlar 'Hududsiz' qatorida.")
    public TerritorySalesReport salesByTerritoryReport(
            @McpToolParam(description = "Boshlanish sanasi (YYYY-MM-DD), bo'sh bo'lsa shu oyning boshi", required = false) String from,
            @McpToolParam(description = "Tugash sanasi (YYYY-MM-DD), bo'sh bo'lsa bugun", required = false) String to) {

        LocalDate[] period = monthPeriod(from, to);
        List<TerritorySalesRow> rows = reportRepository.salesByTerritory(period[0], period[1]).stream()
                .map(r -> new TerritorySalesRow(
                        r.get("id", Long.class),
                        r.get("name", String.class),
                        r.get("parent_id", Long.class),
                        r.get("orderCount", Integer.class),
                        r.get("totalAmount", BigDecimal.class)))
                .toList();
        return new TerritorySalesReport(period[0], period[1], rows);
    }

    @McpTool(name = "overallStatsReport",
            description = "Umumiy savdo statistikasi: buyurtmalar soni, umumiy summa, o'rtacha chek")
    public OverallStatsDto overallStatsReport(
            @McpToolParam(description = "Boshlanish sanasi (YYYY-MM-DD), bo'sh bo'lsa shu oyning boshi", required = false) String from,
            @McpToolParam(description = "Tugash sanasi (YYYY-MM-DD), bo'sh bo'lsa bugun", required = false) String to) {

        LocalDate[] period = monthPeriod(from, to);
        // Agregat so'rov buyurtma bo'lmasa ham bitta qator qaytaradi (0 qiymatlar bilan)
        Record r = reportRepository.overallStats(period[0], period[1]);
        return new OverallStatsDto(period[0], period[1],
                r.get("totalOrders", Integer.class),
                r.get("totalRevenue", BigDecimal.class),
                r.get("avgOrderValue", BigDecimal.class),
                r.get("uniqueCustomers", Integer.class));
    }

    @McpTool(name = "customerSummaryReport",
            description = "Bitta mijozning umumiy xulosasi: buyurtmalar soni, qarz, umumiy xaridlar (bekor qilinganlarsiz)")
    public CustomerSummaryDto customerSummaryReport(
            @McpToolParam(description = "Mijoz ID si") Long customerId) {

        Record r = Require.found(reportRepository.customerSummary(customerId), "Mijoz topilmadi: ID=" + customerId);
        return new CustomerSummaryDto(
                r.get("id", Long.class),
                r.get("name", String.class),
                r.get("debt_amount", BigDecimal.class),
                r.get("orderCount", Integer.class),
                r.get("totalPurchases", BigDecimal.class));
    }

    /**
     * Bo'sh bo'lsa: shu oyning boshidan bugungacha.
     */
    private static LocalDate[] monthPeriod(String from, String to) {
        LocalDate today = AppTime.today();
        LocalDate fromDate = AppTime.parseDate(from, today.withDayOfMonth(1));
        LocalDate toDate = AppTime.parseDate(to, today);
        requireOrdered(fromDate, toDate);
        return new LocalDate[]{fromDate, toDate};
    }

    private static void requireOrdered(LocalDate from, LocalDate to) {
        Require.that(!to.isBefore(from), "Tugash sanasi boshlanish sanasidan oldin.");
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
