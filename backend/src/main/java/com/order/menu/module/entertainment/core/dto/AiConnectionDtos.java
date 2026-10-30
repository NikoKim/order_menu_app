package com.order.menu.module.entertainment.core.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public final class AiConnectionDtos {
    private AiConnectionDtos() {}

    public record Request(@NotBlank @Pattern(regexp = "OWN|DEFAULT|HOSTED|ROOM") String source,
                          Long gameId, Long roomId, @Valid EntertainmentDtos.AiCredentialRequest aiCredential) {}

    public record Result(boolean connected, String message, String model, long latencyMs) {}
}
