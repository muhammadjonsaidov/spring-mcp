# spring-mcp

An MCP (Model Context Protocol) server built with Spring Boot 4 / Spring AI 2, exposing
the **SalesDoctor** sales-data domain (customers, agents, products, stock, orders) as
callable tools for AI clients.

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
`order_items`, `payments` — with supporting indexes. `V2__seed_stock.sql` seeds 100
units of `MAIN` warehouse stock for every active product.

## MCP tools

Tools are declared with Spring AI's `@McpTool` / `@McpToolParam` annotations on
`@Component` classes in `io.salesdoctor.spring_mcp.mcp`. Descriptions are in Uzbek.

### Customers — `CustomerMcpTools`

`createCustomer`, `listCustomers`, `searchCustomers`, `getCustomer`, `updateDebt`,
`deleteCustomer`

### Orders — `OrderMcpTools`

`createOrder`, `getOrder`, `getOrderByNumber`, `listOrdersByCustomer`,
`listOrdersByAgent`, `listRecentOrders`, `updateOrderStatus`

### Products — `ProductMcpTools`

`createProduct`, `listProducts`, `searchProducts`, `getProductById`,
`getProductBySku`, `updateProductPrice`, `deleteProduct`

### Stock — `StockMcpTools`

`addStock`, `listWarehouseStock`, `listAgentStock`, `getStock`, `transferStockToAgent`

## Project layout

```
src/main/java/io/salesdoctor/spring_mcp/
├── SpringMcpApplication.java      # entry point
├── mcp/                           # @McpTool classes (customer, order, product, stock)
└── repository/                    # jOOQ-backed repositories
src/main/resources/
├── application.yaml
└── db/migration/                  # Flyway migrations (V1 schema, V2 seed)
```

## Notes

- jOOQ classes under `io.salesdoctor.spring_mcp.jooq` are generated at build time, not
  committed.
- `HELP.md`, `.idea/`, `target/` and the Maven wrapper jar are git-ignored.
