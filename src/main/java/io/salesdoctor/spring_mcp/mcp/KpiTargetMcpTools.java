package io.salesdoctor.spring_mcp.mcp;

import io.salesdoctor.spring_mcp.jooq.tables.records.KpiTargetsRecord;
import io.salesdoctor.spring_mcp.repository.KpiTargetRepository;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class KpiTargetMcpTools {

    private final KpiTargetRepository kpiTargetRepository;

    public KpiTargetMcpTools(KpiTargetRepository kpiTargetRepository) {
        this.kpiTargetRepository = kpiTargetRepository;
    }

    @McpTool(name = "createKpiTarget",
            description = "Agent uchun KPI maqsad yaratadi (oylik, haftalik va h.k.)")
    public String createKpiTarget(
            @McpToolParam(description = "Agent ID si") Long agentId,
            @McpToolParam(description = "Davr boshlanishi (YYYY-MM-DD)") String periodStart,
            @McpToolParam(description = "Davr tugashi (YYYY-MM-DD)") String periodEnd,
            @McpToolParam(description = "Maqsad summasi (so'mda)") BigDecimal targetAmount) {

        try {
            LocalDate start = LocalDate.parse(periodStart);
            LocalDate end = LocalDate.parse(periodEnd);

            if (end.isBefore(start)) {
                return "Xatolik: tugash sanasi boshlanish sanasidan keyin bo'lishi kerak.";
            }

            KpiTargetsRecord existing = kpiTargetRepository.findByAgentAndPeriod(
                    agentId, start, end
            );
            if (existing != null) {
                return String.format(
                        "Xatolik: bu davr uchun maqsad allaqachon mavjud (ID=%d). " +
                                "Yangilash uchun updateKpiTargetAmount ishlatilsin.",
                        existing.getId()
                );
            }

            KpiTargetsRecord kpi = kpiTargetRepository.create(agentId, start, end, targetAmount);
            return format(kpi);
        } catch (Exception e) {
            return "Xatolik: " + e.getMessage();
        }
    }

    @McpTool(name = "getKpiTarget",
            description = "KPI maqsadni ID bo'yicha topadi")
    public String getKpiTarget(
            @McpToolParam(description = "KPI ID si") Long id) {
        KpiTargetsRecord kpi = kpiTargetRepository.findById(id);
        if (kpi == null) return "KPI topilmadi: ID=" + id;
        return format(kpi);
    }

    @McpTool(name = "listKpiTargetsByAgent",
            description = "Agentning barcha KPI maqsadlarini ko'rsatadi")
    public String listKpiTargetsByAgent(
            @McpToolParam(description = "Agent ID si") Long agentId) {
        List<KpiTargetsRecord> targets = kpiTargetRepository.findByAgent(agentId);
        if (targets.isEmpty()) return "Bu agentda KPI maqsadlar yo'q.";
        return targets.stream().map(this::format).collect(Collectors.joining("\n"));
    }

    @McpTool(name = "listAllKpiTargets",
            description = "Barcha KPI maqsadlarni ko'rsatadi")
    public String listAllKpiTargets() {
        List<KpiTargetsRecord> targets = kpiTargetRepository.findAll();
        if (targets.isEmpty()) return "KPI maqsadlar yo'q.";
        return targets.stream().map(this::format).collect(Collectors.joining("\n"));
    }

    @McpTool(name = "getAgentKpiProgress",
            description = "Agentning berilgan sanadagi KPI bajarilish foizini ko'rsatadi")
    public String getAgentKpiProgress(
            @McpToolParam(description = "Agent ID si") Long agentId,
            @McpToolParam(description = "Sana (YYYY-MM-DD), bo'sh bo'lsa bugun") String date) {

        LocalDate targetDate = (date == null || date.isBlank())
                ? LocalDate.now()
                : LocalDate.parse(date);

        KpiTargetsRecord kpi = kpiTargetRepository.findByAgentAndDate(agentId, targetDate);
        if (kpi == null) {
            return "Bu sanada agent uchun KPI maqsad topilmadi: agentId=" + agentId;
        }

        BigDecimal target = kpi.getTargetAmount();
        BigDecimal achieved = kpi.getAchievedAmount() == null
                ? BigDecimal.ZERO
                : kpi.getAchievedAmount();

        BigDecimal progress = target.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : achieved.multiply(BigDecimal.valueOf(100))
                .divide(target, 2, RoundingMode.HALF_UP);

        return String.format(
                "{\"agentId\": %d, \"periodStart\": \"%s\", \"periodEnd\": \"%s\", " +
                        "\"target\": %s, \"achieved\": %s, \"progressPercent\": %s}",
                agentId, kpi.getPeriodStart(), kpi.getPeriodEnd(),
                target, achieved, progress
        );
    }

    @McpTool(name = "updateKpiAchieved",
            description = "KPI achieved_amount ni yangilaydi (qo'lda tuzatish uchun)")
    public String updateKpiAchieved(
            @McpToolParam(description = "KPI ID si") Long kpiId,
            @McpToolParam(description = "Yangi achieved summasi") BigDecimal achievedAmount) {

        int updated = kpiTargetRepository.setAchievedAmount(kpiId, achievedAmount);
        return updated > 0
                ? "KPI achieved yangilandi: ID=" + kpiId + ", achieved=" + achievedAmount
                : "KPI topilmadi: ID=" + kpiId;
    }

    @McpTool(name = "updateKpiTargetAmount",
            description = "KPI maqsad summasini yangilaydi")
    public String updateKpiTargetAmount(
            @McpToolParam(description = "KPI ID si") Long kpiId,
            @McpToolParam(description = "Yangi maqsad summasi") BigDecimal newTarget) {

        int updated = kpiTargetRepository.updateTargetAmount(kpiId, newTarget);
        return updated > 0
                ? "KPI maqsad yangilandi: ID=" + kpiId + ", target=" + newTarget
                : "KPI topilmadi: ID=" + kpiId;
    }

    @McpTool(name = "deleteKpiTarget",
            description = "KPI maqsadni o'chiradi")
    public String deleteKpiTarget(
            @McpToolParam(description = "KPI ID si") Long kpiId) {
        int deleted = kpiTargetRepository.delete(kpiId);
        return deleted > 0 ? "KPI o'chirildi: ID=" + kpiId
                : "KPI topilmadi: ID=" + kpiId;
    }

    private String format(KpiTargetsRecord k) {
        return String.format(
                "{\"id\": %d, \"agentId\": %d, \"periodStart\": \"%s\", " +
                        "\"periodEnd\": \"%s\", \"target\": %s, \"achieved\": %s}",
                k.getId(),
                k.getAgentId(),
                k.getPeriodStart(),
                k.getPeriodEnd(),
                k.getTargetAmount(),
                k.getAchievedAmount() == null ? BigDecimal.ZERO : k.getAchievedAmount()
        );
    }
}
