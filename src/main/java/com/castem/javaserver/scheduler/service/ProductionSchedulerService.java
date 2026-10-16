package com.castem.javaserver.scheduler.service;

import com.castem.javaserver.scheduler.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

@Service
public class ProductionSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(ProductionSchedulerService.class);

    private static final int SHIPPING_BUFFER_WORKING_DAYS = 6;
    private static final int PLANNING_BUFFER_WORKING_DAYS = 6;
    private static final int TOTAL_BUFFER_WORKING_DAYS = SHIPPING_BUFFER_WORKING_DAYS + PLANNING_BUFFER_WORKING_DAYS;

    /**
     * Main scheduling entry point.
     */
    public ScheduleResponse schedule(ScheduleRequest request) {
        log.info("Starting production schedule calculation for Order [{}] Item [{}]", request.orderNo(), request.itemCode());

        Set<LocalDate> holidaySet = new HashSet<>(request.holidays());
        boolean includeSat = Boolean.TRUE.equals(request.includeSaturday());
        BigDecimal maxHoursPerDay = request.maxWorkingHoursPerDay();

        // 1. Calculate Net Demand & Production Qty
        BigDecimal netRequiredQty = request.orderQty().subtract(request.allocatedQty());
        if (netRequiredQty.compareTo(BigDecimal.ZERO) < 0) {
            netRequiredQty = BigDecimal.ZERO;
        }

        BigDecimal rejectionMultiplier = BigDecimal.ONE.add(
            request.rejectionRate().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
        );
        BigDecimal totalProductionQty = netRequiredQty.multiply(rejectionMultiplier)
            .setScale(0, RoundingMode.CEILING);

        LocalDate today = LocalDate.now();
        LocalDate targetEndDate = null;
        if (request.requestedEtd() != null) {
            targetEndDate = subtractWorkingDays(request.requestedEtd(), TOTAL_BUFFER_WORKING_DAYS, holidaySet, includeSat);
        } else {
            targetEndDate = addWorkingDays(today, 30, holidaySet, includeSat);
        }

        String productionId = "PROD-" + (request.orderNo() != null ? request.orderNo() : UUID.randomUUID().toString().substring(0, 8));

        // If no production qty required (e.g. order already 100% fulfilled by FG allocation)
        if (totalProductionQty.compareTo(BigDecimal.ZERO) <= 0) {
            return new ScheduleResponse(
                request.orderNo(),
                request.salesOrderId(),
                productionId,
                netRequiredQty,
                BigDecimal.ZERO,
                0,
                request.requestedEtd(),
                targetEndDate,
                today,
                today,
                BigDecimal.ZERO,
                false,
                0,
                "FORWARD",
                List.of(),
                List.of(),
                List.of()
            );
        }

        // 2. Split into lots
        BigDecimal autoSplit = request.autoSplitQty().compareTo(BigDecimal.ZERO) > 0
            ? request.autoSplitQty()
            : totalProductionQty;

        int lotCount = totalProductionQty.divide(autoSplit, 0, RoundingMode.CEILING).intValue();
        if (lotCount <= 0) lotCount = 1;

        List<BigDecimal> lotQuantities = new ArrayList<>();
        BigDecimal remaining = totalProductionQty;
        for (int i = 0; i < lotCount; i++) {
            if (i == lotCount - 1) {
                lotQuantities.add(remaining);
            } else {
                BigDecimal lotQty = autoSplit.min(remaining);
                lotQuantities.add(lotQty);
                remaining = remaining.subtract(lotQty);
            }
        }

        // 3. Filter routes
        List<RouteStepDto> baseSteps = new ArrayList<>();
        if (request.itemRoutes() != null) {
            for (RouteStepDto step : request.itemRoutes()) {
                if (Boolean.TRUE.equals(request.hasFabrication())) {
                    if (!Boolean.TRUE.equals(step.hasPacking())) {
                        baseSteps.add(step);
                    }
                } else {
                    baseSteps.add(step);
                }
            }
        }

        List<RouteStepDto> fabSteps = new ArrayList<>();
        if (Boolean.TRUE.equals(request.hasFabrication()) && request.fabRoutes() != null) {
            fabSteps.addAll(request.fabRoutes());
        }

        // 4. Decision: Backward vs Forward Scheduling
        boolean forceForward = request.startDateOverride() != null;
        ScheduleResult result = null;

        if (!forceForward) {
            // Try Backward scheduling
            result = runBackwardScheduling(
                request,
                lotQuantities,
                baseSteps,
                fabSteps,
                targetEndDate,
                holidaySet,
                includeSat,
                maxHoursPerDay
            );

            // Check if backward schedule starts in the past
            if (result.scheduledStartDate.isBefore(today)) {
                log.info("Backward schedule start date [{}] is before today [{}]. Switching to Forward scheduling from tomorrow.",
                    result.scheduledStartDate, today);
                forceForward = true;
            }
        }

        if (forceForward) {
            LocalDate startFrom = request.startDateOverride() != null
                ? ensureWorkingDayForward(request.startDateOverride(), holidaySet, includeSat)
                : addWorkingDays(today, 1, holidaySet, includeSat);

            result = runForwardScheduling(
                request,
                lotQuantities,
                baseSteps,
                fabSteps,
                startFrom,
                targetEndDate,
                holidaySet,
                includeSat,
                maxHoursPerDay
            );
        }

        boolean isDelayed = result.scheduledEndDate.isAfter(targetEndDate);
        int delayDays = 0;
        if (isDelayed) {
            delayDays = Math.max(0, countWorkingDays(targetEndDate, result.scheduledEndDate, holidaySet, includeSat) - 1);
        }

        BigDecimal totalWorkingDays = BigDecimal.valueOf(
            countWorkingDays(result.scheduledStartDate, result.scheduledEndDate, holidaySet, includeSat)
        );

        return new ScheduleResponse(
            request.orderNo(),
            request.salesOrderId(),
            productionId,
            netRequiredQty,
            totalProductionQty,
            lotCount,
            request.requestedEtd(),
            targetEndDate,
            result.scheduledStartDate,
            result.scheduledEndDate,
            totalWorkingDays,
            isDelayed,
            delayDays,
            result.mode,
            result.slips,
            result.resultingLoads,
            result.capacityWarnings
        );
    }

    private static class ScheduleResult {
        String mode;
        LocalDate scheduledStartDate;
        LocalDate scheduledEndDate;
        List<ScheduledSlipDto> slips;
        List<DailyLoadDto> resultingLoads;
        List<CapacityWarningDto> capacityWarnings;
    }

    /**
     * Backward scheduling algorithm.
     */
    private ScheduleResult runBackwardScheduling(
        ScheduleRequest request,
        List<BigDecimal> lotQuantities,
        List<RouteStepDto> baseSteps,
        List<RouteStepDto> fabSteps,
        LocalDate targetEndDate,
        Set<LocalDate> holidaySet,
        boolean includeSat,
        BigDecimal maxHoursPerDay
    ) {
        CapacityTracker tracker = new CapacityTracker(request.existingLoads());
        List<CapacityWarningDto> warnings = new ArrayList<>();
        int lotCount = lotQuantities.size();

        // Data holders for each lot
        List<ScheduledSlipDto> slips = new ArrayList<>(lotCount);
        for (int i = 0; i < lotCount; i++) {
            slips.add(null);
        }

        boolean hasFab = Boolean.TRUE.equals(request.hasFabrication()) && !fabSteps.isEmpty();

        // 1. Determine Base Stage Deadline
        int fabTotalWorkingDays = 0;
        if (hasFab) {
            for (RouteStepDto step : fabSteps) {
                BigDecimal stepHours = calculateStepHours(step, lotQuantities.get(0), maxHoursPerDay);
                fabTotalWorkingDays += calculateStepWorkingDays(stepHours, maxHoursPerDay);
            }
        }

        LocalDate baseStageTargetEndDate = hasFab
            ? subtractWorkingDays(targetEndDate, fabTotalWorkingDays, holidaySet, includeSat)
            : targetEndDate;

        // 2. Schedule base item stage backward
        Map<Integer, List<ScheduledProcessDto>> lotBaseProcesses = new HashMap<>();
        Map<Integer, LocalDate> lotBaseStartDates = new HashMap<>();
        Map<Integer, LocalDate> lotBaseEndDates = new HashMap<>();

        int maxConcurrent = request.maxConcurrentLots() != null && request.maxConcurrentLots() > 0 ? request.maxConcurrentLots() : 1;

        for (int lotIdx = lotCount - 1; lotIdx >= 0; lotIdx--) {
            String lotNo = formatLotNo(request.orderNo(), lotIdx + 1);
            BigDecimal lotQty = lotQuantities.get(lotIdx);
            List<ScheduledProcessDto> procList = new ArrayList<>();

            int batchOffset = (lotCount - 1 - lotIdx) / maxConcurrent;
            LocalDate currentEndDate = subtractWorkingDays(baseStageTargetEndDate, batchOffset, holidaySet, includeSat);

            for (int stepIdx = baseSteps.size() - 1; stepIdx >= 0; stepIdx--) {
                RouteStepDto step = baseSteps.get(stepIdx);
                BigDecimal stepHours = calculateStepHours(step, lotQty, maxHoursPerDay);
                int requiredDays = calculateStepWorkingDays(stepHours, maxHoursPerDay);

                LocalDate stepEndDate = tracker.findAvailableDateBackward(
                    currentEndDate,
                    step,
                    lotQty,
                    request.stdItemWt(),
                    holidaySet,
                    includeSat,
                    warnings,
                    lotNo
                );

                LocalDate stepStartDate = subtractWorkingDays(stepEndDate, Math.max(0, requiredDays - 1), holidaySet, includeSat);

                ScheduledProcessDto procDto = new ScheduledProcessDto(
                    step.stepNo() != null ? step.stepNo() : (stepIdx + 1),
                    step.processId(),
                    step.processName(),
                    request.itemCode(),
                    false,
                    stepStartDate,
                    stepEndDate,
                    step.leadTime(),
                    stepHours,
                    lotQty,
                    stepIdx > 0 ? stepIdx : null,
                    stepIdx < baseSteps.size() - 1 ? List.of(stepIdx + 2) : List.of()
                );
                procList.add(0, procDto);

                currentEndDate = subtractWorkingDays(stepStartDate, 1, holidaySet, includeSat);
            }

            lotBaseProcesses.put(lotIdx, procList);
            if (!procList.isEmpty()) {
                LocalDate bStart = procList.get(0).scheduledStartDate();
                LocalDate bEnd = procList.get(procList.size() - 1).scheduledEndDate();
                lotBaseStartDates.put(lotIdx, bStart);
                lotBaseEndDates.put(lotIdx, bEnd);
            } else {
                lotBaseStartDates.put(lotIdx, baseStageTargetEndDate);
                lotBaseEndDates.put(lotIdx, baseStageTargetEndDate);
            }
        }

        // 3. Fabrication Phase: ALL lots start fabrication concurrently on the same start date
        Map<Integer, List<ScheduledProcessDto>> lotFabProcesses = new HashMap<>();
        Map<Integer, LocalDate> lotFabStartDates = new HashMap<>();
        Map<Integer, LocalDate> lotFabEndDates = new HashMap<>();

        if (hasFab) {
            LocalDate maxBaseEnd = lotBaseEndDates.values().stream().max(LocalDate::compareTo).orElse(baseStageTargetEndDate);
            LocalDate fabCommonStartDate = addWorkingDays(maxBaseEnd, 1, holidaySet, includeSat);

            for (int lotIdx = 0; lotIdx < lotCount; lotIdx++) {
                String lotNo = formatLotNo(request.orderNo(), lotIdx + 1);
                BigDecimal lotQty = lotQuantities.get(lotIdx);
                List<ScheduledProcessDto> procList = new ArrayList<>();

                LocalDate currentStartDate = fabCommonStartDate;

                for (int stepIdx = 0; stepIdx < fabSteps.size(); stepIdx++) {
                    RouteStepDto step = fabSteps.get(stepIdx);
                    BigDecimal stepHours = calculateStepHours(step, lotQty, maxHoursPerDay);
                    int requiredDays = calculateStepWorkingDays(stepHours, maxHoursPerDay);

                    LocalDate stepStartDate = tracker.findAvailableDateForward(
                        currentStartDate,
                        step,
                        lotQty,
                        request.stdItemWt(),
                        holidaySet,
                        includeSat,
                        warnings,
                        lotNo
                    );

                    LocalDate stepEndDate = addWorkingDays(stepStartDate, Math.max(0, requiredDays - 1), holidaySet, includeSat);

                    ScheduledProcessDto procDto = new ScheduledProcessDto(
                        step.stepNo() != null ? step.stepNo() : (baseSteps.size() + stepIdx + 1),
                        step.processId(),
                        step.processName(),
                        request.fabCode() != null ? request.fabCode() : request.itemCode(),
                        true,
                        stepStartDate,
                        stepEndDate,
                        step.leadTime(),
                        stepHours,
                        lotQty,
                        stepIdx > 0 ? (baseSteps.size() + stepIdx) : null,
                        stepIdx < fabSteps.size() - 1 ? List.of(baseSteps.size() + stepIdx + 2) : List.of()
                    );
                    procList.add(procDto);

                    currentStartDate = addWorkingDays(stepEndDate, 1, holidaySet, includeSat);
                }

                lotFabProcesses.put(lotIdx, procList);
                LocalDate fStart = procList.get(0).scheduledStartDate();
                LocalDate fEnd = procList.get(procList.size() - 1).scheduledEndDate();
                lotFabStartDates.put(lotIdx, fStart);
                lotFabEndDates.put(lotIdx, fEnd);
            }
        }

        // Build slips
        LocalDate overallStart = null;
        LocalDate overallEnd = null;

        for (int lotIdx = 0; lotIdx < lotCount; lotIdx++) {
            String lotNo = formatLotNo(request.orderNo(), lotIdx + 1);
            BigDecimal lotQty = lotQuantities.get(lotIdx);

            List<ScheduledProcessDto> combinedProcs = new ArrayList<>();
            List<ScheduledProcessDto> baseList = lotBaseProcesses.getOrDefault(lotIdx, List.of());
            List<ScheduledProcessDto> fabList = lotFabProcesses.getOrDefault(lotIdx, List.of());
            combinedProcs.addAll(baseList);
            combinedProcs.addAll(fabList);

            LocalDate lotBaseStart = !baseList.isEmpty()
                ? baseList.get(0).scheduledStartDate()
                : (!combinedProcs.isEmpty() ? combinedProcs.get(0).scheduledStartDate() : targetEndDate);
            LocalDate lotBaseEnd = !baseList.isEmpty()
                ? baseList.get(baseList.size() - 1).scheduledEndDate()
                : (!combinedProcs.isEmpty() ? combinedProcs.get(combinedProcs.size() - 1).scheduledEndDate() : targetEndDate);
            BigDecimal lotBaseDays = BigDecimal.valueOf(countWorkingDays(lotBaseStart, lotBaseEnd, holidaySet, includeSat));

            LocalDate fStart = hasFab && !fabList.isEmpty() ? lotFabStartDates.get(lotIdx) : null;
            LocalDate fEnd = hasFab && !fabList.isEmpty() ? lotFabEndDates.get(lotIdx) : null;
            BigDecimal fDays = (fStart != null && fEnd != null)
                ? BigDecimal.valueOf(countWorkingDays(fStart, fEnd, holidaySet, includeSat))
                : BigDecimal.ZERO;

            LocalDate slipOverallStart = lotBaseStart;
            LocalDate slipOverallEnd = (fEnd != null) ? fEnd : lotBaseEnd;

            if (overallStart == null || slipOverallStart.isBefore(overallStart)) overallStart = slipOverallStart;
            if (overallEnd == null || slipOverallEnd.isAfter(overallEnd)) overallEnd = slipOverallEnd;

            slips.set(lotIdx, new ScheduledSlipDto(
                lotNo,
                request.orderNo(),
                "ORIGINAL",
                lotQty,
                lotBaseStart,
                lotBaseEnd,
                lotBaseDays,
                fStart,
                fEnd,
                fDays,
                combinedProcs
            ));
        }

        ScheduleResult res = new ScheduleResult();
        res.mode = "BACKWARD";
        res.scheduledStartDate = overallStart != null ? overallStart : targetEndDate;
        res.scheduledEndDate = overallEnd != null ? overallEnd : targetEndDate;
        res.slips = slips;
        res.resultingLoads = tracker.toDailyLoadList();
        res.capacityWarnings = warnings;
        return res;
    }

    /**
     * Forward scheduling algorithm.
     */
    private ScheduleResult runForwardScheduling(
        ScheduleRequest request,
        List<BigDecimal> lotQuantities,
        List<RouteStepDto> baseSteps,
        List<RouteStepDto> fabSteps,
        LocalDate startFromDate,
        LocalDate targetEndDate,
        Set<LocalDate> holidaySet,
        boolean includeSat,
        BigDecimal maxHoursPerDay
    ) {
        CapacityTracker tracker = new CapacityTracker(request.existingLoads());
        List<CapacityWarningDto> warnings = new ArrayList<>();
        int lotCount = lotQuantities.size();

        boolean hasFab = Boolean.TRUE.equals(request.hasFabrication()) && !fabSteps.isEmpty();

        // 1. Forward schedule base item steps
        Map<Integer, List<ScheduledProcessDto>> lotBaseProcesses = new HashMap<>();
        Map<Integer, LocalDate> lotBaseStartDates = new HashMap<>();
        Map<Integer, LocalDate> lotBaseEndDates = new HashMap<>();

        int maxConcurrent = request.maxConcurrentLots() != null && request.maxConcurrentLots() > 0 ? request.maxConcurrentLots() : 1;

        for (int lotIdx = 0; lotIdx < lotCount; lotIdx++) {
            String lotNo = formatLotNo(request.orderNo(), lotIdx + 1);
            BigDecimal lotQty = lotQuantities.get(lotIdx);
            List<ScheduledProcessDto> procList = new ArrayList<>();

            int batchOffset = lotIdx / maxConcurrent;
            LocalDate currentStartDate = addWorkingDays(startFromDate, batchOffset, holidaySet, includeSat);

            for (int stepIdx = 0; stepIdx < baseSteps.size(); stepIdx++) {
                RouteStepDto step = baseSteps.get(stepIdx);
                BigDecimal stepHours = calculateStepHours(step, lotQty, maxHoursPerDay);
                int requiredDays = calculateStepWorkingDays(stepHours, maxHoursPerDay);

                LocalDate stepStartDate = tracker.findAvailableDateForward(
                    currentStartDate,
                    step,
                    lotQty,
                    request.stdItemWt(),
                    holidaySet,
                    includeSat,
                    warnings,
                    lotNo
                );

                LocalDate stepEndDate = addWorkingDays(stepStartDate, Math.max(0, requiredDays - 1), holidaySet, includeSat);

                ScheduledProcessDto procDto = new ScheduledProcessDto(
                    step.stepNo() != null ? step.stepNo() : (stepIdx + 1),
                    step.processId(),
                    step.processName(),
                    request.itemCode(),
                    false,
                    stepStartDate,
                    stepEndDate,
                    step.leadTime(),
                    stepHours,
                    lotQty,
                    stepIdx > 0 ? stepIdx : null,
                    stepIdx < baseSteps.size() - 1 ? List.of(stepIdx + 2) : List.of()
                );
                procList.add(procDto);

                currentStartDate = addWorkingDays(stepEndDate, 1, holidaySet, includeSat);
            }

            lotBaseProcesses.put(lotIdx, procList);
            if (!procList.isEmpty()) {
                LocalDate bStart = procList.get(0).scheduledStartDate();
                LocalDate bEnd = procList.get(procList.size() - 1).scheduledEndDate();
                lotBaseStartDates.put(lotIdx, bStart);
                lotBaseEndDates.put(lotIdx, bEnd);
            } else {
                lotBaseStartDates.put(lotIdx, startFromDate);
                lotBaseEndDates.put(lotIdx, startFromDate);
            }
        }

        // 2. Fabrication Synchronization Barrier
        Map<Integer, List<ScheduledProcessDto>> lotFabProcesses = new HashMap<>();
        Map<Integer, LocalDate> lotFabStartDates = new HashMap<>();
        Map<Integer, LocalDate> lotFabEndDates = new HashMap<>();

        if (hasFab) {
            LocalDate maxBaseEndDate = lotBaseEndDates.values().stream().max(LocalDate::compareTo).orElse(startFromDate);
            LocalDate fabCommonStartDate = addWorkingDays(maxBaseEndDate, 1, holidaySet, includeSat);

            for (int lotIdx = 0; lotIdx < lotCount; lotIdx++) {
                String lotNo = formatLotNo(request.orderNo(), lotIdx + 1);
                BigDecimal lotQty = lotQuantities.get(lotIdx);
                List<ScheduledProcessDto> procList = new ArrayList<>();

                LocalDate currentStartDate = fabCommonStartDate;

                for (int stepIdx = 0; stepIdx < fabSteps.size(); stepIdx++) {
                    RouteStepDto step = fabSteps.get(stepIdx);
                    BigDecimal stepHours = calculateStepHours(step, lotQty, maxHoursPerDay);
                    int requiredDays = calculateStepWorkingDays(stepHours, maxHoursPerDay);

                    LocalDate stepStartDate = tracker.findAvailableDateForward(
                        currentStartDate,
                        step,
                        lotQty,
                        request.stdItemWt(),
                        holidaySet,
                        includeSat,
                        warnings,
                        lotNo
                    );

                    LocalDate stepEndDate = addWorkingDays(stepStartDate, Math.max(0, requiredDays - 1), holidaySet, includeSat);

                    ScheduledProcessDto procDto = new ScheduledProcessDto(
                        step.stepNo() != null ? step.stepNo() : (baseSteps.size() + stepIdx + 1),
                        step.processId(),
                        step.processName(),
                        request.fabCode() != null ? request.fabCode() : request.itemCode(),
                        true,
                        stepStartDate,
                        stepEndDate,
                        step.leadTime(),
                        stepHours,
                        lotQty,
                        stepIdx > 0 ? (baseSteps.size() + stepIdx) : null,
                        stepIdx < fabSteps.size() - 1 ? List.of(baseSteps.size() + stepIdx + 2) : List.of()
                    );
                    procList.add(procDto);

                    currentStartDate = addWorkingDays(stepEndDate, 1, holidaySet, includeSat);
                }

                lotFabProcesses.put(lotIdx, procList);
                LocalDate fStart = procList.get(0).scheduledStartDate();
                LocalDate fEnd = procList.get(procList.size() - 1).scheduledEndDate();
                lotFabStartDates.put(lotIdx, fStart);
                lotFabEndDates.put(lotIdx, fEnd);
            }
        }

        // Build slips
        List<ScheduledSlipDto> slips = new ArrayList<>(lotCount);
        LocalDate overallStart = null;
        LocalDate overallEnd = null;

        for (int lotIdx = 0; lotIdx < lotCount; lotIdx++) {
            String lotNo = formatLotNo(request.orderNo(), lotIdx + 1);
            BigDecimal lotQty = lotQuantities.get(lotIdx);

            List<ScheduledProcessDto> combinedProcs = new ArrayList<>();
            List<ScheduledProcessDto> baseList = lotBaseProcesses.getOrDefault(lotIdx, List.of());
            List<ScheduledProcessDto> fabList = lotFabProcesses.getOrDefault(lotIdx, List.of());
            combinedProcs.addAll(baseList);
            combinedProcs.addAll(fabList);

            LocalDate lotBaseStart = !baseList.isEmpty()
                ? baseList.get(0).scheduledStartDate()
                : (!combinedProcs.isEmpty() ? combinedProcs.get(0).scheduledStartDate() : startFromDate);
            LocalDate lotBaseEnd = !baseList.isEmpty()
                ? baseList.get(baseList.size() - 1).scheduledEndDate()
                : (!combinedProcs.isEmpty() ? combinedProcs.get(combinedProcs.size() - 1).scheduledEndDate() : startFromDate);
            BigDecimal lotBaseDays = BigDecimal.valueOf(countWorkingDays(lotBaseStart, lotBaseEnd, holidaySet, includeSat));

            LocalDate fStart = hasFab && !fabList.isEmpty() ? lotFabStartDates.get(lotIdx) : null;
            LocalDate fEnd = hasFab && !fabList.isEmpty() ? lotFabEndDates.get(lotIdx) : null;
            BigDecimal fDays = (fStart != null && fEnd != null)
                ? BigDecimal.valueOf(countWorkingDays(fStart, fEnd, holidaySet, includeSat))
                : BigDecimal.ZERO;

            LocalDate slipOverallStart = lotBaseStart;
            LocalDate slipOverallEnd = (fEnd != null) ? fEnd : lotBaseEnd;

            if (overallStart == null || slipOverallStart.isBefore(overallStart)) overallStart = slipOverallStart;
            if (overallEnd == null || slipOverallEnd.isAfter(overallEnd)) overallEnd = slipOverallEnd;

            slips.add(new ScheduledSlipDto(
                lotNo,
                request.orderNo(),
                "ORIGINAL",
                lotQty,
                lotBaseStart,
                lotBaseEnd,
                lotBaseDays,
                fStart,
                fEnd,
                fDays,
                combinedProcs
            ));
        }

        ScheduleResult res = new ScheduleResult();
        res.mode = "FORWARD";
        res.scheduledStartDate = overallStart != null ? overallStart : startFromDate;
        res.scheduledEndDate = overallEnd != null ? overallEnd : startFromDate;
        res.slips = slips;
        res.resultingLoads = tracker.toDailyLoadList();
        res.capacityWarnings = warnings;
        return res;
    }

    private static String formatLotNo(String orderNo, int lotIndex) {
        String base = orderNo != null && !orderNo.isBlank() ? orderNo : "LOT";
        return String.format("%s-%02d", base, lotIndex);
    }

    private BigDecimal calculateStepHours(RouteStepDto step, BigDecimal lotQty, BigDecimal maxHoursPerDay) {
        BigDecimal leadTimeHours = convertToHours(step.leadTime(), step.leadTimeUnit(), maxHoursPerDay);
        BigDecimal prepTimeHours = convertToHours(step.prepTime(), step.prepTimeUnit(), maxHoursPerDay);
        BigDecimal cureTimeHours = convertToHours(step.cureTime(), step.cureTimeUnit(), maxHoursPerDay);

        BigDecimal stepHours = BigDecimal.ZERO;
        if ("Piece".equalsIgnoreCase(step.durationType())) {
            stepHours = leadTimeHours.multiply(lotQty).add(prepTimeHours).add(cureTimeHours);
        } else {
            stepHours = leadTimeHours.add(prepTimeHours).add(cureTimeHours);
        }

        return stepHours.setScale(2, RoundingMode.HALF_UP);
    }

    private int calculateStepWorkingDays(BigDecimal stepHours, BigDecimal maxHoursPerDay) {
        if (stepHours.compareTo(BigDecimal.ZERO) <= 0) {
            return 1;
        }
        return Math.max(1, stepHours.divide(maxHoursPerDay, 0, RoundingMode.CEILING).intValue());
    }

    private BigDecimal convertToHours(BigDecimal value, String unit, BigDecimal maxHoursPerDay) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if ("Minutes".equalsIgnoreCase(unit)) {
            return value.divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
        } else if ("Days".equalsIgnoreCase(unit)) {
            return value.multiply(maxHoursPerDay);
        }
        return value;
    }

    // --- Working Days Math ---

    public boolean isWorkingDay(LocalDate date, Set<LocalDate> holidays, boolean includeSaturday) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SUNDAY) return false;
        if (!includeSaturday && dow == DayOfWeek.SATURDAY) return false;
        return !holidays.contains(date);
    }

    public LocalDate ensureWorkingDayForward(LocalDate date, Set<LocalDate> holidays, boolean includeSaturday) {
        LocalDate d = date;
        while (!isWorkingDay(d, holidays, includeSaturday)) {
            d = d.plusDays(1);
        }
        return d;
    }

    public LocalDate ensureWorkingDayBackward(LocalDate date, Set<LocalDate> holidays, boolean includeSaturday) {
        LocalDate d = date;
        while (!isWorkingDay(d, holidays, includeSaturday)) {
            d = d.minusDays(1);
        }
        return d;
    }

    public LocalDate addWorkingDays(LocalDate start, int days, Set<LocalDate> holidays, boolean includeSaturday) {
        LocalDate current = ensureWorkingDayForward(start, holidays, includeSaturday);
        int added = 0;
        while (added < days) {
            current = current.plusDays(1);
            if (isWorkingDay(current, holidays, includeSaturday)) {
                added++;
            }
        }
        return current;
    }

    public LocalDate subtractWorkingDays(LocalDate start, int days, Set<LocalDate> holidays, boolean includeSaturday) {
        LocalDate current = ensureWorkingDayBackward(start, holidays, includeSaturday);
        int subtracted = 0;
        while (subtracted < days) {
            current = current.minusDays(1);
            if (isWorkingDay(current, holidays, includeSaturday)) {
                subtracted++;
            }
        }
        return current;
    }

    public int countWorkingDays(LocalDate start, LocalDate end, Set<LocalDate> holidays, boolean includeSaturday) {
        if (start.isAfter(end)) return 0;
        int count = 0;
        LocalDate current = start;
        while (!current.isAfter(end)) {
            if (isWorkingDay(current, holidays, includeSaturday)) {
                count++;
            }
            current = current.plusDays(1);
        }
        return count;
    }

    // --- Daily Capacity Tracker ---

    private static class CapacityTracker {
        private static final class LoadBucket {
            BigDecimal pcs = BigDecimal.ZERO;
            BigDecimal weight = BigDecimal.ZERO;
            String processName = "";
        }

        private final Map<String, LoadBucket> buckets = new HashMap<>();

        public CapacityTracker(List<DailyLoadDto> existingLoads) {
            if (existingLoads != null) {
                for (DailyLoadDto load : existingLoads) {
                    if (load.date() != null && load.processId() != null) {
                        String key = keyOf(load.date(), load.processId());
                        LoadBucket b = buckets.computeIfAbsent(key, k -> new LoadBucket());
                        b.pcs = b.pcs.add(load.scheduledPcs());
                        b.weight = b.weight.add(load.scheduledWeight());
                        b.processName = load.processName() != null ? load.processName() : "";
                    }
                }
            }
        }

        private String keyOf(LocalDate date, Integer processId) {
            return date.toString() + "_" + (processId != null ? processId : 0);
        }

        public LocalDate findAvailableDateBackward(
            LocalDate targetDate,
            RouteStepDto step,
            BigDecimal lotQty,
            BigDecimal stdItemWt,
            Set<LocalDate> holidays,
            boolean includeSat,
            List<CapacityWarningDto> warnings,
            String lotNo
        ) {
            LocalDate current = targetDate;
            int attempts = 0;
            BigDecimal lotWeight = lotQty.multiply(stdItemWt != null ? stdItemWt : BigDecimal.ZERO);

            while (attempts < 180) {
                while (!isWorkingDay(current, holidays, includeSat)) {
                    current = current.minusDays(1);
                }

                if (step.processId() == null || (
                    (step.maxPcsPerDay() == null || step.maxPcsPerDay().compareTo(BigDecimal.ZERO) <= 0) &&
                    (step.maxWeightPerDay() == null || step.maxWeightPerDay().compareTo(BigDecimal.ZERO) <= 0)
                )) {
                    // No capacity limit
                    recordLoad(current, step, lotQty, lotWeight);
                    return current;
                }

                String key = keyOf(current, step.processId());
                LoadBucket b = buckets.getOrDefault(key, new LoadBucket());

                boolean pcsExceeded = step.maxPcsPerDay() != null &&
                    step.maxPcsPerDay().compareTo(BigDecimal.ZERO) > 0 &&
                    b.pcs.add(lotQty).compareTo(step.maxPcsPerDay()) > 0;

                boolean weightExceeded = step.maxWeightPerDay() != null &&
                    step.maxWeightPerDay().compareTo(BigDecimal.ZERO) > 0 &&
                    b.weight.add(lotWeight).compareTo(step.maxWeightPerDay()) > 0;

                if (!pcsExceeded && !weightExceeded) {
                    recordLoad(current, step, lotQty, lotWeight);
                    return current;
                }

                // Exceeded -> Record warning and shift backward 1 day
                LocalDate nextTry = current.minusDays(1);
                warnings.add(new CapacityWarningDto(
                    current,
                    nextTry,
                    step.processId(),
                    step.processName(),
                    lotNo,
                    String.format("Daily capacity exceeded on %s for process [%s]. Shifted backward to %s.",
                        current, step.processName(), nextTry),
                    b.pcs.add(lotQty),
                    step.maxPcsPerDay(),
                    b.weight.add(lotWeight),
                    step.maxWeightPerDay()
                ));

                current = nextTry;
                attempts++;
            }

            // Fallback
            recordLoad(current, step, lotQty, lotWeight);
            return current;
        }

        public LocalDate findAvailableDateForward(
            LocalDate targetDate,
            RouteStepDto step,
            BigDecimal lotQty,
            BigDecimal stdItemWt,
            Set<LocalDate> holidays,
            boolean includeSat,
            List<CapacityWarningDto> warnings,
            String lotNo
        ) {
            LocalDate current = targetDate;
            int attempts = 0;
            BigDecimal lotWeight = lotQty.multiply(stdItemWt != null ? stdItemWt : BigDecimal.ZERO);

            while (attempts < 180) {
                while (!isWorkingDay(current, holidays, includeSat)) {
                    current = current.plusDays(1);
                }

                if (step.processId() == null || (
                    (step.maxPcsPerDay() == null || step.maxPcsPerDay().compareTo(BigDecimal.ZERO) <= 0) &&
                    (step.maxWeightPerDay() == null || step.maxWeightPerDay().compareTo(BigDecimal.ZERO) <= 0)
                )) {
                    recordLoad(current, step, lotQty, lotWeight);
                    return current;
                }

                String key = keyOf(current, step.processId());
                LoadBucket b = buckets.getOrDefault(key, new LoadBucket());

                boolean pcsExceeded = step.maxPcsPerDay() != null &&
                    step.maxPcsPerDay().compareTo(BigDecimal.ZERO) > 0 &&
                    b.pcs.add(lotQty).compareTo(step.maxPcsPerDay()) > 0;

                boolean weightExceeded = step.maxWeightPerDay() != null &&
                    step.maxWeightPerDay().compareTo(BigDecimal.ZERO) > 0 &&
                    b.weight.add(lotWeight).compareTo(step.maxWeightPerDay()) > 0;

                if (!pcsExceeded && !weightExceeded) {
                    recordLoad(current, step, lotQty, lotWeight);
                    return current;
                }

                LocalDate nextTry = current.plusDays(1);
                warnings.add(new CapacityWarningDto(
                    current,
                    nextTry,
                    step.processId(),
                    step.processName(),
                    lotNo,
                    String.format("Daily capacity exceeded on %s for process [%s]. Shifted forward to %s.",
                        current, step.processName(), nextTry),
                    b.pcs.add(lotQty),
                    step.maxPcsPerDay(),
                    b.weight.add(lotWeight),
                    step.maxWeightPerDay()
                ));

                current = nextTry;
                attempts++;
            }

            recordLoad(current, step, lotQty, lotWeight);
            return current;
        }

        private void recordLoad(LocalDate date, RouteStepDto step, BigDecimal lotQty, BigDecimal lotWeight) {
            if (step.processId() != null) {
                String key = keyOf(date, step.processId());
                LoadBucket b = buckets.computeIfAbsent(key, k -> new LoadBucket());
                b.pcs = b.pcs.add(lotQty);
                b.weight = b.weight.add(lotWeight);
                b.processName = step.processName() != null ? step.processName() : "";
            }
        }

        public List<DailyLoadDto> toDailyLoadList() {
            List<DailyLoadDto> list = new ArrayList<>();
            for (Map.Entry<String, LoadBucket> entry : buckets.entrySet()) {
                String[] parts = entry.getKey().split("_");
                if (parts.length == 2) {
                    LocalDate date = LocalDate.parse(parts[0]);
                    Integer procId = Integer.parseInt(parts[1]);
                    LoadBucket b = entry.getValue();
                    list.add(new DailyLoadDto(date, procId, b.processName, b.pcs, b.weight));
                }
            }
            return list;
        }

        private boolean isWorkingDay(LocalDate date, Set<LocalDate> holidays, boolean includeSaturday) {
            DayOfWeek dow = date.getDayOfWeek();
            if (dow == DayOfWeek.SUNDAY) return false;
            if (!includeSaturday && dow == DayOfWeek.SATURDAY) return false;
            return !holidays.contains(date);
        }
    }
}
