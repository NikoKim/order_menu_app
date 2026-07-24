package com.order.menu.websocket;

import com.order.menu.common.transaction.AfterCommitExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class WsPushServiceTest {

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void publishesImmediatelyWithoutTransaction() {
        WsEventTransport transport = mock(WsEventTransport.class);
        WsPushService service = new WsPushService(transport, new WsInstanceIdentity("instance-a"),
                new AfterCommitExecutor());

        service.pushToUser(9L, "CHAT", "MESSAGE_CREATED", Map.of("messageId", 81L));

        var captor = org.mockito.ArgumentCaptor.forClass(WsEvent.class);
        verify(transport).publish(captor.capture());
        WsEvent event = captor.getValue();
        assertThat(event.targetType()).isEqualTo("USER");
        assertThat(event.targetId()).isEqualTo(9L);
        assertThat(event.payload().module()).isEqualTo("CHAT");
        assertThat(event.payload().action()).isEqualTo("MESSAGE_CREATED");
        assertThat(event.sourceInstanceId()).isEqualTo("instance-a");
    }

    @Test
    void defersPublishUntilTransactionCommit() {
        WsEventTransport transport = mock(WsEventTransport.class);
        WsPushService service = new WsPushService(transport, new WsInstanceIdentity("instance-a"),
                new AfterCommitExecutor());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        service.pushToShop(12L, "ORDER", "CREATED", Map.of("orderId", 31L));

        verify(transport, never()).publish(any());
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        verify(transport).publish(any(WsEvent.class));
    }

    @Test
    void alreadyDeferredCallPublishesOnceWithoutRegisteringAgain() {
        WsEventTransport transport = mock(WsEventTransport.class);
        AfterCommitExecutor executor = new AfterCommitExecutor();
        WsPushService service = new WsPushService(transport, new WsInstanceIdentity("instance-a"), executor);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        executor.execute(() -> service.pushToUser(9L, "CHAT", "UPDATED", Map.of()));
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();

        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(transport).publish(any(WsEvent.class));
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
    }
}
