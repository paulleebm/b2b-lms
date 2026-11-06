package com.lms.reporting.internal;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/** Caching on (Redis/Valkey in the app, an in-memory map in tests — see spring.cache.type). */
@Configuration(proxyBeanMethods = false)
@EnableCaching
class CacheConfig {
}
