package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Business-time calculations for the default Monday-Friday 08:00-17:00 SLA calendar. */
public final class BusinessHours {
    private static final LocalTime START = LocalTime.of(8, 0);
    private static final LocalTime END = LocalTime.of(17, 0);

    private BusinessHours() {}

    public static LocalDateTime addWorkingHours(LocalDateTime start, double hours) {
        if (hours < 0) throw new IllegalArgumentException("Working hours cannot be negative.");
        LocalDateTime cursor = normalize(start);
        double remaining = hours;
        while (remaining > 0) {
            double available = java.time.Duration.between(cursor, cursor.toLocalDate().atTime(END)).toMinutes() / 60.0;
            double consumed = Math.min(available, remaining);
            cursor = cursor.plusMinutes(Math.round(consumed * 60));
            remaining -= consumed;
            if (remaining > 0) cursor = nextBusinessStart(cursor);
        }
        return cursor;
    }

    public static double workingHoursBetween(LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) return 0;
        LocalDateTime cursor = normalize(start);
        double total = 0;
        while (cursor.isBefore(end)) {
            LocalDateTime dayEnd = cursor.toLocalDate().atTime(END);
            LocalDateTime segmentEnd = end.isBefore(dayEnd) ? end : dayEnd;
            if (segmentEnd.isAfter(cursor)) {
                total += java.time.Duration.between(cursor, segmentEnd).toMinutes() / 60.0;
            }
            if (segmentEnd.equals(end)) break;
            cursor = nextBusinessStart(cursor);
        }
        return total;
    }

    private static LocalDateTime normalize(LocalDateTime value) {
        LocalDateTime cursor = value;
        while (cursor.getDayOfWeek() == DayOfWeek.SATURDAY
                || cursor.getDayOfWeek() == DayOfWeek.SUNDAY
                || !cursor.toLocalTime().isBefore(END)) {
            cursor = cursor.toLocalDate().plusDays(1).atTime(START);
        }
        if (cursor.toLocalTime().isBefore(START)) cursor = cursor.toLocalDate().atTime(START);
        return cursor;
    }

    private static LocalDateTime nextBusinessStart(LocalDateTime value) {
        LocalDateTime next = value.toLocalDate().plusDays(1).atTime(START);
        while (next.getDayOfWeek() == DayOfWeek.SATURDAY || next.getDayOfWeek() == DayOfWeek.SUNDAY) {
            next = next.plusDays(1);
        }
        return next;
    }
}
