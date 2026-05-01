package com.augment.cbsa.repository;

import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Supplier;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;

public final class CrdbRetry {

    private static final int MAX_ATTEMPTS = 5;
    private static final String SERIALIZATION_FAILURE_SQLSTATE = "40001";

    private CrdbRetry() {
    }

    public static <T> T run(DSLContext dsl, Supplier<T> work) {
        Objects.requireNonNull(dsl, "dsl must not be null");
        Objects.requireNonNull(work, "work must not be null");

        DataAccessException lastException = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return work.get();
            } catch (DataAccessException exception) {
                if (!isSerializationFailure(exception) || attempt == MAX_ATTEMPTS) {
                    throw exception;
                }
                lastException = exception;
            }
        }

        throw lastException;
    }

    private static boolean isSerializationFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && SERIALIZATION_FAILURE_SQLSTATE.equals(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
