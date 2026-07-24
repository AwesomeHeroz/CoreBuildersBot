package com.corebuilders.bot.db;

import com.querydsl.sql.MySQLTemplates;
import com.querydsl.sql.SQLQueryFactory;
import com.querydsl.sql.SQLTemplates;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Small transaction/lifecycle wrapper around QueryDSL SQL.
 *
 * Service code receives a type-safe SQLQueryFactory and never handles SQL strings.
 * Nested transactions reuse the current connection; only the outer transaction commits.
 */
public final class QueryDslDatabase {
    private final DataSource dataSource;
    private final com.querydsl.sql.Configuration configuration;
    private final ThreadLocal<Connection> transactionConnection = new ThreadLocal<>();

    public QueryDslDatabase(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        SQLTemplates templates = MySQLTemplates.builder()
                .quote()
                .build();
        this.configuration = new com.querydsl.sql.Configuration(templates);
    }

    public <T> T query(Function<SQLQueryFactory, T> work) {
        Objects.requireNonNull(work, "work");
        Connection current = transactionConnection.get();
        if (current != null) {
            return work.apply(factory(current));
        }

        try (Connection connection = dataSource.getConnection()) {
            return work.apply(factory(connection));
        } catch (SQLException error) {
            throw new DatabaseException("Database operation failed: " + error.getMessage(), error.getSQLState(), error);
        }
    }

    public <T> T inTransaction(Supplier<T> work) {
        Objects.requireNonNull(work, "work");
        if (transactionConnection.get() != null) {
            return work.get();
        }

        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            transactionConnection.set(connection);
            try {
                T result = work.get();
                connection.commit();
                return result;
            } catch (Throwable error) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    error.addSuppressed(rollbackError);
                }
                throw propagate(error);
            } finally {
                transactionConnection.remove();
                try {
                    connection.setAutoCommit(originalAutoCommit);
                } catch (SQLException ignored) {
                    // Connection is about to be returned to Hikari, which resets state.
                }
            }
        } catch (SQLException error) {
            throw new DatabaseException("Could not start database transaction: " + error.getMessage(), error.getSQLState(), error);
        }
    }

    public void inTransaction(Runnable work) {
        inTransaction(() -> {
            work.run();
            return null;
        });
    }

    public boolean isDuplicateKey(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SQLException sql) {
                // MySQL ER_DUP_ENTRY. SQLState 23000 also covers other integrity violations.
                if (sql.getErrorCode() == 1062) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private SQLQueryFactory factory(Connection connection) {
        return new SQLQueryFactory(configuration, () -> connection);
    }

    private static RuntimeException propagate(Throwable error) {
        if (error instanceof RuntimeException runtime) return runtime;
        if (error instanceof Error fatal) throw fatal;
        return new RuntimeException(error);
    }
}
