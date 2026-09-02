package com.order.menu.module.entertainment.mode.guessstock.credit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.menu.common.exception.BusinessException;
import com.order.menu.common.exception.ErrorCode;
import com.order.menu.module.entertainment.mode.guessstock.config.GuessStockConfig;
import com.order.menu.module.entertainment.mode.guessstock.credit.engine.CreditLimitCalculator;
import com.order.menu.module.entertainment.mode.guessstock.credit.engine.CreditRiskCalculator;
import com.order.menu.module.entertainment.mode.guessstock.credit.engine.ForcedLiquidationPlanner;
import com.order.menu.module.entertainment.mode.guessstock.credit.engine.LoanInterestCalculator;
import com.order.menu.module.entertainment.mode.guessstock.credit.entity.CreditFacility;
import com.order.menu.module.entertainment.mode.guessstock.credit.entity.CreditSnapshot;
import com.order.menu.module.entertainment.mode.guessstock.credit.entity.LoanContract;
import com.order.menu.module.entertainment.mode.guessstock.credit.entity.LoanLedger;
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
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GuessStockCreditService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4);
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private final CreditFacilityMapper facilityMapper;
    private final LoanContractMapper contractMapper;
    private final LoanLedgerMapper ledgerMapper;
    private final CreditSnapshotMapper snapshotMapper;
    private final GuessStockParticipantMapper participantMapper;
    private final GuessStockPositionMapper positionMapper;
    private final ObjectMapper objectMapper;

    public CreditFacility find(Long sessionId, Long participantId) {
        return facilityMapper.selectOne(new LambdaQueryWrapper<CreditFacility>()
                .eq(CreditFacility::getSessionId, sessionId).eq(CreditFacility::getParticipantId, participantId)
                .last("LIMIT 1"));
    }

    public List<CreditFacility> findAll(Long sessionId, Long participantId) {
        return facilityMapper.selectList(new LambdaQueryWrapper<CreditFacility>()
                .eq(CreditFacility::getSessionId, sessionId).eq(CreditFacility::getParticipantId, participantId)
                .orderByAsc(CreditFacility::getId));
    }

    public CreditStatus status(Long sessionId, GuessStockParticipant participant, GuessStockPosition position,
                               BigDecimal price, GuessStockConfig config) {
        if (!Boolean.TRUE.equals(config.loanEnabled())) {
            CreditRiskCalculator.Snapshot risk = CreditRiskCalculator.calculate(participant.getCashBalance(),
                    position.getQuantity(), price, ZERO, ZERO, null, false);
            return new CreditStatus(false, null, null, ZERO, ZERO, ZERO, Map.of(), 0, risk,
                    ZERO, 0, List.of(), List.of(), List.of());
        }
        List<CreditFacility> facilities = findAll(sessionId, participant.getId());
        BigDecimal principal = totalPrincipal(facilities);
        BigDecimal interest = totalInterest(facilities);
        CreditFacility facility = facilities.stream().filter(item -> debt(item).signum() > 0)
                .findFirst().orElse(facilities.stream().findFirst().orElse(null));
        GuessStockConfig.CreditProductConfig product = riskProduct(config, facilities, participant, position, price,
                principal, interest);
        boolean defaulted = facilities.stream().anyMatch(item -> "DEFAULTED".equals(item.getStatus()));
        CreditRiskCalculator.Snapshot risk = CreditRiskCalculator.calculate(participant.getCashBalance(), position.getQuantity(),
                price, principal, interest, product, defaulted);
        BigDecimal available = ZERO;
        Map<String, BigDecimal> productAvailableCredit = new LinkedHashMap<>();
        List<BankStatus> banks = new ArrayList<>();
        for (GuessStockConfig.CreditProductConfig item : config.creditProducts()) {
            if (!Boolean.TRUE.equals(item.enabled())) continue;
            CreditFacility bankFacility = facilities.stream().filter(row -> item.code().equalsIgnoreCase(row.getProductCode()))
                    .findFirst().orElse(null);
            boolean bankDefaulted = bankFacility != null && "DEFAULTED".equals(bankFacility.getStatus());
            CreditRiskCalculator.Snapshot bankRisk = CreditRiskCalculator.calculate(participant.getCashBalance(), position.getQuantity(),
                    price, principal, interest, item, defaulted);
            BigDecimal productAvailable = bankDefaulted || List.of("BLOCKED", "LIQUIDATION", "DEFAULTED").contains(bankRisk.riskState())
                    ? ZERO : CreditLimitCalculator.calculate(participant.getInitialCash(), participant.getCashBalance(),
                    position.getQuantity(), price, principal, interest,
                    bankFacility == null ? ZERO : bankFacility.getPrincipalOutstanding(),
                    bankFacility == null ? ZERO : bankFacility.getAccruedInterest(), item).availableCredit();
            productAvailableCredit.put(item.code(), productAvailable);
            if (productAvailable.compareTo(available) > 0) available = productAvailable;
            List<LoanContract> bankContracts = contracts(bankFacility).stream()
                    .sorted(Comparator.comparing(LoanContract::getId).reversed()).toList();
            int bankMaturity = bankContracts.stream().filter(row -> active(row.getStatus()))
                    .map(LoanContract::getMaturityRound).min(Integer::compareTo).orElse(0);
            banks.add(new BankStatus(item, bankFacility,
                    bankFacility == null ? ZERO : bankFacility.getPrincipalOutstanding(),
                    bankFacility == null ? ZERO : bankFacility.getAccruedInterest(), productAvailable, bankMaturity,
                    ledgers(bankFacility), bankContracts));
        }
        int maturity = facilities.stream().flatMap(item -> contracts(item).stream()).filter(item -> active(item.getStatus()))
                .map(LoanContract::getMaturityRound).min(Integer::compareTo).orElse(0);
        List<LoanLedger> recent = ledgers(sessionId, participant.getId());
        BigDecimal totalInterest = ledgerMapper.selectList(new LambdaQueryWrapper<LoanLedger>()
                        .eq(LoanLedger::getSessionId, sessionId).eq(LoanLedger::getParticipantId, participant.getId()))
                .stream().map(LoanLedger::getInterestDelta).filter(value -> value != null && value.signum() > 0)
                .reduce(ZERO, BigDecimal::add);
        long forcedShares = snapshotMapper.selectList(new LambdaQueryWrapper<CreditSnapshot>()
                        .eq(CreditSnapshot::getSessionId, sessionId).eq(CreditSnapshot::getParticipantId, participant.getId()))
                .stream().mapToLong(row -> row.getForcedLiquidationShares() == null ? 0 : row.getForcedLiquidationShares()).sum();
        List<LoanContract> displayContracts = facilities.stream().flatMap(item -> contracts(item).stream())
                .sorted(Comparator.comparing(LoanContract::getId).reversed()).toList();
        return new CreditStatus(Boolean.TRUE.equals(config.loanEnabled()), facility, product, principal, interest,
                available, Map.copyOf(productAvailableCredit), maturity, risk,
                GuessStockRules.money(totalInterest), forcedShares, recent, displayContracts, List.copyOf(banks));
    }

    public CreditStatus borrow(Long sessionId, int roundNo, String phase, GuessStockParticipant participant,
                               GuessStockPosition position, BigDecimal price, GuessStockConfig config,
                               String productCode, BigDecimal requested, Integer requestedMaturityRounds,
                               String requestId) {
        requireLoanEnabled(config);
        if (!List.of("CREDIT_TRADING", "OPENING_TRADING").contains(phase)) {
            bad("只允许在银行融资或开盘交易阶段借款");
        }
        if (requested == null || requested.signum() <= 0) bad("借款金额必须大于0");
        LoanLedger duplicate = requestLedger(sessionId, participant.getId(), requestId);
        if (duplicate != null) return status(sessionId, participant, position, price, config);
        List<CreditFacility> facilities = facilityMapper.selectAllForUpdate(sessionId, participant.getId());
        CreditFacility facility = facilities.stream().filter(item -> productCode != null
                && productCode.equalsIgnoreCase(item.getProductCode())).findFirst().orElse(null);
        GuessStockConfig.CreditProductConfig product = product(config, productCode);
        if (product == null || !Boolean.TRUE.equals(product.enabled())) bad("所选银行当前不可用");
        if (facility != null && "DEFAULTED".equals(facility.getStatus())) bad("授信账户已经违约，不能继续借款");
        if (facility != null && facility.getLastBorrowRound() != null
                && roundNo - facility.getLastBorrowRound() < product.borrowCooldownRounds()) bad("当前仍在借款冷却期");
        BigDecimal minBorrow = participant.getInitialCash().multiply(product.minBorrowRatio())
                .divide(HUNDRED, 10, RoundingMode.HALF_UP);
        if (requested.compareTo(minBorrow) < 0) bad("借款金额低于该银行最低放款金额");
        BigDecimal principal = totalPrincipal(facilities);
        BigDecimal interest = totalInterest(facilities);
        CreditLimitCalculator.Limit limit = CreditLimitCalculator.calculate(participant.getInitialCash(),
                participant.getCashBalance(), position.getQuantity(), price, principal, interest,
                facility == null ? ZERO : facility.getPrincipalOutstanding(),
                facility == null ? ZERO : facility.getAccruedInterest(), product);
        BigDecimal amount = GuessStockRules.money(requested);
        if (amount.compareTo(limit.availableCredit()) > 0) bad("借款金额超过当前可用额度");
        if (facility == null) facility = createFacility(sessionId, participant.getId(), roundNo, product);
        BigDecimal beforePrincipal = facility.getPrincipalOutstanding();
        BigDecimal beforeInterest = facility.getAccruedInterest();
        facility.setPrincipalOutstanding(GuessStockRules.money(beforePrincipal.add(amount)));
        facility.setLastBorrowRound(roundNo);
        facility.setStatus("ACTIVE");
        facilityMapper.updateById(facility);
        LoanContract contract = new LoanContract();
        contract.setFacilityId(facility.getId());
        contract.setPrincipalOriginal(amount);
        contract.setPrincipalOutstanding(amount);
        contract.setInterestRate(product.interestRatePerRound());
        contract.setOpenedRound(roundNo);
        int maturityRounds = requestedMaturityRounds == null ? product.maturityRounds() : requestedMaturityRounds;
        if (maturityRounds < 1 || maturityRounds > product.maturityRounds()) {
            bad("贷款期限需在 1 到 " + product.maturityRounds() + " 回合之间");
        }
        contract.setMaturityRound(roundNo + maturityRounds - 1);
        contract.setOriginalMaturityRound(contract.getMaturityRound());
        contract.setExtensionCount(0);
        contract.setTotalExtendedRounds(0);
        contract.setLastInterestRound(roundNo - 1);
        contract.setAccruedInterest(ZERO);
        contract.setStatus("ACTIVE");
        contractMapper.insert(contract);
        participant.setCashBalance(GuessStockRules.money(participant.getCashBalance().add(amount)));
        participantMapper.updateById(participant);
        ledger(facility, contract.getId(), roundNo, "BORROW", amount, ZERO, amount,
                beforePrincipal, beforeInterest, requestId, "银行放款");
        return status(sessionId, participant, position, price, config);
    }

    public CreditStatus repay(Long sessionId, int roundNo, String phase, GuessStockParticipant participant,
                              GuessStockPosition position, BigDecimal price, GuessStockConfig config,
                              String productCode, BigDecimal requested, String requestId) {
        requireLoanEnabled(config);
        if (requested == null || requested.signum() <= 0) bad("还款金额必须大于0");
        if (requestLedger(sessionId, participant.getId(), requestId) != null) {
            return status(sessionId, participant, position, price, config);
        }
        CreditFacility facility = productCode == null ? null
                : facilityMapper.selectProductForUpdate(sessionId, participant.getId(), productCode);
        if (facility == null && productCode == null) {
            List<CreditFacility> debts = facilityMapper.selectAllForUpdate(sessionId, participant.getId()).stream()
                    .filter(item -> debt(item).signum() > 0).toList();
            if (debts.size() == 1) facility = debts.get(0);
        }
        if (facility == null || debt(facility).signum() <= 0) bad("当前没有需要归还的贷款");
        BigDecimal availableCash = participant.getCashBalance()
                .subtract(participant.getReservedCash() == null ? ZERO : participant.getReservedCash()).max(ZERO);
        BigDecimal amount = requested.min(availableCash).min(debt(facility));
        if (amount.signum() <= 0) bad("可用现金不足");
        applyRepayment(facility, participant, roundNo, amount, "MANUAL_REPAY", requestId, "玩家主动还款");
        return status(sessionId, participant, position, price, config);
    }

    public CreditStatus extend(Long sessionId, int roundNo, GuessStockParticipant participant,
                               GuessStockPosition position, BigDecimal price, GuessStockConfig config,
                               Long contractId, Integer requestedRounds, String requestId) {
        requireLoanEnabled(config);
        if (requestedRounds == null || requestedRounds < 1) bad("延期回合数必须大于 0");
        if (requestLedger(sessionId, participant.getId(), requestId) != null) {
            return status(sessionId, participant, position, price, config);
        }
        LoanContract contract = contractMapper.selectByIdForUpdate(contractId);
        CreditFacility facility = contract == null ? null : facilityMapper.selectById(contract.getFacilityId());
        if (facility == null || !facility.getSessionId().equals(sessionId)
                || !facility.getParticipantId().equals(participant.getId()) || !active(contract.getStatus())) {
            bad("贷款合同不存在或已经结清");
        }
        int extensionCount = contract.getExtensionCount() == null ? 0 : contract.getExtensionCount();
        if (extensionCount >= 2) bad("该笔贷款最多延期 2 次");
        if (contract.getMaturityRound() < roundNo) bad("贷款已经到期，不能延期");
        GuessStockConfig.CreditProductConfig product = product(config, facility.getProductCode());
        if (product == null) bad("贷款所属银行配置不存在");
        if (requestedRounds > product.maturityRounds()) bad("每次可延期 1 到 " + product.maturityRounds() + " 回合");
        CreditRiskCalculator.Snapshot risk = CreditRiskCalculator.calculate(participant.getCashBalance(), position.getQuantity(),
                price, facility.getPrincipalOutstanding(), facility.getAccruedInterest(), product, false);
        if (List.of("BLOCKED", "LIQUIDATION", "DEFAULTED").contains(risk.riskState())) bad("当前信用风险过高，不能延期");
        BigDecimal fee = GuessStockRules.money(contract.getPrincipalOutstanding().multiply(new BigDecimal("0.005")));
        BigDecimal availableCash = participant.getCashBalance()
                .subtract(participant.getReservedCash() == null ? ZERO : participant.getReservedCash()).max(ZERO);
        if (availableCash.compareTo(fee) < 0) bad("可用现金不足以支付延期费");
        BigDecimal beforePrincipal = facility.getPrincipalOutstanding();
        BigDecimal beforeInterest = facility.getAccruedInterest();
        participant.setCashBalance(GuessStockRules.money(participant.getCashBalance().subtract(fee)));
        participantMapper.updateById(participant);
        contract.setMaturityRound(contract.getMaturityRound() + requestedRounds);
        contract.setExtensionCount(extensionCount + 1);
        contract.setTotalExtendedRounds((contract.getTotalExtendedRounds() == null ? 0 : contract.getTotalExtendedRounds()) + requestedRounds);
        contract.setLastExtendedRound(roundNo);
        contract.setInterestRate(contract.getInterestRate().add(new BigDecimal("0.2")));
        contractMapper.updateById(contract);
        ledger(facility, contract.getId(), roundNo, "EXTENSION_FEE", ZERO, ZERO, fee.negate(),
                beforePrincipal, beforeInterest, requestId, "贷款延期 " + requestedRounds + " 回合，费率上调 0.2 个百分点");
        return status(sessionId, participant, position, price, config);
    }

    public AiCreditCapacity aiCapacity(Long sessionId, int roundNo, String phase,
                                       GuessStockParticipant participant, GuessStockPosition position,
                                       BigDecimal price, GuessStockConfig config, BigDecimal requestedExposure) {
        BigDecimal target = requestedExposure == null ? HUNDRED : requestedExposure.max(BigDecimal.ZERO);
        if (!Boolean.TRUE.equals(config.loanEnabled()) || target.compareTo(HUNDRED) <= 0) {
            return new AiCreditCapacity(null, ZERO, target.min(HUNDRED), "贷款未启用或目标敞口不需要融资");
        }
        List<CreditFacility> facilities = facilityMapper.selectAllForUpdate(sessionId, participant.getId());
        BigDecimal currentDebt = totalDebt(facilities);
        GuessStockConfig.CreditProductConfig product = CreditLimitCalculator.route(config.creditProducts(), target);
        if (product == null) return new AiCreditCapacity(null, ZERO, HUNDRED, "没有可用授信产品");
        BigDecimal executable = target.min(product.maxExposureRatio());
        if (!"OPENING_TRADING".equals(phase)) {
            return new AiCreditCapacity(product.code(), ZERO, executable,
                    "当前阶段不新增贷款，保留目标敞口供下一次开盘执行");
        }
        CreditFacility facility = facilities.stream().filter(item -> product.code().equalsIgnoreCase(item.getProductCode()))
                .findFirst().orElse(null);
        if (facility != null && "DEFAULTED".equals(facility.getStatus())) {
            return new AiCreditCapacity(product.code(), ZERO, executable, "授信账户已经违约");
        }
        CreditLimitCalculator.Limit limit = CreditLimitCalculator.calculate(participant.getInitialCash(),
                participant.getCashBalance(), position.getQuantity(), price,
                totalPrincipal(facilities), totalInterest(facilities),
                facility == null ? ZERO : facility.getPrincipalOutstanding(),
                facility == null ? ZERO : facility.getAccruedInterest(), product);
        BigDecimal minimum = participant.getInitialCash().multiply(product.minBorrowRatio())
                .divide(HUNDRED, 10, RoundingMode.HALF_UP);
        BigDecimal available = limit.availableCredit();
        if (available.compareTo(minimum) < 0) available = ZERO;
        if (facility != null && facility.getLastBorrowRound() != null
                && roundNo - facility.getLastBorrowRound() < product.borrowCooldownRounds()) available = ZERO;
        return new AiCreditCapacity(product.code(), available, executable,
                target.compareTo(product.maxExposureRatio()) > 0
                        ? "目标已按所选产品上限裁剪" : "按满足目标且仍有额度的最低利率产品路由");
    }

    public BigDecimal autoBorrow(Long sessionId, int roundNo, String phase, GuessStockParticipant participant,
                                 GuessStockPosition position, BigDecimal price, GuessStockConfig config,
                                 AiCreditCapacity capacity, BigDecimal requested, String requestId) {
        if (capacity == null || capacity.productCode() == null || requested == null || requested.signum() <= 0) return ZERO;
        BigDecimal amount = GuessStockRules.money(requested.min(capacity.availableCredit()));
        GuessStockConfig.CreditProductConfig product = product(config, capacity.productCode());
        BigDecimal minimum = participant.getInitialCash().multiply(product.minBorrowRatio())
                .divide(HUNDRED, 10, RoundingMode.HALF_UP);
        if (amount.compareTo(minimum) < 0) return ZERO;
        borrow(sessionId, roundNo, phase, participant, position, price, config,
                capacity.productCode(), amount, null, requestId);
        return amount;
    }

    public void autoRepayAll(Long sessionId, int roundNo, GuessStockParticipant participant) {
        List<CreditFacility> facilities = facilityMapper.selectAllForUpdate(sessionId, participant.getId());
        for (CreditFacility facility : facilities.stream().filter(item -> debt(item).signum() > 0)
                .sorted(Comparator.comparing(CreditFacility::getLastBorrowRound,
                        Comparator.nullsFirst(Comparator.naturalOrder()))).toList()) {
            if (participant.getCashBalance().signum() <= 0) break;
            applyRepayment(facility, participant, roundNo, participant.getCashBalance().min(debt(facility)),
                    "AI_AUTO_REPAY", null, "AI 目标敞口降至 100% 以下后自动偿还贷款");
        }
    }

    public long planForcedShares(Long sessionId, int roundNo, GuessStockParticipant participant,
                                 long projectedShares, BigDecimal projectedCash, BigDecimal referencePrice,
                                 GuessStockConfig config, boolean finalRound) {
        if (!Boolean.TRUE.equals(config.loanEnabled())) return 0;
        List<CreditFacility> facilities = facilityMapper.selectAllForUpdate(sessionId, participant.getId());
        if (totalDebt(facilities).signum() <= 0) return 0;
        facilities.forEach(item -> accrueInterest(item, roundNo));
        BigDecimal principal = totalPrincipal(facilities);
        BigDecimal interest = totalInterest(facilities);
        GuessStockConfig.CreditProductConfig product = riskProduct(config, facilities, projectedCash,
                projectedShares, referencePrice, principal, interest);
        boolean matured = finalRound || facilities.stream().flatMap(item -> contracts(item).stream())
                .anyMatch(item -> active(item.getStatus())
                && item.getMaturityRound() <= roundNo);
        CreditRiskCalculator.Snapshot risk = CreditRiskCalculator.calculate(projectedCash, projectedShares,
                referencePrice, principal, interest, product, false);
        if (!matured && !"LIQUIDATION".equals(risk.riskState())) return 0;
        BigDecimal payable = matured ? facilities.stream().map(item -> maturityDue(item, roundNo, finalRound))
                .reduce(ZERO, BigDecimal::add) : principal.add(interest);
        BigDecimal remainingPayable = payable.subtract(projectedCash.min(payable)).max(BigDecimal.ZERO);
        if (remainingPayable.signum() <= 0 || projectedShares <= 0) return 0;
        return matured ? sharesForDebt(projectedShares, referencePrice, remainingPayable)
                : ForcedLiquidationPlanner.minimumShares(projectedShares, referencePrice,
                    ZERO, principal, interest, product);
    }

    public long settleParticipant(Long sessionId, int roundNo, String stage, GuessStockParticipant participant,
                                  GuessStockPosition position, BigDecimal price, GuessStockConfig config,
                                  boolean finalRound) {
        if (!Boolean.TRUE.equals(config.loanEnabled())) return 0;
        List<CreditFacility> facilities = facilityMapper.selectAllForUpdate(sessionId, participant.getId());
        if (totalDebt(facilities).signum() <= 0) {
            saveSnapshot(sessionId, roundNo, stage, participant, position, price, facilities, config, 0);
            return 0;
        }
        facilities.forEach(item -> accrueInterest(item, roundNo));
        boolean matured = finalRound || facilities.stream().flatMap(item -> contracts(item).stream())
                .anyMatch(item -> active(item.getStatus()) && item.getMaturityRound() <= roundNo);
        BigDecimal principal = totalPrincipal(facilities);
        BigDecimal interest = totalInterest(facilities);
        GuessStockConfig.CreditProductConfig product = riskProduct(config, facilities, participant, position, price,
                principal, interest);
        CreditRiskCalculator.Snapshot risk = CreditRiskCalculator.calculate(participant.getCashBalance(), position.getQuantity(),
                price, principal, interest, product, false);
        boolean liquidation = matured || "LIQUIDATION".equals(risk.riskState());
        long forcedShares = 0;
        if (liquidation) {
            List<CreditFacility> repaymentOrder = facilities.stream().filter(item -> debt(item).signum() > 0)
                    .sorted(Comparator.comparingInt(item -> nearestMaturity(item, Integer.MAX_VALUE))).toList();
            repayFacilities(repaymentOrder, participant, roundNo, matured, finalRound,
                    matured ? "MATURITY_REPAY" : "LIQUIDATION_REPAY",
                    matured ? "贷款到期自动还款" : "担保率触发自动还款");
            BigDecimal remainingPayable = matured
                    ? repaymentOrder.stream().map(item -> maturityDue(item, roundNo, finalRound)).reduce(ZERO, BigDecimal::add)
                    : totalDebt(repaymentOrder);
            if (remainingPayable.signum() > 0 && position.getQuantity() > 0) {
                forcedShares = matured || finalRound ? sharesForDebt(position.getQuantity(), price, remainingPayable)
                        : ForcedLiquidationPlanner.minimumShares(position.getQuantity(), price, ZERO,
                        totalPrincipal(repaymentOrder), totalInterest(repaymentOrder), product);
                BigDecimal proceeds = GuessStockRules.money(price.multiply(BigDecimal.valueOf(forcedShares)));
                position.setQuantity(position.getQuantity() - forcedShares);
                if (position.getQuantity() == 0) position.setAverageCost(ZERO);
                positionMapper.updateById(position);
                participant.setCashBalance(GuessStockRules.money(participant.getCashBalance().add(proceeds)));
                participantMapper.updateById(participant);
                repayFacilities(repaymentOrder, participant, roundNo, matured, finalRound,
                        matured ? "MATURITY_LIQUIDATION" : "RISK_LIQUIDATION",
                        "按最终阶段价格强制卖出 " + forcedShares + " 股");
            }
            if (totalDebt(facilities).signum() > 0 && position.getQuantity() == 0 && participant.getCashBalance().signum() == 0) {
                for (CreditFacility facility : facilities.stream().filter(item -> debt(item).signum() > 0).toList()) {
                    facility.setStatus("DEFAULTED");
                    facilityMapper.updateById(facility);
                    ledger(facility, null, roundNo, "DEFAULT", ZERO, ZERO, ZERO,
                            facility.getPrincipalOutstanding(), facility.getAccruedInterest(), null, "资产不足以清偿债务");
                }
            }
        }
        saveSnapshot(sessionId, roundNo, stage, participant, position, price, facilities, config, forcedShares);
        return forcedShares;
    }

    public BigDecimal debt(Long sessionId, Long participantId) {
        return totalDebt(findAll(sessionId, participantId));
    }

    public boolean hasOutstandingDebt(Long sessionId, List<Long> participantIds) {
        if (participantIds == null || participantIds.isEmpty()) return false;
        return facilityMapper.selectCount(new LambdaQueryWrapper<CreditFacility>()
                .eq(CreditFacility::getSessionId, sessionId)
                .in(CreditFacility::getParticipantId, participantIds)
                .and(wrapper -> wrapper.gt(CreditFacility::getPrincipalOutstanding, ZERO)
                        .or().gt(CreditFacility::getAccruedInterest, ZERO))) > 0;
    }

    public void reset(Long sessionId) {
        facilityMapper.delete(new LambdaQueryWrapper<CreditFacility>().eq(CreditFacility::getSessionId, sessionId));
    }

    private void accrueInterest(CreditFacility facility, int roundNo) {
        BigDecimal beforePrincipal = facility.getPrincipalOutstanding();
        BigDecimal beforeInterest = facility.getAccruedInterest();
        BigDecimal added = ZERO;
        for (LoanContract contract : contracts(facility)) {
            if (!active(contract.getStatus()) || contract.getLastInterestRound() >= roundNo) continue;
            BigDecimal interest = LoanInterestCalculator.simpleInterest(contract.getPrincipalOutstanding(),
                    contract.getInterestRate());
            contract.setAccruedInterest(GuessStockRules.money(contract.getAccruedInterest().add(interest)));
            contract.setLastInterestRound(roundNo);
            contractMapper.updateById(contract);
            added = added.add(interest);
        }
        if (added.signum() > 0) {
            facility.setAccruedInterest(GuessStockRules.money(facility.getAccruedInterest().add(added)));
            facilityMapper.updateById(facility);
            ledger(facility, null, roundNo, "INTEREST_ACCRUAL", ZERO, added, ZERO,
                    beforePrincipal, beforeInterest, null, "按未偿本金计收本回合简单利息");
        }
    }

    private void applyRepayment(CreditFacility facility, GuessStockParticipant participant, int roundNo,
                                BigDecimal amount, String type, String requestId, String reason) {
        BigDecimal beforePrincipal = facility.getPrincipalOutstanding();
        BigDecimal beforeInterest = facility.getAccruedInterest();
        BigDecimal remaining = GuessStockRules.money(amount);
        BigDecimal interestPaid = remaining.min(facility.getAccruedInterest());
        remaining = remaining.subtract(interestPaid);
        facility.setAccruedInterest(GuessStockRules.money(facility.getAccruedInterest().subtract(interestPaid)));
        allocateInterest(facility, interestPaid);
        BigDecimal principalPaid = remaining.min(facility.getPrincipalOutstanding());
        facility.setPrincipalOutstanding(GuessStockRules.money(facility.getPrincipalOutstanding().subtract(principalPaid)));
        allocatePrincipal(facility, principalPaid);
        BigDecimal paid = interestPaid.add(principalPaid);
        participant.setCashBalance(GuessStockRules.money(participant.getCashBalance().subtract(paid)));
        participantMapper.updateById(participant);
        facility.setLastRepayRound(roundNo);
        if (debt(facility).signum() == 0) {
            facility.setStatus("CLOSED");
            facility.setClosedRound(roundNo);
        }
        facilityMapper.updateById(facility);
        ledger(facility, null, roundNo, type, principalPaid.negate(), interestPaid.negate(), paid.negate(),
                beforePrincipal, beforeInterest, requestId, reason);
    }

    private void allocateInterest(CreditFacility facility, BigDecimal amount) {
        BigDecimal remaining = amount;
        for (LoanContract contract : contracts(facility)) {
            if (remaining.signum() <= 0) break;
            BigDecimal paid = remaining.min(contract.getAccruedInterest());
            contract.setAccruedInterest(GuessStockRules.money(contract.getAccruedInterest().subtract(paid)));
            remaining = remaining.subtract(paid);
            contractMapper.updateById(contract);
        }
    }

    private void allocatePrincipal(CreditFacility facility, BigDecimal amount) {
        BigDecimal remaining = amount;
        for (LoanContract contract : contracts(facility)) {
            if (remaining.signum() <= 0) break;
            BigDecimal paid = remaining.min(contract.getPrincipalOutstanding());
            contract.setPrincipalOutstanding(GuessStockRules.money(contract.getPrincipalOutstanding().subtract(paid)));
            remaining = remaining.subtract(paid);
            if (contract.getPrincipalOutstanding().signum() == 0 && contract.getAccruedInterest().signum() == 0) {
                contract.setStatus("CLOSED");
            } else if (contract.getPrincipalOutstanding().compareTo(contract.getPrincipalOriginal()) < 0) {
                contract.setStatus("PARTIAL_REPAID");
            }
            contractMapper.updateById(contract);
        }
    }

    private CreditFacility createFacility(Long sessionId, Long participantId, int roundNo,
                                          GuessStockConfig.CreditProductConfig product) {
        CreditFacility facility = new CreditFacility();
        facility.setSessionId(sessionId);
        facility.setParticipantId(participantId);
        facility.setPrincipalOutstanding(ZERO);
        facility.setAccruedInterest(ZERO);
        facility.setCreatedRound(roundNo);
        facility.setRowVersion(0L);
        configureFacility(facility, product, roundNo);
        facilityMapper.insert(facility);
        return facility;
    }

    private void configureFacility(CreditFacility facility, GuessStockConfig.CreditProductConfig product, int roundNo) {
        facility.setProductCode(product.code());
        facility.setProductName(product.name());
        facility.setRiskLevel(product.riskLevel());
        facility.setProductSnapshotJson(write(product));
        facility.setStatus("ACTIVE");
        facility.setClosedRound(null);
        if (facility.getCreatedRound() == null) facility.setCreatedRound(roundNo);
        if (facility.getId() != null) facilityMapper.updateById(facility);
    }

    private void ledger(CreditFacility facility, Long contractId, int roundNo, String type,
                        BigDecimal principalDelta, BigDecimal interestDelta, BigDecimal cashDelta,
                        BigDecimal beforePrincipal, BigDecimal beforeInterest, String requestId, String reason) {
        LoanLedger row = new LoanLedger();
        row.setSessionId(facility.getSessionId());
        row.setParticipantId(facility.getParticipantId());
        row.setFacilityId(facility.getId());
        row.setContractId(contractId);
        row.setRoundNo(roundNo);
        row.setEntryType(type);
        row.setPrincipalDelta(GuessStockRules.money(principalDelta));
        row.setInterestDelta(GuessStockRules.money(interestDelta));
        row.setCashDelta(GuessStockRules.money(cashDelta));
        row.setBeforePrincipal(GuessStockRules.money(beforePrincipal));
        row.setAfterPrincipal(facility.getPrincipalOutstanding());
        row.setBeforeInterest(GuessStockRules.money(beforeInterest));
        row.setAfterInterest(facility.getAccruedInterest());
        row.setRequestId(requestId);
        row.setReason(reason);
        ledgerMapper.insert(row);
    }

    private void saveSnapshot(Long sessionId, int roundNo, String stage, GuessStockParticipant participant,
                              GuessStockPosition position, BigDecimal price, List<CreditFacility> facilities,
                              GuessStockConfig config, long forcedShares) {
        BigDecimal principal = totalPrincipal(facilities);
        BigDecimal interest = totalInterest(facilities);
        GuessStockConfig.CreditProductConfig product = riskProduct(config, facilities, participant, position, price,
                principal, interest);
        boolean defaulted = facilities.stream().anyMatch(item -> "DEFAULTED".equals(item.getStatus()));
        CreditRiskCalculator.Snapshot risk = CreditRiskCalculator.calculate(participant.getCashBalance(), position.getQuantity(),
                price, principal, interest, product, defaulted);
        CreditSnapshot row = snapshotMapper.selectOne(new LambdaQueryWrapper<CreditSnapshot>()
                .eq(CreditSnapshot::getSessionId, sessionId).eq(CreditSnapshot::getParticipantId, participant.getId())
                .eq(CreditSnapshot::getRoundNo, roundNo).eq(CreditSnapshot::getStage, stage).last("LIMIT 1"));
        if (row == null) row = new CreditSnapshot();
        row.setSessionId(sessionId);
        row.setParticipantId(participant.getId());
        row.setRoundNo(roundNo);
        row.setStage(stage);
        row.setProductCode(facilities.size() > 1 ? "MULTI" : facilities.stream()
                .findFirst().map(CreditFacility::getProductCode).orElse(null));
        row.setCashValue(participant.getCashBalance());
        row.setStockValue(risk.stockValue());
        row.setNetAsset(risk.netAsset());
        row.setPrincipal(principal);
        row.setAccruedInterest(interest);
        row.setCollateralValue(risk.collateralValue());
        row.setExposurePercent(risk.exposurePercent());
        row.setLtvPercent(risk.ltvPercent());
        row.setRiskState(risk.riskState());
        row.setForcedLiquidationShares(forcedShares);
        if (row.getId() == null) snapshotMapper.insert(row); else snapshotMapper.updateById(row);
    }

    private List<LoanContract> contracts(CreditFacility facility) {
        if (facility == null || facility.getId() == null) return List.of();
        return contractMapper.selectList(new LambdaQueryWrapper<LoanContract>()
                .eq(LoanContract::getFacilityId, facility.getId()).orderByAsc(LoanContract::getMaturityRound)
                .orderByAsc(LoanContract::getId));
    }

    private List<LoanLedger> ledgers(Long sessionId, Long participantId) {
        List<LoanLedger> rows = ledgerMapper.selectList(new LambdaQueryWrapper<LoanLedger>()
                .eq(LoanLedger::getSessionId, sessionId).eq(LoanLedger::getParticipantId, participantId)
                .orderByDesc(LoanLedger::getId).last("LIMIT 100"));
        return List.copyOf(rows);
    }

    private List<LoanLedger> ledgers(CreditFacility facility) {
        if (facility == null || facility.getId() == null) return List.of();
        return List.copyOf(ledgerMapper.selectList(new LambdaQueryWrapper<LoanLedger>()
                .eq(LoanLedger::getFacilityId, facility.getId()).orderByDesc(LoanLedger::getId).last("LIMIT 100")));
    }

    private void repayFacilities(List<CreditFacility> facilities, GuessStockParticipant participant, int roundNo,
                                 boolean maturedOnly, boolean finalRound, String type, String reason) {
        for (CreditFacility facility : facilities) {
            if (participant.getCashBalance().signum() <= 0) break;
            BigDecimal payable = maturedOnly ? maturityDue(facility, roundNo, finalRound) : debt(facility);
            BigDecimal amount = participant.getCashBalance().min(payable);
            if (amount.signum() > 0) applyRepayment(facility, participant, roundNo, amount, type, null, reason);
        }
    }

    private int nearestMaturity(CreditFacility facility, int fallback) {
        return contracts(facility).stream().filter(item -> active(item.getStatus()))
                .map(LoanContract::getMaturityRound).min(Integer::compareTo).orElse(fallback);
    }

    private GuessStockConfig.CreditProductConfig riskProduct(GuessStockConfig config,
                                                              List<CreditFacility> facilities,
                                                              GuessStockParticipant participant,
                                                              GuessStockPosition position, BigDecimal price,
                                                              BigDecimal principal, BigDecimal interest) {
        return riskProduct(config, facilities, participant.getCashBalance(), position.getQuantity(), price,
                principal, interest);
    }

    private GuessStockConfig.CreditProductConfig riskProduct(GuessStockConfig config,
                                                              List<CreditFacility> facilities,
                                                              BigDecimal cash, long shares, BigDecimal price,
                                                              BigDecimal principal, BigDecimal interest) {
        GuessStockConfig.CreditProductConfig selected = null;
        int selectedSeverity = -1;
        for (CreditFacility facility : facilities) {
            if (debt(facility).signum() <= 0) continue;
            GuessStockConfig.CreditProductConfig candidate = product(config, facility.getProductCode());
            if (candidate == null) continue;
            String state = CreditRiskCalculator.calculate(cash, shares, price, principal, interest, candidate,
                    "DEFAULTED".equals(facility.getStatus())).riskState();
            int severity = riskSeverity(state);
            if (selected == null || severity > selectedSeverity
                    || (severity == selectedSeverity && candidate.liquidationLtv().compareTo(selected.liquidationLtv()) < 0)) {
                selected = candidate;
                selectedSeverity = severity;
            }
        }
        return selected;
    }

    private static int riskSeverity(String state) {
        return switch (state) {
            case "DEFAULTED" -> 4;
            case "LIQUIDATION" -> 3;
            case "BLOCKED" -> 2;
            case "WARNING" -> 1;
            default -> 0;
        };
    }

    private static BigDecimal totalPrincipal(List<CreditFacility> facilities) {
        return facilities.stream().map(CreditFacility::getPrincipalOutstanding)
                .filter(value -> value != null).reduce(ZERO, BigDecimal::add);
    }

    private static BigDecimal totalInterest(List<CreditFacility> facilities) {
        return facilities.stream().map(CreditFacility::getAccruedInterest)
                .filter(value -> value != null).reduce(ZERO, BigDecimal::add);
    }

    private static BigDecimal totalDebt(List<CreditFacility> facilities) {
        return totalPrincipal(facilities).add(totalInterest(facilities));
    }

    private LoanLedger requestLedger(Long sessionId, Long participantId, String requestId) {
        if (requestId == null || requestId.isBlank()) bad("缺少请求幂等编号");
        return ledgerMapper.selectOne(new LambdaQueryWrapper<LoanLedger>()
                .eq(LoanLedger::getSessionId, sessionId).eq(LoanLedger::getParticipantId, participantId)
                .eq(LoanLedger::getRequestId, requestId).last("LIMIT 1"));
    }

    private static GuessStockConfig.CreditProductConfig product(GuessStockConfig config, String code) {
        if (config.creditProducts() == null || code == null) return null;
        return config.creditProducts().stream().filter(item -> code.equalsIgnoreCase(item.code())).findFirst().orElse(null);
    }

    private static BigDecimal debt(CreditFacility facility) {
        return facility.getPrincipalOutstanding().add(facility.getAccruedInterest());
    }
    private static long sharesForDebt(long availableShares, BigDecimal price, BigDecimal debt) {
        if (availableShares <= 0 || price == null || price.signum() <= 0 || debt == null || debt.signum() <= 0) return 0;
        long required = debt.divide(price, 0, RoundingMode.CEILING).longValue();
        return Math.min(availableShares, required);
    }
    private BigDecimal maturityDue(CreditFacility facility, int roundNo, boolean finalRound) {
        BigDecimal principalDue = contracts(facility).stream().filter(item -> active(item.getStatus()))
                .filter(item -> finalRound || item.getMaturityRound() <= roundNo)
                .map(LoanContract::getPrincipalOutstanding).reduce(ZERO, BigDecimal::add);
        if (!finalRound && principalDue.signum() == 0) return ZERO;
        return GuessStockRules.money(principalDue.add(facility.getAccruedInterest()));
    }
    private static boolean active(String status) { return List.of("ACTIVE", "PARTIAL_REPAID", "MATURED").contains(status); }
    private static void requireLoanEnabled(GuessStockConfig config) { if (!Boolean.TRUE.equals(config.loanEnabled())) bad("当前游戏未开启银行贷款"); }
    private static void bad(String message) { throw new BusinessException(ErrorCode.BAD_REQUEST, message); }
    private String write(Object value) { try { return objectMapper.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); } }

    public record CreditStatus(boolean enabled, CreditFacility facility, GuessStockConfig.CreditProductConfig product,
                               BigDecimal principal, BigDecimal accruedInterest, BigDecimal availableCredit,
                               Map<String, BigDecimal> productAvailableCredit, int nearestMaturityRound,
                               CreditRiskCalculator.Snapshot risk,
                               BigDecimal totalInterestCharged, long totalForcedLiquidationShares,
                               List<LoanLedger> ledgers, List<LoanContract> contracts,
                               List<BankStatus> banks) {}

    public record BankStatus(GuessStockConfig.CreditProductConfig product, CreditFacility facility,
                             BigDecimal principal, BigDecimal accruedInterest, BigDecimal availableCredit,
                             int nearestMaturityRound, List<LoanLedger> ledgers,
                             List<LoanContract> contracts) {}

    public record AiCreditCapacity(String productCode, BigDecimal availableCredit,
                                   BigDecimal executableExposure, String reason) {}
}
