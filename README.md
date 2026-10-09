# spring-mcp

An MCP (Model Context Protocol) server built with Spring Boot 4 / Spring AI 2, exposing
the **SalesDoctor** sales-data domain (territories, agents, customers, orders, payments,
products, categories, stock, returns, KPI targets) as callable tools for AI clients.

The server speaks the **streamable HTTP** MCP protocol at `http://localhost:8888/mcp`,
requires a JWT on every MCP request, and backs its tools with PostgreSQL via jOOQ +
Flyway migrations.

## Tech stack

| Component | Version / notes |
| --- | --- |
| Java | 25 |
| Spring Boot | 4.1.1 (webmvc, actuator, flyway, jooq, security + OAuth2 resource server) |
| Spring AI | 2.0.1 (`spring-ai-starter-mcp-server-webmvc`) |
| Database | PostgreSQL 18 (`postgres:18.6-alpine`) |
| Persistence | jOOQ (code generated from the migrated schema) + Flyway migrations |
| Tests | JUnit 5, AssertJ, Testcontainers (PostgreSQL), Spring Security test |
| Build | Maven (`./mvnw`) with Flyway, jOOQ codegen and exec plugins |

## Getting started

### 1. Start PostgreSQL

```bash
docker compose up -d
```

This starts a `salesdoctor-postgres` container. Docker Compose reads `.env`
(`SALESDOCTOR_DB_NAME`, `SALESDOCTOR_DB_USER`, `SALESDOCTOR_DB_PASSWORD`,
`SALESDOCTOR_DB_PORT`) and falls back to `salesdoctor_db` / `salesdoctor` /
`salesdoctor_pass` / `5432`. The server builds its JDBC URL from the same variables.
Postgres applies the user and password only when the volume is first created; after
changing them, recreate it with `docker compose down -v`.

### 2. Build and run the server

```bash
./mvnw spring-boot:run
```

During `generate-sources`, `flyway-maven-plugin` applies the migrations and the jOOQ
codegen plugin then generates classes from the migrated schema (package
`io.salesdoctor.spring_mcp.jooq`, under `target/generated-sources/jooq`). PostgreSQL must
be reachable before the build runs. Without `SPRING_PROFILES_ACTIVE` the `dev` profile is
used.

| Endpoint | Auth | Purpose |
| --- | --- | --- |
| `/mcp` | JWT with scope `mcp` | MCP streamable HTTP endpoint |
| `/actuator/health` (`/liveness`, `/readiness`) | none | server and database health |
| `/actuator/info` | none | build version and time, Java runtime |

### 3. Local settings (`.env`)

```bash
cp .env.example .env
```

Fill in `SALESDOCTOR_JWT_SECRET` (`openssl rand -hex 32`). The server imports `.env` from
the working directory (`spring.config.import: optional:file:.env[.properties]`); real
environment variables take precedence over it. `.env` is git-ignored; `.env.example`
documents every variable. Without `.env` the `dev` profile falls back to a built-in
development key.

### 4. Create a token

Tokens are HS256 JWTs; the subject identifies the client in the logs. `TokenCli` takes the
key from `SALESDOCTOR_JWT_SECRET`, then from `.env`, and with `--dev` falls back to the
built-in development key:

```bash
./mvnw -q compile exec:java -Dexec.args="--subject claude-code --days 30"
```

Put the result into `SALESDOCTOR_MCP_TOKEN` in `.env`.

### 5. Connect an MCP client

`.mcp.json` reads the token from the `SALESDOCTOR_MCP_TOKEN` environment variable, so
the token never lands in git:

```json
{
  "mcpServers": {
    "salesdoctor-mcp": {
      "type": "http",
      "url": "http://localhost:8888/mcp",
      "headers": { "Authorization": "Bearer ${SALESDOCTOR_MCP_TOKEN}" }
    }
  }
}
```

Claude Code does not read `.env` itself, so export it before starting:

```bash
set -a; source .env; set +a
claude
```

Requests without a token, with an expired or foreign-signed token, or from another
issuer get `401`; a valid token without the `mcp` scope gets `403`.

## Configuration

`application.yaml` holds the shared settings; `application-dev.yaml` and
`application-prod.yaml` override them per environment. Values come from (highest first)
environment variables, `.env`, then the profile defaults.

| Setting | `dev` (default) | `prod` |
| --- | --- | --- |
| Database | `SALESDOCTOR_DB_HOST`/`_PORT`/`_NAME`/`_USER`/`_PASSWORD` (or `SALESDOCTOR_DB_URL`), local defaults | `SALESDOCTOR_DB_URL`, `SALESDOCTOR_DB_USER`, `SALESDOCTOR_DB_PASSWORD` (required) |
| JWT key | built-in development key | `SALESDOCTOR_JWT_SECRET` (required, ≥ 32 bytes; startup fails otherwise) |
| Logs | plain text | JSON (Elastic Common Schema) |
| `INTERNAL_ERROR` details | original message shown | generic message, details only in the log |
| Health details | always shown | only for authenticated requests |
| Docker Compose auto-start | on | off |

