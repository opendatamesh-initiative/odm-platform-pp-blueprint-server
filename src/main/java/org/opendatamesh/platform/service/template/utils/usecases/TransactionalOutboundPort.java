package org.opendatamesh.platform.service.template.utils.usecases;

import java.util.function.Function;

public interface TransactionalOutboundPort {

    void doInTransaction(Runnable runnable);

    <T, R> R doInTransactionWithResults(Function<T, R> function, T arg);
}
