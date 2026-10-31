package com.cancerweb.backend.dto;

import java.util.List;

/**
 * One day of a Breast Surgeon's calendar: every time slot the surgeon offers that day, and whether it can still
 * be booked (not taken by another appointment, and not already past).
 *
 * @param date      yyyy-MM-dd
 * @param dayOfWeek MONDAY, TUESDAY, ...
 */
public record SlotDay(String date, String dayOfWeek, List<Slot> slots) {

    public record Slot(String time, boolean available) {
    }
}