Other environment variables: `SERVER_PORT` (default `8888`), `SALESDOCTOR_JWT_ISSUER`
(default `salesdoctor-mcp`), `SALESDOCTOR_DB_POOL_SIZE` (prod, default `10`).

Run with the production profile:

```bash
SPRING_PROFILES_ACTIVE=prod SALESDOCTOR_DB_URL=... SALESDOCTOR_DB_USER=... \
SALESDOCTOR_DB_PASSWORD=... SALESDOCTOR_JWT_SECRET=... java -jar target/spring-mcp-0.0.1-SNAPSHOT.jar
```

## Errors

Every failing tool call returns `isError: true` with one JSON shape:

```json
{"error": {"code": "NOT_FOUND", "message": "Buyurtma topilmadi: ID=5", "tool": "getOrder", "traceId": "573090a1"}}
```

| Code | Meaning |
| --- | --- |
| `INVALID_ARGUMENT` | a parameter is missing or invalid (bad date, negative quantity, unknown status) |
| `NOT_FOUND` | the requested record does not exist |
| `CONFLICT` | a business rule or the current state forbids it (not enough stock, final order status, duplicate SKU) |
| `INTERNAL_ERROR` | unexpected server error; look up `traceId` in the server log |

Code throws `ToolException` (`error` package); `ToolCallInterceptor` wraps every tool
handler registered by Spring AI and converts failures into this format.

## Logging

Each tool call produces one log line on the `salesdoctor.mcp.calls` logger:

```
MCP tool getOrder caller=claude-code ERROR 4ms code=NOT_FOUND
```

With JSON logs (`prod`) the same entry carries separate fields: `tool`, `caller`
(JWT subject), `traceId` (also in the error response), `event=mcp.tool.call`,
`durationMs`, `outcome`, `errorCode`. Other log lines written during the call carry
`tool`, `caller` and `traceId` as well.

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
Spring AI serializes to JSON text; list tools return `[]` when nothing matches.
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
├── dto/                           # tool response records (entities, reports, results)
├── error/                         # ErrorCode, ToolException, ToolErrorDto
├── mcp/                           # @McpTool classes (territory, agent, customer, order,
│                                  #  payment, product, category, stock, report, return, kpi)
├── observability/                 # ToolCallInterceptor, caller context, MCP transport config
├── repository/                    # jOOQ-backed repositories
├── security/                      # SecurityConfig, JwtTokenService, TokenCli
└── support/                       # AppTime (Asia/Tashkent dates), Require (validation)
src/main/resources/
├── application.yaml               # shared settings
├── application-dev.yaml
├── application-prod.yaml
└── db/migration/                  # Flyway migrations (V1 schema, V2–V5 seed, V6–V9 schema, V10 seed)
```

## Tests

```bash
./mvnw test
```

Tests run against a throwaway `postgres:18.6-alpine` container via Testcontainers, so
Docker must be running; the development database is not touched.

| Test | Covers |
| --- | --- |
| `BusinessRulesIntegrationTest` | orders, stock, payments, returns, KPI, reports through the tool classes |
| `ToolCallInterceptorTest` | the registered tool handlers: error codes and format, call log |
| `SecurityIntegrationTest` | 401/403 cases, health endpoints, a full MCP session over HTTP with the caller in the log |
| `StructuredLoggingTest` | JSON (ECS) log fields of a tool call |
| `DomainEnumsTest` | enum transitions, parsing, description constants |

## Notes

- jOOQ classes under `io.salesdoctor.spring_mcp.jooq` are generated at build time, not
  committed. `forcedTypes` in `pom.xml` map `orders.status`, `returns.status`,
  `agents.role` and `payments.payment_method` to the enums in `domain` (stored as
  `name()`), so these fields are typed and status rules live in one place.
- The Maven build (Flyway + jOOQ codegen) does not read `.env`; it uses `pom.xml`
  properties `db.url`/`db.user`/`db.password` (local defaults). If `.env` points to
  another database, pass `-Ddb.url=...` as well.
- A freshly recreated database (`docker compose down -v && docker compose up -d`) builds
  without extra steps, because Flyway runs before jOOQ codegen. Override the build
  database with `-Ddb.url=... -Ddb.user=... -Ddb.password=...`.
- Tool beans must not be proxied (no `@Transactional`/AOP on `mcp` classes): Spring AI's
  scanner reads `@McpTool` from the bean's own class. Transactions live in repositories.
- `HELP.md`, `.idea/`, `target/` and the Maven wrapper jar are git-ignored.
