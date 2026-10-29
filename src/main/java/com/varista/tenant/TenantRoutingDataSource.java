package com.varista.tenant;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import java.util.HashMap;
import java.util.Map;

public class TenantRoutingDataSource extends AbstractRoutingDataSource implements AutoCloseable {
    private final Map<Long, HikariDataSource> tenantDataSources;
    private final Long defaultTenantId;

    public TenantRoutingDataSource(Map<Long, HikariDataSource> tenantDataSources, Long defaultTenantId) {
        this.tenantDataSources = Map.copyOf(tenantDataSources);
        this.defaultTenantId = defaultTenantId;
        Map<Object, Object> targets = new HashMap<>();
        this.tenantDataSources.forEach(targets::put);
        setTargetDataSources(targets);
        if (this.tenantDataSources.containsKey(defaultTenantId)) {
            setDefaultTargetDataSource(this.tenantDataSources.get(defaultTenantId));
        }
        setLenientFallback(true);
        afterPropertiesSet();
    }

    @Override
    protected Object determineCurrentLookupKey() {
        Long tenantId = TenantContext.getTenantId();
        return tenantId != null ? tenantId : defaultTenantId;
    }

    public boolean containsTenant(Long tenantId) {
        return tenantDataSources.containsKey(tenantId);
    }

    @Override
    public void close() {
        tenantDataSources.values().forEach(HikariDataSource::close);
    }
}
