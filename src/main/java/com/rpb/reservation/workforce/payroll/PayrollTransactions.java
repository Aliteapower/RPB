package com.rpb.reservation.workforce.payroll;

import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit boundaries keep arithmetic outside both JDBC transactions and advisory locks. */
final class PayrollTransactions {
    private final TransactionTemplate read;
    private final TransactionTemplate write;
    PayrollTransactions(PlatformTransactionManager manager) {
        read=new TransactionTemplate(manager);
        read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setReadOnly(true);
        write=new TransactionTemplate(manager);
        write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    <T>T read(Supplier<T> work){return read.execute(status->work.get());}
    <T>T write(Supplier<T> work){return write.execute(status->work.get());}
}
