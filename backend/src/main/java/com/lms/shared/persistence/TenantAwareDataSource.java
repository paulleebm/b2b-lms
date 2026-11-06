package com.lms.shared.persistence;

import com.lms.shared.TenantContext;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Binds the current tenant to every database connection, so Postgres row-level security applies
 * to <b>all</b> SQL — Hibernate, {@code JdbcClient}, anything that borrows a connection.
 *
 * <p>On checkout: {@code set_config('app.tenant_id', <tenant>, false)} (or {@code RESET} if there
 * is no tenant). On close (= return to the Hikari pool): {@code RESET app.tenant_id}.
 *
 * <p><b>Why the reset matters:</b> pooled connections are reused by the next request, which may
 * belong to another company. Without the reset, request B would inherit company A's tenant id.
 * The setting is session-scoped ({@code false}) because one checkout can span several
 * transactions; resetting on release keeps it from leaking.
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    public TenantAwareDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return bind(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return bind(super.getConnection(username, password));
    }

    private Connection bind(Connection connection) throws SQLException {
        try {
            UUID tenantId = TenantContext.tenantId().orElse(null);
            if (tenantId == null) {
                reset(connection);
            } else {
                try (PreparedStatement ps = connection.prepareStatement("select set_config('app.tenant_id', ?, false)")) {
                    ps.setString(1, tenantId.toString());
                    ps.execute();
                }
            }
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                new ResetOnClose(connection));
    }

    private static void reset(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("reset app.tenant_id");
        }
    }

    private static final class ResetOnClose implements InvocationHandler {

        private final Connection target;
        private boolean closed;

        private ResetOnClose(Connection target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "close" -> {
                    if (closed) {
                        return null;
                    }
                    closed = true;
                    try {
                        if (!target.isClosed()) {
                            reset(target);
                        }
                    } finally {
                        target.close();
                    }
                    return null;
                }
                case "isClosed" -> {
                    return closed || target.isClosed();
                }
                case "unwrap" -> {
                    Class<?> type = (Class<?>) args[0];
                    return type.isInstance(proxy) ? proxy : target.unwrap(type);
                }
                case "isWrapperFor" -> {
                    Class<?> type = (Class<?>) args[0];
                    return type.isInstance(proxy) || target.isWrapperFor(type);
                }
                case "equals" -> {
                    return proxy == args[0];
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                case "toString" -> {
                    return "TenantBound[" + target + "]";
                }
                default -> {
                    try {
                        return method.invoke(target, args);
                    } catch (InvocationTargetException e) {
                        throw e.getTargetException();
                    }
                }
            }
        }
    }
}
