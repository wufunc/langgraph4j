package org.bsc.langgraph4j.utils;

import java.util.function.Supplier;

@FunctionalInterface
public interface TrySupplier<R, Ex extends Throwable> extends Supplier<R> {
    org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TrySupplier.class);

    R tryGet() throws Ex;

    default R get() {
        try {
            return tryGet();
        } catch (Throwable ex) {
            log.error( ex.getMessage(), ex );
            throw new RuntimeException(ex);
        }
    }

    static <T,R,Ex extends Throwable> Supplier<R> Try( TrySupplier<R,Ex> supplier ) {
        return supplier;
    }
}
