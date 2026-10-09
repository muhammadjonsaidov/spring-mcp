package io.salesdoctor.spring_mcp;

import io.salesdoctor.spring_mcp.domain.AgentRole;
import io.salesdoctor.spring_mcp.domain.OrderStatus;
import io.salesdoctor.spring_mcp.domain.PaymentMethod;
import io.salesdoctor.spring_mcp.domain.ReturnStatus;
import io.salesdoctor.spring_mcp.dto.*;
import io.salesdoctor.spring_mcp.error.ErrorCode;
import io.salesdoctor.spring_mcp.error.ToolException;
import io.salesdoctor.spring_mcp.mcp.*;
import io.salesdoctor.spring_mcp.support.AppTime;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.ThrowingConsumer;
import org.springframework.ai.util.JsonHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static io.salesdoctor.spring_mcp.jooq.Tables.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Seed ma'lumotlari (V3, V5, V10): agentlar 1..5, mahsulotlar 1..10 (har biri omborda 100 dona).
 * Mahsulot 1 = Coca-Cola, 12 000 so'm. Har bir test o'z tranzaksiyasida ishlaydi va oxirida qaytariladi.
 * Tool'lar xatoda istisno tashlaydi; Spring AI uni isError = true javobga aylantiradi.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class BusinessRulesIntegrationTest {

    private static final long ALI = 1L;
    private static final long COLA = 1L;
    private static final BigDecimal COLA_PRICE = new BigDecimal("12000.00");

    @Autowired DSLContext dsl;
    @Autowired OrderMcpTools orders;
    @Autowired StockMcpTools stock;
    @Autowired CustomerMcpTools customers;
    @Autowired PaymentMcpTools payments;
    @Autowired ReturnMcpTools returns;
    @Autowired KpiTargetMcpTools kpi;
    @Autowired ReportMcpTools reports;
    @Autowired AgentsMcpTools agents;
    @Autowired TerritoryMcpTools territories;

    private long customerId;

    @BeforeEach
    void setUp() {
        customerId = customers.createCustomer("Test do'kon", "Chilonzor", "+998900000000", 4L).id();
        StockTransferDto transfer = stock.transferStockToAgent(COLA, ALI, 50);
        assertThat(transfer.warehouseQuantity()).isEqualTo(50);
        assertThat(transfer.agentQuantity()).isEqualTo(50);
    }

    // --- Buyurtmalar ---

    @Test
    void negativeQuantityIsRejectedAndChangesNothing() {
        assertThatThrownBy(() -> orders.createOrder(customerId, ALI, "1:-50", null))
                .hasMessageContaining("0 dan katta");

        assertThat(agentQty(COLA, ALI)).isEqualTo(50);
        assertThat(debt(customerId)).isEqualByComparingTo("0");
    }

    @Test
    void duplicateLinesAreMergedAndValidatedTogether() {
        // 30 + 30 = 60 > 50: alohida tekshirilganda ikkalasi ham o'tib ketardi
        assertThatThrownBy(() -> orders.createOrder(customerId, ALI, "1:30,1:30", null))
                .hasMessageContaining("yetarli mahsulot yo'q");
        assertThat(agentQty(COLA, ALI)).isEqualTo(50);

        OrderDto order = orders.createOrder(customerId, ALI, "1:10,1:5", null);
        OrderDetailsDto details = orders.getOrder(order.id());

        assertThat(details.items()).singleElement()
                .satisfies(item -> assertThat(item.quantity()).isEqualTo(15));
        assertThat(order.status()).isEqualTo(OrderStatus.NEW);
        assertThat(order.totalAmount()).isEqualByComparingTo(COLA_PRICE.multiply(BigDecimal.valueOf(15)));
        assertThat(agentQty(COLA, ALI)).isEqualTo(35);
        assertThat(debt(customerId)).isEqualByComparingTo(order.totalAmount());
    }

    @Test
    void inactiveAgentCannotCreateOrders() {
        assertThat(agents.deactivateAgent(ALI).active()).isFalse();
        assertThatThrownBy(() -> orders.createOrder(customerId, ALI, "1:1", null))
                .hasMessageContaining("faol emas");
    }

    @Test
    void cancellingRestoresStockDebtAndKpi() {
        var monthStart = AppTime.today().withDayOfMonth(1);
        long kpiId = kpi.createKpiTarget(ALI, monthStart.toString(),
                monthStart.plusMonths(1).minusDays(1).toString(), new BigDecimal("1000000")).id();
        OrderDto order = orders.createOrder(customerId, ALI, "1:10", null);

        BigDecimal total = COLA_PRICE.multiply(BigDecimal.TEN);
        assertThat(agentQty(COLA, ALI)).isEqualTo(40);
        assertThat(debt(customerId)).isEqualByComparingTo(total);
        assertThat(kpi.getKpiTarget(kpiId).achieved()).isEqualByComparingTo(total);

        assertThat(orders.updateOrderStatus(order.id(), "cancelled").status()).isEqualTo(OrderStatus.CANCELLED);

        assertThat(agentQty(COLA, ALI)).isEqualTo(50);
        assertThat(debt(customerId)).isEqualByComparingTo("0");
        assertThat(kpi.getKpiTarget(kpiId).achieved()).isEqualByComparingTo("0");

        // Yakuniy holat: qayta bekor qilish yoki qaytarib ochish mumkin emas
        assertThatThrownBy(() -> orders.updateOrderStatus(order.id(), "CANCELLED")).hasMessageContaining("allaqachon");
        assertThatThrownBy(() -> orders.updateOrderStatus(order.id(), "NEW")).hasMessageContaining("o'zgartirib bo'lmaydi");
        assertThat(agentQty(COLA, ALI)).isEqualTo(50);
    }

    @Test
    void invalidStatusAndTransitionsAreRejected() {
        long orderId = orders.createOrder(customerId, ALI, "1:1", null).id();

        assertThatThrownBy(() -> orders.updateOrderStatus(orderId, "abc"))
                .hasMessageContaining("Noto'g'ri holat")
                .hasMessageContaining(OrderStatus.ALLOWED);
        assertThat(orders.updateOrderStatus(orderId, "DELIVERED").status()).isEqualTo(OrderStatus.DELIVERED);
        assertThatThrownBy(() -> orders.updateOrderStatus(orderId, "CANCELLED"))
                .satisfies(hasCode(ErrorCode.CONFLICT));
    }

    @Test
    void notFoundIsAnError() {
        assertThatThrownBy(() -> orders.getOrder(999_999L)).satisfies(hasCode(ErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> customers.getCustomer(999_999L)).hasMessageContaining("topilmadi");
        assertThat(orders.listOrdersByCustomer(999_999L)).isEmpty();
    }

    // --- Ombor ---

    @Test
    void addStockRejectsNonPositiveQuantityAndUnknownProduct() {
        assertThatThrownBy(() -> stock.addStock(COLA, -10)).hasMessageContaining("0 dan katta");
        assertThatThrownBy(() -> stock.addStock(COLA, 0)).hasMessageContaining("0 dan katta");
        assertThatThrownBy(() -> stock.addStock(9999L, 5)).hasMessageContaining("Mahsulot topilmadi");
        assertThat(warehouseQty(COLA)).isEqualTo(50);
    }

    @Test
    void addStockKeepsSingleWarehouseRow() {
        stock.addStock(COLA, 5);
        StockDto result = stock.addStock(COLA, 5);

        assertThat(result.quantity()).isEqualTo(60);
        assertThat(result.agentId()).isNull();
        assertThat(result.location()).isEqualTo("MAIN");
        assertThat(dsl.fetchCount(STOCK, STOCK.PRODUCT_ID.eq(COLA).and(STOCK.AGENT_ID.isNull()))).isEqualTo(1);
    }

    @Test
    void transferToInactiveAgentIsRejected() {
        agents.deactivateAgent(ALI);
        assertThatThrownBy(() -> stock.transferStockToAgent(COLA, ALI, 1)).hasMessageContaining("faol emas");
        assertThat(warehouseQty(COLA)).isEqualTo(50);
    }

    // --- To'lovlar ---

    @Test
    void paymentsAreValidatedAgainstDebtAndOrder() {
        long orderId = orders.createOrder(customerId, ALI, "1:5", null).id(); // 60 000
        long otherCustomer = customers.createCustomer("Boshqa", "-", "-", null).id();

        assertThatThrownBy(() -> payments.acceptPayment(customerId, orderId, new BigDecimal("70000"), null, ALI))
                .hasMessageContaining("qarzidan katta");
        assertThatThrownBy(() -> payments.acceptPayment(otherCustomer, orderId, new BigDecimal("1000"), null, ALI))
                .satisfies(hasCode(ErrorCode.INVALID_ARGUMENT));
        assertThatThrownBy(() -> payments.acceptPayment(customerId, orderId, BigDecimal.TEN, "bitcoin", ALI))
                .hasMessageContaining(PaymentMethod.ALLOWED);

        PaymentDto first = payments.acceptPayment(customerId, orderId, new BigDecimal("30000"), "cash", ALI);
        PaymentDto second = payments.acceptPayment(customerId, orderId, new BigDecimal("30000"), null, null);

        assertThat(first.method()).isEqualTo(PaymentMethod.CASH);
        assertThat(second.method()).isEqualTo(PaymentMethod.CASH);
        assertThat(second.agentId()).isNull();
        assertThat(debt(customerId)).isEqualByComparingTo("0");

        OrderPaymentStatusDto status = payments.orderPaymentStatus(orderId);
        assertThat(status.totalPaid()).isEqualByComparingTo("60000");
        assertThat(status.remaining()).isEqualByComparingTo("0");
        assertThat(payments.listPaymentsByOrder(orderId)).hasSize(2);
        assertThat(payments.dailyPaymentsTotal(null).totalPayments()).isEqualByComparingTo("60000");
    }

    // --- Qaytarishlar ---

    @Test
    void returnsAreValidatedAndApprovedOnlyOnce() {
        long orderId = orders.createOrder(customerId, ALI, "1:5", null).id(); // 60 000

        assertThatThrownBy(() -> returns.createReturn(customerId, orderId, COLA, 1, null, "singan", null))
                .hasMessageContaining("DELIVERED");

        orders.updateOrderStatus(orderId, "DELIVERED");

        assertThatThrownBy(() -> returns.createReturn(customerId, orderId, COLA, 6, null, null, null))
                .hasMessageContaining("Qaytariladigan miqdor ko'p");
        assertThatThrownBy(() -> returns.createReturn(customerId, orderId, COLA, 1, new BigDecimal("999999"), null, null))
                .hasMessageContaining("qiymatidan katta");
        assertThatThrownBy(() -> returns.createReturn(customerId, orderId, 2L, 1, null, null, null))
                .hasMessageContaining("buyurtmada yo'q");

        ReturnDto created = returns.createReturn(customerId, orderId, COLA, 2, null, "singan", null);
        assertThat(created.amount()).isEqualByComparingTo("24000");
        assertThat(created.status()).isEqualTo(ReturnStatus.PENDING);
        assertThat(created.agentId()).isEqualTo(ALI);

        int warehouseBefore = warehouseQty(COLA);
        ReturnDto approved = returns.approveReturn(created.id());
        assertThat(approved.status()).isEqualTo(ReturnStatus.APPROVED);
        assertThat(approved.resolvedAt()).isNotNull();

        assertThatThrownBy(() -> returns.approveReturn(created.id())).hasMessageContaining("Hozirgi holat: APPROVED");
        assertThatThrownBy(() -> returns.rejectReturn(created.id(), null)).satisfies(hasCode(ErrorCode.CONFLICT));

        assertThat(warehouseQty(COLA)).isEqualTo(warehouseBefore + 2);
        assertThat(debt(customerId)).isEqualByComparingTo("36000");

        // Qolgan 3 donadan ko'p qaytarib bo'lmaydi
        assertThatThrownBy(() -> returns.createReturn(customerId, orderId, COLA, 4, null, null, null))
                .hasMessageContaining("mumkin 3");
        assertThat(returns.listReturnsByStatus("approved")).extracting(ReturnDto::id).containsExactly(created.id());
    }

    // --- KPI ---

    @Test
    void overlappingKpiPeriodsAndBadInputAreRejected() {
        KpiTargetDto target = kpi.createKpiTarget(ALI, "2030-01-01", "2030-01-31", new BigDecimal("100"));
        assertThat(target.achieved()).isEqualByComparingTo("0");

        assertThatThrownBy(() -> kpi.createKpiTarget(ALI, "2030-01-15", "2030-02-15", new BigDecimal("100")))
                .hasMessageContaining("kesishadi");
        assertThatThrownBy(() -> kpi.createKpiTarget(ALI, "2030-03-01", "2030-03-31", BigDecimal.ZERO))
                .hasMessageContaining("0 dan katta");
        assertThatThrownBy(() -> kpi.createKpiTarget(ALI, "01-03-2030", "2030-03-31", BigDecimal.TEN))
                .hasMessageContaining("noto'g'ri formatda");
        assertThatThrownBy(() -> kpi.getAgentKpiProgress(ALI, "bad")).hasMessageContaining("noto'g'ri formatda");
    }

    // --- Agentlar va hududlar ---

    @Test
    void rolesAreParsedThroughEnum() {
        AgentDto created = agents.createAgent("Yangi Agent", "+998901234567", "new@salesdoctor.uz", null, null);
        assertThat(created.role()).isEqualTo(AgentRole.AGENT);

        assertThat(agents.updateAgentRole(created.id(), "supervisor").role()).isEqualTo(AgentRole.SUPERVISOR);
        assertThatThrownBy(() -> agents.updateAgentRole(created.id(), "boss"))
                .hasMessageContaining(AgentRole.ALLOWED);
    }

    @Test
    void territoryInUseCannotBeDeleted() {
        // FK xatosidan keyin Postgres test tranzaksiyasini to'xtatadi, shuning uchun bu oxirgi so'rov
        assertThatThrownBy(() -> territories.deleteTerritory(4L)).hasMessageContaining("o'chirib bo'lmadi");
    }

    // --- Hisobotlar ---

    @Test
    void salesByTerritoryRollsUpChildTerritories() {
        orders.createOrder(customerId, ALI, "1:5", null); // Chilonzor (4) -> Toshkent shahri (7) -> O'zbekiston (1)

        TerritorySalesReport report = reports.salesByTerritoryReport(null, null);

        assertThat(report.territories())
                .extracting(TerritorySalesRow::territoryId)
                .contains(4L, 7L, 1L)
                .doesNotContain(2L);
        assertThat(report.territories())
                .allSatisfy(row -> assertThat(row.totalAmount()).isEqualByComparingTo("60000"));
    }

    @Test
    void cancelledOrdersAreExcludedFromCustomerSummary() {
        long orderId = orders.createOrder(customerId, ALI, "1:5", null).id();
        orders.createOrder(customerId, ALI, "1:1", null);
        orders.updateOrderStatus(orderId, "CANCELLED");

        CustomerSummaryDto summary = reports.customerSummaryReport(customerId);
        assertThat(summary.orderCount()).isEqualTo(1);
        assertThat(summary.totalPurchases()).isEqualByComparingTo("12000");
    }

    @Test
    void overallStatsForEmptyPeriodReturnsZeros() {
        OverallStatsDto stats = reports.overallStatsReport("2030-01-01", "2030-01-31");

        assertThat(stats.totalOrders()).isZero();
        assertThat(stats.totalRevenue()).isEqualByComparingTo("0");
        assertThat(stats.avgOrderValue()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> reports.dailySalesReport("31-12-2030")).hasMessageContaining("noto'g'ri formatda");
    }

    // --- JSON (Spring AI tool natijasini shu helper bilan serialize qiladi) ---

    @Test
    void dtosSerializeToReadableJson() {
        OrderDto order = orders.createOrder(customerId, ALI, "1:1", "2030-05-01");

        String json = new JsonHelper().toJson(order);

        assertThat(json)
                .contains("\"status\":\"NEW\"")
                .contains("\"deliveryDate\":\"2030-05-01\"")
                .contains("\"totalAmount\":12000.00")
                .containsPattern("\"createdAt\":\"\\d{4}-\\d{2}-\\d{2}T");
    }

    // --- Yordamchilar ---

    static ThrowingConsumer<Throwable> hasCode(ErrorCode code) {
        return e -> assertThat(e).isInstanceOfSatisfying(ToolException.class,
                te -> assertThat(te.code()).isEqualTo(code));
    }

    private int agentQty(long productId, long agentId) {
        return dsl.select(STOCK.QUANTITY).from(STOCK)
                .where(STOCK.PRODUCT_ID.eq(productId)).and(STOCK.AGENT_ID.eq(agentId))
                .fetchOne(STOCK.QUANTITY);
    }

    private int warehouseQty(long productId) {
        return dsl.select(STOCK.QUANTITY).from(STOCK)
                .where(STOCK.PRODUCT_ID.eq(productId)).and(STOCK.AGENT_ID.isNull())
                .fetchOne(STOCK.QUANTITY);
    }

    private BigDecimal debt(long id) {
        return dsl.select(CUSTOMERS.DEBT_AMOUNT).from(CUSTOMERS)
                .where(CUSTOMERS.ID.eq(id))
                .fetchOne(CUSTOMERS.DEBT_AMOUNT);
    }
}
