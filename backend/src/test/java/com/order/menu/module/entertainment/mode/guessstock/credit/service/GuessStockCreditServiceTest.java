package com.order.menu.module.entertainment.mode.guessstock.credit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.menu.module.entertainment.mode.guessstock.config.GuessStockConfig;
import com.order.menu.module.entertainment.mode.guessstock.credit.entity.CreditFacility;
import com.order.menu.module.entertainment.mode.guessstock.credit.mapper.CreditFacilityMapper;
import com.order.menu.module.entertainment.mode.guessstock.credit.mapper.CreditSnapshotMapper;
import com.order.menu.module.entertainment.mode.guessstock.credit.mapper.LoanContractMapper;
import com.order.menu.module.entertainment.mode.guessstock.credit.mapper.LoanLedgerMapper;
import com.order.menu.module.entertainment.mode.guessstock.engine.GuessStockRules;
import com.order.menu.module.entertainment.mode.guessstock.entity.GuessStockParticipant;
import com.order.menu.module.entertainment.mode.guessstock.entity.GuessStockPosition;
import com.order.menu.module.entertainment.mode.guessstock.mapper.GuessStockParticipantMapper;
import com.order.menu.module.entertainment.mode.guessstock.mapper.GuessStockPositionMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GuessStockCreditServiceTest {
    private CreditFacilityMapper facilityMapper;
    private GuessStockCreditService service;

    @BeforeEach
    void setUp() {
        facilityMapper = mock(CreditFacilityMapper.class);
        service = new GuessStockCreditService(facilityMapper, mock(LoanContractMapper.class),
                mock(LoanLedgerMapper.class), mock(CreditSnapshotMapper.class),
                mock(GuessStockParticipantMapper.class), mock(GuessStockPositionMapper.class), new ObjectMapper());
    }

    @Test
    void closingStagePreservesLeveragedTargetWithoutBorrowing() {
        when(facilityMapper.selectAllForUpdate(1L, 9L)).thenReturn(List.of());

        GuessStockCreditService.AiCreditCapacity capacity = service.aiCapacity(1L, 3, "CLOSING_TRADING",
                participant(), position(), bd("100"), config(), bd("230"));

        assertEquals("HIGH_RISK_BANK", capacity.productCode());
        assertEquals(0, bd("230").compareTo(capacity.executableExposure()));
        assertEquals(0, capacity.availableCredit().signum());
        assertTrue(capacity.reason().contains("保留目标敞口"));
    }

    @Test
    void activeFacilityDoesNotPreventRoutingToAnotherBank() {
        CreditFacility facility = new CreditFacility();
        facility.setProductCode("GROWTH_BANK");
        facility.setPrincipalOutstanding(bd("10000"));
        facility.setAccruedInterest(bd("0"));
        when(facilityMapper.selectAllForUpdate(1L, 9L)).thenReturn(List.of(facility));

        GuessStockCreditService.AiCreditCapacity capacity = service.aiCapacity(1L, 3, "CLOSING_TRADING",
                participant(), position(), bd("100"), config(), bd("230"));

        assertEquals("HIGH_RISK_BANK", capacity.productCode());
        assertEquals(0, bd("230").compareTo(capacity.executableExposure()));
    }

    private static GuessStockParticipant participant() {
        GuessStockParticipant participant = new GuessStockParticipant();
        participant.setId(9L);
        participant.setInitialCash(bd("100000"));
        participant.setCashBalance(bd("100000"));
        return participant;
    }

    private static GuessStockPosition position() {
        GuessStockPosition position = new GuessStockPosition();
        position.setQuantity(0L);
        return position;
    }

    private static GuessStockConfig config() {
        return new GuessStockConfig("QA", "测试", 10, 1, 5, bd("100000"), bd("100"), bd("3"),
                "MEDIUM", bd("1000"), bd("10"), 10_000L, "MANUAL", false, 2,
                false, 0, "NORMAL", List.of(), List.of(), List.of(), "V2", true, 1,
                GuessStockRules.defaultCreditProducts());
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
