# spring-mcp

An MCP (Model Context Protocol) server built with Spring Boot 4 / Spring AI 2, exposing
the **SalesDoctor** sales-data domain (territories, agents, customers, orders, payments,
products, categories, stock, returns, KPI targets) as callable tools for AI clients.

The server speaks the **streamable HTTP** MCP protocol at `http://localhost:8888/mcp`
and backs its tools with PostgreSQL via jOOQ + Flyway migrations.

## Tech stack

| Component | Version / notes |
| --- | --- |
| Java | 25 |
| Spring Boot | 4.1.1 (`spring-boot-starter-webmvc`, actuator, flyway, jooq) |
| Spring AI | 2.0.1 (`spring-ai-starter-mcp-server-webmvc`) |
| Database | PostgreSQL 18 (`postgres:18.6-alpine`) |
| Persistence | jOOQ (code generated from the live schema) + Flyway migrations |
| Build | Maven (`./mvnw`), Spring Boot Maven plugin, jOOQ codegen plugin |

## Getting started

### 1. Start PostgreSQL

```bash
docker compose up -d
```

This starts a `salesdoctor-postgres` container with:

- database `salesdoctor_db`
- user `salesdoctor` / password `salesdoctor_pass`
- port `5432`

Schema and seed data are applied automatically by Flyway on application startup
(`src/main/resources/db/migration`).

### 2. Generate jOOQ sources and run the server

```bash
./mvnw spring-boot:run
```

The jOOQ codegen plugin reads the **live** database at `localhost:5432/salesdoctor_db`
during the `generate-sources` phase and writes classes to
`target/generated-sources/jooq` under package `io.salesdoctor.spring_mcp.jooq`.
PostgreSQL must be reachable before the build runs.

Server endpoints:

| Endpoint | Purpose |
| --- | --- |
| `http://localhost:8888/mcp` | MCP streamable HTTP endpoint |
| `http://localhost:8888/actuator` | Spring Boot Actuator |

### 3. Connect an MCP client

`.mcp.json` is already wired up for local clients:

```json
{
  "mcpServers": {
    "salesdoctor-mcp": {
      "type": "http",
      "url": "http://localhost:8888/mcp"
    }
  }
}
```

## Configuration

Key settings live in `src/main/resources/application.yaml`:

- `spring.ai.mcp.server` — server name (`salesdoctor-mcp`), version, protocol
  (`streamable`), sync mode, enabled capabilities (tool/resource/prompt/completion),
  annotation scanning, 30s request timeout, `/mcp` endpoint with a 30s keep-alive.
- `spring.datasource` — PostgreSQL connection (`salesdoctor_db` @ `localhost:5432`).
- `spring.flyway` — migration location `classpath:db/migration`, `public` schema,
  baseline-on-migrate enabled.
- `server.port` — `8888`.

## Data model

Flyway `V1__init_schema.sql` creates the following tables:

`territories`, `agents`, `customers`, `categories`, `products`, `stock`, `orders`,
`order_items`, `payments` — with supporting indexes.

Later migrations extend the schema:

- `V6__alter_returns_table.sql` — creates `returns` (customer/order/product, quantity,
  amount, agent, `PENDING`/`APPROVED`/`REJECTED` status, `resolved_at`) with indexes on
  customer, product and status
- `V7__create_kpi_targets_table.sql` — creates `kpi_targets` (per-agent target vs.
  achieved amount for a period, unique per agent/period) with indexes
- `V8__add_reason_to_returns.sql` — adds a free-text `reason` column to `returns`

Seed data:

- `V2__seed_stock.sql` — 100 units of `MAIN` warehouse stock for every active product
- `V3__seed_territories_agents.sql` — 7 territories (hierarchical) and 5 agents
  covering the `AGENT`, `SUPERVISOR` and `EXPEDITOR` roles
- `V4__seed_agent_stock.sql` — initial van stock for agents 1–3
- `V5__seed_categories.sql` — a two-level product-category hierarchy
- `V6`–`V8` — `returns` and `kpi_targets` tables, `returns.reason`
- `V9__integrity_constraints.sql` — merges duplicate warehouse rows, adds a unique index
  on warehouse stock per product, `CHECK` constraints (non-negative stock/debt/price,
  positive quantities/amounts, valid KPI periods), `returns.status` default `PENDING`,
  date indexes on `orders` and `payments`
- `V10__seed_products.sql` — 10 categorised products with 100 units of `MAIN` stock each
  (existing SKUs are left untouched)

## MCP tools

Tools are declared with Spring AI's `@McpTool` / `@McpToolParam` annotations on
`@Component` classes in `io.salesdoctor.spring_mcp.mcp`. Descriptions are in Uzbek.
Tools return DTO records from `io.salesdoctor.spring_mcp.dto` (or lists of them), which
Spring AI serializes to JSON text; list tools return `[]` when nothing matches. Validation
and "not found" failures are thrown as exceptions, which Spring AI turns into an
`isError: true` result carrying the message.
85 tools are exposed in total. Parameters described as optional ("ixtiyoriy" /
"bo'sh bo'lsa ...") are declared with `required = false`.

### Territories — `TerritoryMcpTools`

`createTerritory`, `listTerritories`, `listRootTerritories`, `listChildTerritories`,
`getTerritory`, `renameTerritory`, `deleteTerritory`

### Agents — `AgentsMcpTools`

`createAgent`, `listAgents`, `listAgentsByTerritory`, `listAgentsByRole`, `getAgent`,
`getAgentByEmail`, `searchAgents`, `updateAgentTerritory`, `updateAgentRole`,
`deactivateAgent`

### Customers — `CustomerMcpTools`

