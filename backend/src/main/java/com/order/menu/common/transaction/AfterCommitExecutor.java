package com.order.menu.common.transaction;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class AfterCommitExecutor {

    private static final ThreadLocal<Boolean> EXECUTING_AFTER_COMMIT =
            ThreadLocal.withInitial(() -> false);

    public void execute(Runnable action) {
        if (Boolean.TRUE.equals(EXECUTING_AFTER_COMMIT.get())
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        EXECUTING_AFTER_COMMIT.set(true);
                        try {
                            action.run();
                        } finally {
                            EXECUTING_AFTER_COMMIT.remove();
                        }
                    }
                }
        );
    }
}
