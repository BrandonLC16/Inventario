package com.example.inventory;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class DatabaseTimeoutConfigurationIntegrationTest
        extends AbstractIntegrationTest {

    @Autowired
    DataSource dataSource;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void databaseAndTransactionWaitsAreBounded() throws SQLException {
        assertEquals("5s", jdbcTemplate.queryForObject(
                "SHOW lock_timeout", String.class));
        assertEquals("30s", jdbcTemplate.queryForObject(
                "SHOW statement_timeout", String.class));

        HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);
        assertEquals(5_000L, hikari.getConnectionTimeout());

        AbstractPlatformTransactionManager springTransactions = assertInstanceOf(
                AbstractPlatformTransactionManager.class, transactionManager);
        assertEquals(30, springTransactions.getDefaultTimeout());
    }
}