`createCustomer`, `listCustomers`, `searchCustomers`, `getCustomer`, `updateDebt`,
`deleteCustomer`

### Orders — `OrderMcpTools`

`createOrder`, `getOrder`, `getOrderByNumber`, `listOrdersByCustomer`,
`listOrdersByAgent`, `listRecentOrders`, `updateOrderStatus`

`createOrder` rejects non-positive quantities, merges repeated product lines, and refuses
inactive customers, agents or products. It decreases the stock source (agent van, or the
warehouse when no agent is given), increases the customer's `debt_amount` by the order
total and adds that total to the agent's KPI target covering the order date.

`updateOrderStatus` allows `NEW → CONFIRMED | DELIVERED | CANCELLED` and
`CONFIRMED → DELIVERED | CANCELLED`; `DELIVERED` and `CANCELLED` are final. Cancelling
returns the items to their stock source and reverses the debt and KPI amounts.

### Payments — `PaymentMcpTools`

`acceptPayment`, `listPaymentsByCustomer`, `listPaymentsByOrder`, `listPaymentsByAgent`,
`listRecentPayments`, `dailyPaymentsTotal`, `orderPaymentStatus`

`acceptPayment` reduces the customer's debt. It rejects amounts above the current debt
(or above the order's unpaid remainder when `orderId` is given), orders of another
customer and cancelled orders. `orderPaymentStatus` returns the order total, the paid
amount and the remainder.

### Products — `ProductMcpTools`

`createProduct`, `listProducts`, `searchProducts`, `getProductById`,
`getProductBySku`, `updateProductPrice`, `updateProductCategory`, `deleteProduct`

### Categories — `CategoryMcpTools`

`createCategory`, `listCategories`, `listRootCategories`, `listChildCategories`,
`getCategory`, `searchCategories`, `renameCategory`, `deleteCategory`

`deleteCategory` refuses to delete a category that still has products.

### Stock — `StockMcpTools`

`addStock`, `getStock`, `listWarehouseStock`, `listAgentStock`, `getAgentStock`,
`transferStockToAgent`, `returnStockFromAgent`, `getAgentStockValue`

`transferStockToAgent` (warehouse → active agent's van) and `returnStockFromAgent`
(agent van → warehouse) run both steps in one transaction. All stock changes reject
non-positive quantities.

### Reports — `ReportMcpTools`

`dailySalesReport`, `topProductsReport`, `agentKpiReport`, `debtorsReport`,
`salesByTerritoryReport`, `overallStatsReport`, `customerSummaryReport`

Cancelled orders are excluded everywhere. `salesByTerritoryReport` rolls each territory
up with all of its descendants and lists customers without a territory as `Hududsiz`.
Dates are interpreted in `Asia/Tashkent` (see `AppTime` and the Hikari
`connection-init-sql`).

### Returns — `ReturnMcpTools`

`createReturn`, `approveReturn`, `rejectReturn`, `getReturn`, `listReturnsByCustomer`,
`listReturnsByStatus`, `listPendingReturns`, `listReturnsByAgent`, `listRecentReturns`

`createReturn` records a `PENDING` return. With an `orderId`, the order must belong to the
customer and be `DELIVERED`, the product must be on it, and the quantity may not exceed
what was ordered minus earlier pending/approved returns. The amount defaults to the sold
price × quantity and may not exceed it. `approveReturn` increases the warehouse stock and
reduces the customer's debt and the agent's KPI; `rejectReturn` records a reason. Only
`PENDING` returns can be approved or rejected, and each only once.

### KPI targets — `KpiTargetMcpTools`

`createKpiTarget`, `getKpiTarget`, `listKpiTargetsByAgent`, `listAllKpiTargets`,
`getAgentKpiProgress`, `updateKpiAchieved`, `updateKpiTargetAmount`, `deleteKpiTarget`

An agent's KPI periods may not overlap and targets must be positive. Orders add to the
target covering the order date; cancellations and approved returns subtract from it.
`getAgentKpiProgress` reports the achieved-vs-target percentage for a given date.

## Project layout

```
src/main/java/io/salesdoctor/spring_mcp/
├── SpringMcpApplication.java      # entry point
├── domain/                        # enums: OrderStatus (with transitions), ReturnStatus,
│                                  #  AgentRole, PaymentMethod, StockLocation
├── mcp/                           # @McpTool classes (territory, agent, customer, order,
│                                  #  payment, product, category, stock, report, return, kpi)
├── repository/                    # jOOQ-backed repositories
├── dto/                           # tool response records (entities, reports, results)
└── support/                       # AppTime (Asia/Tashkent dates), Require (validation)
src/main/resources/
├── application.yaml
└── db/migration/                  # Flyway migrations (V1 schema, V2–V5 seed, V6–V9 schema, V10 seed)
```

## Notes

- jOOQ classes under `io.salesdoctor.spring_mcp.jooq` are generated at build time, not
  committed. `forcedTypes` in `pom.xml` map `orders.status`, `returns.status`,
  `agents.role` and `payments.payment_method` to the enums in `domain` (stored as
  `name()`), so these fields are typed and status rules live in one place.
- `HELP.md`, `.idea/`, `target/` and the Maven wrapper jar are git-ignored.
- Tests (`./mvnw test`) run against a throwaway `postgres:18.6-alpine` container via
  Testcontainers, so Docker must be running; the development database is not touched.
- The build needs the local database from `docker-compose.yml` running: in
  `generate-sources`, `flyway-maven-plugin` first applies the migrations, then jOOQ
  generates code from the migrated schema. A freshly recreated database
  (`docker compose down -v && docker compose up -d`) therefore builds without extra steps.
  Override the build database with `-Ddb.url=... -Ddb.user=... -Ddb.password=...`.
