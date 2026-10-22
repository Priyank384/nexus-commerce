package com.example.nexusCommerce.configs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The namespace is the only thing keeping the MySQL demo catalogue and the Postgres apparel
 * catalogue from serving each other's cached rows out of one Redis, so the parsing has to hold
 * for both dialects' URL shapes.
 */
class CacheConfigNamespaceTest {

    @Test
    void usesTheDatabaseNameFromAMysqlUrl() {
        assertEquals("nexuscommerce",
                CacheConfig.datasourceNamespace("jdbc:mysql://localhost:3306/nexuscommerce"));
    }

    @Test
    void ignoresQueryParametersFromAPostgresUrl() {
        assertEquals("defaultdb", CacheConfig.datasourceNamespace(
                "jdbc:postgresql://host.aivencloud.com:23076/defaultdb?sslmode=require"));
    }

    @Test
    void keepsTheDatabaseNameAndDropsTheConnectionStringNoise() {
        assertEquals("my-db", CacheConfig.datasourceNamespace("jdbc:mysql://localhost:3306/my-db?useSSL=false"));
    }

    @Test
    void fallsBackWhenNoUrlIsConfigured() {
        assertEquals("default", CacheConfig.datasourceNamespace(""));
        assertEquals("default", CacheConfig.datasourceNamespace(null));
        assertEquals("default", CacheConfig.datasourceNamespace("jdbc:h2:mem:testdb"));
    }
}
