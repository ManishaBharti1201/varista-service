package com.varista.tenant;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(TenantDatabaseProperties.class)
public class TenantDatabaseConfiguration {

    @Bean(destroyMethod = "close")
    @Primary
    public TenantRoutingDataSource dataSource(TenantDatabaseProperties properties) {
        if (properties.getTenants() == null || properties.getTenants().isEmpty()) {
            throw new IllegalStateException("At least one varista.tenants database must be configured");
        }

        Map<Long, HikariDataSource> dataSources = new LinkedHashMap<>();
        properties.getTenants().forEach((tenantId, database) -> {
            if (tenantId == null || tenantId <= 0) {
                throw new IllegalStateException("Tenant database IDs must be positive numbers");
            }
            if (database == null
                    || database.getUrl() == null || database.getUrl().isBlank()
                    || database.getUsername() == null || database.getUsername().isBlank()) {
                throw new IllegalStateException("A valid database URL and username are required for tenant " + tenantId);
            }

            String url = database.getUrl();
            if (!(url.startsWith("jdbc:mysql://") || url.startsWith("jdbc:h2:"))) {
                throw new IllegalStateException("Only MySQL and H2 JDBC URLs are supported for tenant " + tenantId);
            }

            HikariDataSource tenantDataSource = new HikariDataSource();
            tenantDataSource.setJdbcUrl(url);
            tenantDataSource.setUsername(database.getUsername());
            if (database.getPassword() != null) {
                tenantDataSource.setPassword(database.getPassword());
            }
            tenantDataSource.setDriverClassName(url.startsWith("jdbc:mysql://")
                    ? "com.mysql.cj.jdbc.Driver"
                    : "org.h2.Driver");
            tenantDataSource.setPoolName("tenant-" + tenantId);
            tenantDataSource.setMaximumPoolSize(5);
            tenantDataSource.setMinimumIdle(0);
            try (var connection = tenantDataSource.getConnection()) {
                if (!connection.isValid(2)) {
                    throw new SQLException("Connection validation returned false");
                }
            } catch (SQLException exception) {
                tenantDataSource.close();
                dataSources.values().forEach(HikariDataSource::close);
                throw new IllegalStateException("Could not connect to the database for tenant " + tenantId, exception);
            }
            dataSources.put(tenantId, tenantDataSource);
        });

        Long defaultTenantId = dataSources.keySet().iterator().next();
        return new TenantRoutingDataSource(dataSources, defaultTenantId);
    }
}
