package io.salesdoctor.spring_mcp.repository;

import io.salesdoctor.spring_mcp.jooq.tables.records.CustomersRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

import static io.salesdoctor.spring_mcp.jooq.Tables.CUSTOMERS;

@Repository
public class CustomerRepository {

    private final DSLContext dsl;

    public CustomerRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public CustomersRecord create(String name, String address, String phone, Long territoryId) {
        return dsl.insertInto(CUSTOMERS)
                .set(CUSTOMERS.NAME, name)
                .set(CUSTOMERS.ADDRESS, address)
                .set(CUSTOMERS.PHONE, phone)
                .set(CUSTOMERS.TERRITORY_ID, territoryId)
                .set(CUSTOMERS.DEBT_AMOUNT, BigDecimal.ZERO)
                .set(CUSTOMERS.IS_ACTIVE, true)
                .returning()
                .fetchOne();
    }

    public List<CustomersRecord> findAll() {
        return dsl.selectFrom(CUSTOMERS)
                .where(CUSTOMERS.IS_ACTIVE.eq(true))
                .orderBy(CUSTOMERS.CREATED_AT.desc())
                .fetch();
    }

    public CustomersRecord findById(Long id) {
        return dsl.selectFrom(CUSTOMERS)
                .where(CUSTOMERS.ID.eq(id))
                .fetchOne();
    }

    public List<CustomersRecord> searchByName(String query) {
        return dsl.selectFrom(CUSTOMERS)
                .where(CUSTOMERS.NAME.containsIgnoreCase(query))
                .and(CUSTOMERS.IS_ACTIVE.eq(true))
                .orderBy(CUSTOMERS.NAME)
                .fetch();
    }

    public int updateDebt(Long customerId, BigDecimal newDebt) {
        return dsl.update(CUSTOMERS)
                .set(CUSTOMERS.DEBT_AMOUNT, newDebt)
                .where(CUSTOMERS.ID.eq(customerId))
                .execute();
    }

    @Deprecated(forRemoval = true)
    public int delete(Long id) {
        return dsl.deleteFrom(CUSTOMERS)
                .where(CUSTOMERS.ID.eq(id))
                .execute();
    }

    public int softDelete(Long id) {
        return dsl.update(CUSTOMERS)
                .set(CUSTOMERS.IS_ACTIVE, false)
                .where(CUSTOMERS.ID.eq(id))
                .execute();
    }
}
