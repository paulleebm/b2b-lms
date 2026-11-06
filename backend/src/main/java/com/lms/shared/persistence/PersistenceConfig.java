package com.lms.shared.persistence;

import javax.sql.DataSource;
import org.hibernate.cfg.MultiTenancySettings;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class PersistenceConfig {

    /** Registers the tenant resolver that drives Hibernate's {@code @TenantId} filtering. */
    @Bean
    HibernatePropertiesCustomizer tenantResolverCustomizer() {
        TenantIdentifierResolver resolver = new TenantIdentifierResolver();
        return properties -> properties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
    }

    /**
     * Wraps the application DataSource (Hikari pool, lms_app user) so every checkout is bound to
     * the current tenant for RLS. Flyway uses its own owner connection and is not wrapped.
     */
    @Bean
    static BeanPostProcessor tenantAwareDataSourcePostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource dataSource
                        && "dataSource".equals(beanName)
                        && !(bean instanceof TenantAwareDataSource)) {
                    return new TenantAwareDataSource(dataSource);
                }
                return bean;
            }
        };
    }
}
