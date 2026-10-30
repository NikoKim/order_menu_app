package com.order.menu.module.entertainment.mode.guessstock.service;

import com.order.menu.module.entertainment.core.service.GameAiCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NonTransactionalAiModelGateway {
    private final ModelAiDecisionService modelAiDecisionService;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ModelAiDecisionService.Decision decide(GameAiCredentialService.DecryptedCredential credential,
                                                   ModelAiDecisionService.Context context,
                                                   String modelRoute) {
        return modelAiDecisionService.decide(credential, context, modelRoute);
    }

    public ModelAiDecisionService.InvocationTelemetry consumeLastTelemetry() {
        return modelAiDecisionService.consumeLastTelemetry();
    }

    public int inputTokenUpperBound(GameAiCredentialService.DecryptedCredential credential,
                                    ModelAiDecisionService.Context context, String route) {
        return modelAiDecisionService.inputTokenUpperBound(credential, context, route);
    }
}
