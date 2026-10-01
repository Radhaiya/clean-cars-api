package com.example.cleancarsapi.service;

import java.time.LocalDate;
import java.util.Set;

/**
 * Runtime AMC slot math (docs/FEATURE-AMC.md) — nothing here is stored. Slot {@code i} (0-based)
 * covers {@code [start + i·interval months, start + (i+1)·interval months)}, each boundary computed
 * from the original start date (so end-of-month clamping never drifts). The AMC ends at
 * {@code start + tenure}. Only the <em>current</em> slot can be redeemed; a past slot nobody
 * redeemed is lapsed.
 */
public final class AmcSlots {

    private AmcSlots() {
    }

    public enum Status { UPCOMING, ACTIVE, EXPIRED }

    /**
     * @param currentSlot 0-based index of today's slot; null when upcoming or expired
     * @param used        slots with a (non-cancelled) redemption
     * @param lapsed      past slots never redeemed
     * @param remaining   current + future slots not yet used
     * @param availableNow 1 when today's slot is unused, else 0
     */
    public record Counts(Status status, LocalDate endDate, int total, Integer currentSlot,
                         int used, int lapsed, int remaining, int availableNow) {
    }

    public static LocalDate slotStart(LocalDate start, int intervalMonths, int slot) {
        return start.plusMonths((long) slot * intervalMonths);
    }

    public static Counts compute(LocalDate start, int tenureMonths, int intervalMonths, LocalDate today,
                                 Set<Integer> usedSlots) {
        int total = tenureMonths / intervalMonths;
        LocalDate end = start.plusMonths(tenureMonths);

        Status status;
        Integer current = null;
        int firstNotPast; // slots below this index are over
        if (today.isBefore(start)) {
            status = Status.UPCOMING;
            firstNotPast = 0;
        } else if (!today.isBefore(end)) {
            status = Status.EXPIRED;
            firstNotPast = total;
        } else {
            status = Status.ACTIVE;
            int slot = 0;
            while (slot + 1 < total && !today.isBefore(slotStart(start, intervalMonths, slot + 1))) {
                slot++;
            }
            current = slot;
            firstNotPast = slot;
        }

        int used = 0;
        int lapsed = 0;
        int remaining = 0;
        for (int i = 0; i < total; i++) {
            boolean isUsed = usedSlots.contains(i);
            if (isUsed) {
                used++;
            } else if (i < firstNotPast) {
                lapsed++;
            } else {
                remaining++;
            }
        }
        int availableNow = current != null && !usedSlots.contains(current) ? 1 : 0;
        return new Counts(status, end, total, current, used, lapsed, remaining, availableNow);
    }
}
