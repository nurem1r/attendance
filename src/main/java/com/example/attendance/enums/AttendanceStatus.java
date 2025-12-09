package com.example.attendance.enums;

/**
 * Attendance statuses.
 *
 * EXCUSED — used when teacher did not mark a student and system fills the slot to avoid nulls.
 * EXCUSED does NOT consume (decrement) remainingLessons.
 */
public enum AttendanceStatus {
    PRESENT,
    LATE,
    ABSENT,
    EXCUSED
}