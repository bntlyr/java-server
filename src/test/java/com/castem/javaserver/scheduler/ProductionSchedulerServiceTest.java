package com.castem.javaserver.scheduler;

import com.castem.javaserver.scheduler.dto.*;
import com.castem.javaserver.scheduler.service.ProductionSchedulerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProductionSchedulerServiceTest {

    private ProductionSchedulerService schedulerService;

    @BeforeEach
    void setUp() {
        schedulerService = new ProductionSchedulerService();
    }

    @Test
    @DisplayName("TEST 1: Concurrent Lots - User provides max 2 lots starting on the same date")
    void testConcurrentLotsStartingSameDate() {
        LocalDate etd = LocalDate.of(2026, 12, 20);

        List<RouteStepDto> itemRoutes = List.of(
            new RouteStepDto(1, 101, "Wax Injection", BigDecimal.valueOf(120), "Minutes", null, null, null, null, "Batch", false, BigDecimal.valueOf(5000), BigDecimal.valueOf(5000), null, null),
            new RouteStepDto(2, 102, "Casting", BigDecimal.valueOf(180), "Minutes", null, null, null, null, "Batch", false, BigDecimal.valueOf(5000), BigDecimal.valueOf(5000), null, null),
            new RouteStepDto(3, 103, "Packing", BigDecimal.valueOf(60), "Minutes", null, null, null, null, "Batch", true, BigDecimal.valueOf(5000), BigDecimal.valueOf(5000), null, null)
        );

        // 2000 pcs with autoSplit = 500 -> 4 lots (Lot 1, 2, 3, 4)
        // maxConcurrentLots = 2 -> Lots 1 & 2 start on Day 1, Lots 3 & 4 start on Day 2
        ScheduleRequest request = new ScheduleRequest(
            "SO-CONCURRENT-001",
            10,
            "10-11111-000",
            BigDecimal.valueOf(2000),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.valueOf(500),
            BigDecimal.valueOf(0.2),
            BigDecimal.valueOf(0.2),
            etd,
            null,
            2, // maxConcurrentLots = 2
            true,
            BigDecimal.valueOf(14.0),
            List.of(),
            List.of(),
            itemRoutes,
            false,
            null,
            List.of()
        );

        ScheduleResponse res = schedulerService.schedule(request);

        System.out.println("================================================================================");
        System.out.println("       TEST 1: CONCURRENT LOTS SCHEDULING RESULT (maxConcurrentLots = 2)        ");
        System.out.println("================================================================================");
        System.out.println("Order No            : " + res.orderNo());
        System.out.println("Lot Count           : " + res.lotCount() + " lots");
        for (ScheduledSlipDto slip : res.slips()) {
            System.out.printf("  [SLIP] %s | Qty: %s pcs | Start Date: %s -> End Date: %s\n",
                slip.lotNo(), slip.totalQty(), slip.startDate(), slip.endDate());
        }
        System.out.println("================================================================================");

        assertEquals(4, res.slips().size());
        ScheduledSlipDto lot1 = res.slips().get(0);
        ScheduledSlipDto lot2 = res.slips().get(1);
        ScheduledSlipDto lot3 = res.slips().get(2);
        ScheduledSlipDto lot4 = res.slips().get(3);

        // In backward scheduling with 2 per batch:
        // Lots 1 & 2 share the same start date
        assertEquals(lot1.startDate(), lot2.startDate());
        // Lots 3 & 4 share the same start date (1 working day offset)
        assertEquals(lot3.startDate(), lot4.startDate());
    }

    @Test
    @DisplayName("TEST 2: Start Date Override - User provides explicit start date (Forward Scheduling)")
    void testUserProvidedStartDateOverride() {
        LocalDate userChosenStartDate = LocalDate.of(2026, 11, 10); // Tuesday
        LocalDate requestedEtd = LocalDate.of(2026, 12, 25);

        List<RouteStepDto> itemRoutes = List.of(
            new RouteStepDto(1, 101, "Wax Injection", BigDecimal.valueOf(120), "Minutes", null, null, null, null, "Batch", false, BigDecimal.valueOf(2000), null, null, null),
            new RouteStepDto(2, 102, "Casting", BigDecimal.valueOf(180), "Minutes", null, null, null, null, "Batch", false, BigDecimal.valueOf(2000), null, null, null),
            new RouteStepDto(3, 103, "Packing", BigDecimal.valueOf(60), "Minutes", null, null, null, null, "Batch", true, BigDecimal.valueOf(2000), null, null, null)
        );

        ScheduleRequest request = new ScheduleRequest(
            "SO-START-DATE-OVERRIDE",
            20,
            "10-22222-000",
            BigDecimal.valueOf(1000),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.valueOf(500), // 2 lots
            BigDecimal.valueOf(0.3),
            BigDecimal.valueOf(0.3),
            requestedEtd,
            userChosenStartDate, // User-provided Start Date!
            1,
            true,
            BigDecimal.valueOf(14.0),
            List.of(),
            List.of(),
            itemRoutes,
            false,
            null,
            List.of()
        );

        ScheduleResponse res = schedulerService.schedule(request);

        System.out.println("================================================================================");
        System.out.println("       TEST 2: USER PROVIDED START DATE OVERRIDE RESULT                         ");
        System.out.println("================================================================================");
        System.out.println("Order No            : " + res.orderNo());
        System.out.println("Scheduling Mode     : " + res.schedulingMode());
        System.out.println("User Chosen Start   : " + userChosenStartDate);
        System.out.println("Scheduled Start Date: " + res.scheduledStartDate());
        System.out.println("Scheduled End Date  : " + res.scheduledEndDate());
        for (ScheduledSlipDto slip : res.slips()) {
            System.out.printf("  [SLIP] %s | Start: %s -> End: %s\n",
                slip.lotNo(), slip.startDate(), slip.endDate());
            for (ScheduledProcessDto p : slip.processes()) {
                System.out.printf("         -> Step %d [%s]: %s to %s\n",
                    p.stepNo(), p.processName(), p.scheduledStartDate(), p.scheduledEndDate());
            }
        }
        System.out.println("================================================================================");

        assertEquals("FORWARD", res.schedulingMode());
        assertEquals(userChosenStartDate, res.scheduledStartDate());
        assertEquals(userChosenStartDate, res.slips().get(0).startDate());
    }

    @Test
    @DisplayName("TEST 3: Capacity Checking - Process limit of 400 pcs/day shifts overflowing lot to next day")
    void testDetailedCapacityCheckingAndShifting() {
        LocalDate userStartDate = LocalDate.of(2026, 11, 10);

        // Wax Injection process max capacity is 400 pcs per day
        List<RouteStepDto> itemRoutes = List.of(
            new RouteStepDto(1, 101, "Wax Injection", BigDecimal.valueOf(60), "Minutes", null, null, null, null, "Batch", false, BigDecimal.valueOf(400), null, null, null),
            new RouteStepDto(2, 102, "Casting", BigDecimal.valueOf(120), "Minutes", null, null, null, null, "Batch", false, BigDecimal.valueOf(2000), null, null, null)
        );

        // 2 lots of 300 pcs each:
        // Total on Wax Injection = 300 + 300 = 600 pcs > 400 pcs capacity!
        // Lot 1 (300 pcs) takes Day 1 (2026-11-10) -> Remaining cap = 100 pcs.
        // Lot 2 (300 pcs) does NOT fit on 2026-11-10 -> Capacity engine automatically shifts Lot 2 to Day 2 (2026-11-11)!
        ScheduleRequest request = new ScheduleRequest(
            "SO-CAPACITY-CHECK",
            30,
            "10-33333-000",
            BigDecimal.valueOf(600),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.valueOf(300), // 2 lots of 300 pcs
            BigDecimal.valueOf(0.2),
            BigDecimal.valueOf(0.2),
            LocalDate.of(2026, 12, 20),
            userStartDate,
            2, // Both lots allowed to start same day IF capacity permits
            true,
            BigDecimal.valueOf(14.0),
            List.of(),
            List.of(),
            itemRoutes,
            false,
            null,
            List.of()
        );

        ScheduleResponse res = schedulerService.schedule(request);

        System.out.println("================================================================================");
        System.out.println("       TEST 3: CAPACITY CHECKING & SHIFTING DEMONSTRATION                       ");
        System.out.println("================================================================================");
        System.out.println("Order No            : " + res.orderNo());
        System.out.println("Process Capacity    : Wax Injection max 400 pcs/day");
        System.out.println("Requested Load      : Lot 1 (300 pcs) + Lot 2 (300 pcs) = 600 pcs");
        System.out.println("Capacity Warnings Logged: " + res.capacityWarnings().size());
        for (CapacityWarningDto w : res.capacityWarnings()) {
            System.out.printf("  [WARNING] Process [%s] | Lot: %s | Requested: %s pcs on %s | Exceeded Cap: %s | Shifted To: %s\n",
                w.processName(), w.lotNo(), w.requestedPcs(), w.requestedDate(), w.maxPcsPerDay(), w.adjustedDate());
        }
        System.out.println("--------------------------------------------------------------------------------");
        for (ScheduledSlipDto slip : res.slips()) {
            System.out.printf("  [SLIP] %s | Total Qty: %s pcs | Start Date: %s -> End Date: %s\n",
                slip.lotNo(), slip.totalQty(), slip.startDate(), slip.endDate());
            for (ScheduledProcessDto p : slip.processes()) {
                System.out.printf("         -> Step %d [%s]: %s to %s\n",
                    p.stepNo(), p.processName(), p.scheduledStartDate(), p.scheduledEndDate());
            }
        }
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("Resulting Committed Daily Loads:");
        for (DailyLoadDto load : res.resultingLoads()) {
            System.out.printf("  -> Date: %s | Process: %s (ID %d) | Total Scheduled: %s pcs\n",
                load.date(), load.processName(), load.processId(), load.scheduledPcs());
        }
        System.out.println("================================================================================");

        // Assertions
        assertFalse(res.capacityWarnings().isEmpty());
        ScheduledSlipDto lot1 = res.slips().get(0);
        ScheduledSlipDto lot2 = res.slips().get(1);

        // Lot 1 Wax is on 2026-11-10
        assertEquals(userStartDate, lot1.processes().get(0).scheduledStartDate());
        // Lot 2 Wax was shifted forward to 2026-11-11 because capacity was exceeded!
        assertEquals(userStartDate.plusDays(1), lot2.processes().get(0).scheduledStartDate());
    }

    @Test
    @DisplayName("TEST 4: Fabrication Separation - Slip start/end reflects base item, while fabStart/fabEnd reflects fabrication")
    void testFabricationSeparationAndDates() {
        LocalDate userStartDate = LocalDate.of(2026, 10, 21); // Wednesday

        List<RouteStepDto> itemRoutes = List.of(
            new RouteStepDto(1, 101, "Wax Injection", BigDecimal.valueOf(60), "Minutes", null, null, null, null, "Batch", false, BigDecimal.valueOf(5000), null, null, null),
            new RouteStepDto(2, 102, "Casting", BigDecimal.valueOf(60), "Minutes", null, null, null, null, "Batch", false, BigDecimal.valueOf(5000), null, null, null)
        );

        List<RouteStepDto> fabRoutes = List.of(
            new RouteStepDto(3, 201, "Machining", BigDecimal.valueOf(60), "Minutes", null, null, null, null, "Batch", false, BigDecimal.valueOf(5000), null, null, null)
        );

        // 3 lots of 20 pcs each with maxConcurrentLots = 1
        ScheduleRequest request = new ScheduleRequest(
            "SO-FAB-TEST-001",
            40,
            "10-44444-100",
            BigDecimal.valueOf(60),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.valueOf(20), // 3 lots
            BigDecimal.valueOf(0.2),
            BigDecimal.valueOf(0.2),
            LocalDate.of(2026, 12, 20),
            userStartDate,
            1, // 1 lot per day
            true, // includeSaturday = true
            BigDecimal.valueOf(14.0),
            List.of(),
            List.of(),
            itemRoutes,
            true, // hasFabrication
            "100",
            fabRoutes
        );

        ScheduleResponse res = schedulerService.schedule(request);

        assertEquals(3, res.slips().size());

        ScheduledSlipDto lot1 = res.slips().get(0);
        ScheduledSlipDto lot2 = res.slips().get(1);
        ScheduledSlipDto lot3 = res.slips().get(2);

        // Base item route has 2 steps:
        // Lot 1: Oct 21 -> Oct 22
        // Lot 2: Oct 22 -> Oct 23
        // Lot 3: Oct 23 -> Oct 24
        assertEquals(LocalDate.of(2026, 10, 21), lot1.startDate());
        assertEquals(LocalDate.of(2026, 10, 22), lot1.endDate());
        assertEquals(BigDecimal.valueOf(2), lot1.totalDays());

        assertEquals(LocalDate.of(2026, 10, 22), lot2.startDate());
        assertEquals(LocalDate.of(2026, 10, 23), lot2.endDate());
        assertEquals(BigDecimal.valueOf(2), lot2.totalDays());

        assertEquals(LocalDate.of(2026, 10, 23), lot3.startDate());
        assertEquals(LocalDate.of(2026, 10, 24), lot3.endDate());
        assertEquals(BigDecimal.valueOf(2), lot3.totalDays());

        // Max base end date is Oct 24 (Saturday). Sunday Oct 25 is skipped. Next working day is Monday Oct 26.
        // Fabrication starts Oct 26 for all lots
        assertEquals(LocalDate.of(2026, 10, 26), lot1.fabStartDate());
        assertEquals(LocalDate.of(2026, 10, 26), lot1.fabEndDate());
        assertEquals(BigDecimal.valueOf(1), lot1.fabTotalDays());

        assertEquals(LocalDate.of(2026, 10, 26), lot2.fabStartDate());
        assertEquals(LocalDate.of(2026, 10, 26), lot2.fabEndDate());

        assertEquals(LocalDate.of(2026, 10, 26), lot3.fabStartDate());
        assertEquals(LocalDate.of(2026, 10, 26), lot3.fabEndDate());

        // Overall order schedule ends on Oct 26 (end of fabrication)
        assertEquals(LocalDate.of(2026, 10, 26), res.scheduledEndDate());
    }
}
