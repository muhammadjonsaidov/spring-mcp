package io.salesdoctor.spring_mcp.mcp;

import io.salesdoctor.spring_mcp.dto.DeletedDto;
import io.salesdoctor.spring_mcp.dto.KpiProgressDto;
import io.salesdoctor.spring_mcp.dto.KpiTargetDto;
import io.salesdoctor.spring_mcp.jooq.tables.records.KpiTargetsRecord;
import io.salesdoctor.spring_mcp.repository.AgentRepository;
import io.salesdoctor.spring_mcp.repository.KpiTargetRepository;
import io.salesdoctor.spring_mcp.support.AppTime;
import io.salesdoctor.spring_mcp.support.Require;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Component
public class KpiTargetMcpTools {

    private final KpiTargetRepository kpiTargetRepository;
    private final AgentRepository agentRepository;

    public KpiTargetMcpTools(KpiTargetRepository kpiTargetRepository, AgentRepository agentRepository) {
        this.kpiTargetRepository = kpiTargetRepository;
        this.agentRepository = agentRepository;
    }

    @McpTool(name = "createKpiTarget",
            description = "Agent uchun KPI maqsad yaratadi (oylik, haftalik va h.k.). " +
                    "Bir agentning davrlari bir-biri bilan kesishmasligi kerak.")
    public KpiTargetDto createKpiTarget(
            @McpToolParam(description = "Agent ID si") Long agentId,
            @McpToolParam(description = "Davr boshlanishi (YYYY-MM-DD)") String periodStart,
            @McpToolParam(description = "Davr tugashi (YYYY-MM-DD)") String periodEnd,
            @McpToolParam(description = "Maqsad summasi (so'mda)") BigDecimal targetAmount) {

        Require.that(targetAmount != null && targetAmount.signum() > 0, "Maqsad summasi 0 dan katta bo'lishi kerak.");

        LocalDate start = AppTime.parseDate(periodStart, null);
        LocalDate end = AppTime.parseDate(periodEnd, null);
        Require.that(start != null && end != null, "Davr boshlanishi va tugashi ko'rsatilishi kerak (YYYY-MM-DD).");
        Require.that(!end.isBefore(start), "Tugash sanasi boshlanish sanasidan oldin bo'lishi mumkin emas.");
        Require.found(agentRepository.findById(agentId), "Agent topilmadi: ID=" + agentId);

        KpiTargetsRecord overlapping = kpiTargetRepository.findOverlapping(agentId, start, end);
        if (overlapping != null) {
            throw new IllegalStateException(String.format(
                    "Bu davr agentning mavjud maqsadi bilan kesishadi (ID=%d, %s — %s). " +
                            "Yangilash uchun updateKpiTargetAmount ishlatilsin.",
                    overlapping.getId(), overlapping.getPeriodStart(), overlapping.getPeriodEnd()));
        }

        return KpiTargetDto.from(kpiTargetRepository.create(agentId, start, end, targetAmount));
    }

    @McpTool(name = "getKpiTarget",
            description = "KPI maqsadni ID bo'yicha topadi")
    public KpiTargetDto getKpiTarget(
            @McpToolParam(description = "KPI ID si") Long id) {
        return KpiTargetDto.from(Require.found(kpiTargetRepository.findById(id), "KPI topilmadi: ID=" + id));
    }

    @McpTool(name = "listKpiTargetsByAgent",
            description = "Agentning barcha KPI maqsadlarini ko'rsatadi")
    public List<KpiTargetDto> listKpiTargetsByAgent(
            @McpToolParam(description = "Agent ID si") Long agentId) {
        return kpiTargetRepository.findByAgent(agentId).stream().map(KpiTargetDto::from).toList();
    }

    @McpTool(name = "listAllKpiTargets",
            description = "Barcha KPI maqsadlarni ko'rsatadi")
    public List<KpiTargetDto> listAllKpiTargets() {
        return kpiTargetRepository.findAll().stream().map(KpiTargetDto::from).toList();
    }

    @McpTool(name = "getAgentKpiProgress",
            description = "Agentning berilgan sanadagi KPI bajarilish foizini ko'rsatadi")
    public KpiProgressDto getAgentKpiProgress(
            @McpToolParam(description = "Agent ID si") Long agentId,
            @McpToolParam(description = "Sana (YYYY-MM-DD), bo'sh bo'lsa bugun", required = false) String date) {

        LocalDate targetDate = AppTime.parseDate(date, AppTime.today());
        KpiTargetsRecord kpi = Require.found(kpiTargetRepository.findByAgentAndDate(agentId, targetDate),
                "Bu sanada agent uchun KPI maqsad topilmadi: agentId=" + agentId + ", sana=" + targetDate);

        BigDecimal target = kpi.getTargetAmount();
        BigDecimal achieved = kpi.getAchievedAmount() == null ? BigDecimal.ZERO : kpi.getAchievedAmount();
        BigDecimal progress = target.signum() == 0
                ? BigDecimal.ZERO
                : achieved.multiply(BigDecimal.valueOf(100)).divide(target, 2, RoundingMode.HALF_UP);

        return new KpiProgressDto(kpi.getId(), agentId, kpi.getPeriodStart(), kpi.getPeriodEnd(),
                target, achieved, progress);
    }

    @McpTool(name = "updateKpiAchieved",
            description = "KPI achieved_amount ni yangilaydi (qo'lda tuzatish uchun)")
    public KpiTargetDto updateKpiAchieved(
            @McpToolParam(description = "KPI ID si") Long kpiId,
            @McpToolParam(description = "Yangi achieved summasi (0 yoki katta)") BigDecimal achievedAmount) {

        Require.that(achievedAmount != null && achievedAmount.signum() >= 0,
                "Achieved summasi manfiy bo'lishi mumkin emas.");
        Require.that(kpiTargetRepository.setAchievedAmount(kpiId, achievedAmount) > 0, "KPI topilmadi: ID=" + kpiId);
        return getKpiTarget(kpiId);
    }

    @McpTool(name = "updateKpiTargetAmount",
            description = "KPI maqsad summasini yangilaydi")
    public KpiTargetDto updateKpiTargetAmount(
            @McpToolParam(description = "KPI ID si") Long kpiId,
            @McpToolParam(description = "Yangi maqsad summasi (0 dan katta)") BigDecimal newTarget) {

        Require.that(newTarget != null && newTarget.signum() > 0, "Maqsad summasi 0 dan katta bo'lishi kerak.");
        Require.that(kpiTargetRepository.updateTargetAmount(kpiId, newTarget) > 0, "KPI topilmadi: ID=" + kpiId);
        return getKpiTarget(kpiId);
    }

    @McpTool(name = "deleteKpiTarget",
            description = "KPI maqsadni o'chiradi")
    public DeletedDto deleteKpiTarget(
            @McpToolParam(description = "KPI ID si") Long kpiId) {
        Require.that(kpiTargetRepository.delete(kpiId) > 0, "KPI topilmadi: ID=" + kpiId);
        return new DeletedDto("kpiTarget", kpiId);
    }
}
