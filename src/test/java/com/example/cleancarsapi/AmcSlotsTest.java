package com.example.cleancarsapi;

import com.example.cleancarsapi.service.AmcSlots;
import com.example.cleancarsapi.service.AmcSlots.Counts;
import com.example.cleancarsapi.service.AmcSlots.Status;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Pure slot / lapse math for AMCs — strict slot usage, anniversary periods, no carry-forward. */
class AmcSlotsTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 15);

    private static Counts at(String today, Set<Integer> used) {
        return AmcSlots.compute(START, 12, 1, LocalDate.parse(today), used);
    }

    @Test
    void firstDayIsTheFirstSlotAndEverythingIsRemaining() {
        Counts c = at("2026-01-15", Set.of());
        assertEquals(Status.ACTIVE, c.status());
        assertEquals(0, c.currentSlot());
        assertEquals(12, c.total());
        assertEquals(0, c.used());
        assertEquals(0, c.lapsed());
        assertEquals(12, c.remaining());
        assertEquals(1, c.availableNow());
    }

    @Test
    void slotsRollOnTheAnniversaryDayNotTheCalendarMonth() {
        assertEquals(0, at("2026-02-14", Set.of()).currentSlot());
        assertEquals(1, at("2026-02-15", Set.of()).currentSlot());
    }

    @Test
    void skippedMonthsLapseAndAreNotCarriedForward() {
        // slot 0 used, slots 1 and 2 skipped, now in slot 3 (20 April), unused
        Counts c = at("2026-04-20", Set.of(0));
        assertEquals(3, c.currentSlot());
        assertEquals(1, c.used());
        assertEquals(2, c.lapsed());
        assertEquals(9, c.remaining()); // slots 3..11
        assertEquals(1, c.availableNow());
    }

    @Test
    void usingTheCurrentSlotLeavesNothingAvailableUntilTheNextOne() {
        Counts c = at("2026-04-20", Set.of(0, 3));
        assertEquals(2, c.used());
        assertEquals(2, c.lapsed());
        assertEquals(8, c.remaining());
        assertEquals(0, c.availableNow());
    }

    @Test
    void afterTheTenureEverythingUnusedIsLapsedAndNothingRemains() {
        Counts c = at("2027-01-15", Set.of(0, 5));
        assertEquals(Status.EXPIRED, c.status());
        assertNull(c.currentSlot());
        assertEquals(LocalDate.of(2027, 1, 15), c.endDate());
        assertEquals(2, c.used());
        assertEquals(10, c.lapsed());
        assertEquals(0, c.remaining());
        assertEquals(0, c.availableNow());
    }

    @Test
    void lastDayBeforeTheEndIsStillTheLastSlot() {
        Counts c = at("2027-01-14", Set.of());
        assertEquals(Status.ACTIVE, c.status());
        assertEquals(11, c.currentSlot());
    }

    @Test
    void anAmcThatHasNotStartedYetIsUpcomingWithNothingAvailable() {
        Counts c = at("2026-01-10", Set.of());
        assertEquals(Status.UPCOMING, c.status());
        assertNull(c.currentSlot());
        assertEquals(12, c.remaining());
        assertEquals(0, c.lapsed());
        assertEquals(0, c.availableNow());
    }

    @Test
    void yearlyFrequencyOverTwoYearsHasTwoSlots() {
        Counts c = AmcSlots.compute(START, 24, 12, LocalDate.of(2027, 6, 1), Set.of(0));
        assertEquals(2, c.total());
        assertEquals(1, c.currentSlot());
        assertEquals(0, c.lapsed());
        assertEquals(1, c.remaining());
    }

    @Test
    void endOfMonthStartsClampWithoutDrifting() {
        LocalDate jan31 = LocalDate.of(2026, 1, 31);
        // slot 1 starts 28 Feb (clamped), slot 2 starts 31 Mar (not 28 Mar)
        assertEquals(LocalDate.of(2026, 2, 28), AmcSlots.slotStart(jan31, 1, 1));
        assertEquals(LocalDate.of(2026, 3, 31), AmcSlots.slotStart(jan31, 1, 2));
        assertEquals(1, AmcSlots.compute(jan31, 12, 1, LocalDate.of(2026, 3, 30), Set.of()).currentSlot());
    }
}
