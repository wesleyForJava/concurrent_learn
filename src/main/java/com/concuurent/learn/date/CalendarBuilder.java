package com.concuurent.learn.date;

import static com.concuurent.learn.date.Calendar.DAY_OF_WEEK;
import static com.concuurent.learn.date.Calendar.FIELD_COUNT;
import static com.concuurent.learn.date.Calendar.SUNDAY;
import static com.concuurent.learn.date.Calendar.WEEK_OF_YEAR;
import static com.concuurent.learn.date.Calendar.YEAR;

import com.concuurent.learn.date.Calendar;

class CalendarBuilder {
    /*
     * Pseudo time stamp constants used in java.util.Calendar
     */
    private static final int UNSET = 0;
    private static final int COMPUTED = 1;
    private static final int MINIMUM_USER_STAMP = 2;

    private static final int MAX_FIELD = FIELD_COUNT + 1;

    public static final int WEEK_YEAR = FIELD_COUNT;
    public static final int ISO_DAY_OF_WEEK = 1000; // pseudo field index

    // stamp[] (lower half) and field[] (upper half) combined
    private final int[] field;
    private int nextStamp;
    private int maxFieldIndex;

    CalendarBuilder() {
        field = new int[MAX_FIELD * 2];
        nextStamp = MINIMUM_USER_STAMP;
        maxFieldIndex = -1;
    }

    CalendarBuilder set(int index, int value) {
        if (index == ISO_DAY_OF_WEEK) {
            index = DAY_OF_WEEK;
            value = toCalendarDayOfWeek(value);
        }
        field[index] = nextStamp++;
        field[MAX_FIELD + index] = value;
        if (index > maxFieldIndex && index < FIELD_COUNT) {
            maxFieldIndex = index;
        }
        return this;
    }

    CalendarBuilder addYear(int value) {
        field[MAX_FIELD + YEAR] += value;
        field[MAX_FIELD + WEEK_YEAR] += value;
        return this;
    }

    boolean isSet(int index) {
        if (index == ISO_DAY_OF_WEEK) {
            index = DAY_OF_WEEK;
        }
        return field[index] > UNSET;
    }

    CalendarBuilder clear(int index) {
        if (index == ISO_DAY_OF_WEEK) {
            index = DAY_OF_WEEK;
        }
        field[index] = UNSET;
        field[MAX_FIELD + index] = 0;
        return this;
    }

    /**
     * 代码（4）使用calb中解析好的日期数据设置cal对象。
     * 代码（5）返回设置好的cal对象。
     * 从以上代码可以看出，代码(3）、代码（4）和代码（5）并不是原子性操作。
     * 当多个线程调用parse方法时，比如线程A执行了代码（3）和代码（4），
     * 也就是设置好了cal对象，但是在执行代码（5）之前，
     * 线程B执行了代码(3），清空了cal对象。
     * 由于多个线程使用的是一个cal对象，所以线程A执行代码（4）返回的可能就是被线程B清空的对象，
     * 当然也有可能线程B执行了代码（的，设置被线程A修改的cal象，从而导致程序出现错误。
     * 那么怎么解决呢？ 详见TestSimpleDateFormat1
     * @param cal
     * @return
     */
    Calendar establish(Calendar cal) {
        boolean weekDate = isSet(WEEK_YEAR)
                            && field[WEEK_YEAR] > field[YEAR];
        if (weekDate && !cal.isWeekDateSupported()) {
            // Use YEAR instead
            if (!isSet(YEAR)) {
                set(YEAR, field[MAX_FIELD + WEEK_YEAR]);
            }
            weekDate = false;
        }
        
        //3.重置日期对象cal的属性值。
        cal.clear();
        // Set the fields from the min stamp to the max stamp so that
        // the field resolution works in the Calendar.
        //4.使用calb中的属性设置cal
        for (int stamp = MINIMUM_USER_STAMP; stamp < nextStamp; stamp++) {
            for (int index = 0; index <= maxFieldIndex; index++) {
                if (field[index] == stamp) {
                    cal.set(index, field[MAX_FIELD + index]);
                    break;
                }
            }
        }
 
        if (weekDate) {
            int weekOfYear = isSet(WEEK_OF_YEAR) ? field[MAX_FIELD + WEEK_OF_YEAR] : 1;
            int dayOfWeek = isSet(DAY_OF_WEEK) ?
                                field[MAX_FIELD + DAY_OF_WEEK] : cal.getFirstDayOfWeek();
            if (!isValidDayOfWeek(dayOfWeek) && cal.isLenient()) {
                if (dayOfWeek >= 8) {
                    dayOfWeek--;
                    weekOfYear += dayOfWeek / 7;
                    dayOfWeek = (dayOfWeek % 7) + 1;
                } else {
                    while (dayOfWeek <= 0) {
                        dayOfWeek += 7;
                        weekOfYear--;
                    }
                }
                dayOfWeek = toCalendarDayOfWeek(dayOfWeek);
            }
            cal.setWeekDate(field[MAX_FIELD + WEEK_YEAR], weekOfYear, dayOfWeek);
        }
        //5.返回设置好的cal对象
        return cal;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("CalendarBuilder:[");
        for (int i = 0; i < field.length; i++) {
            if (isSet(i)) {
                sb.append(i).append('=').append(field[MAX_FIELD + i]).append(',');
            }
        }
        int lastIndex = sb.length() - 1;
        if (sb.charAt(lastIndex) == ',') {
            sb.setLength(lastIndex);
        }
        sb.append(']');
        return sb.toString();
    }

    static int toISODayOfWeek(int calendarDayOfWeek) {
        return calendarDayOfWeek == SUNDAY ? 7 : calendarDayOfWeek - 1;
    }

    static int toCalendarDayOfWeek(int isoDayOfWeek) {
        if (!isValidDayOfWeek(isoDayOfWeek)) {
            // adjust later for lenient mode
            return isoDayOfWeek;
        }
        return isoDayOfWeek == 7 ? SUNDAY : isoDayOfWeek + 1;
    }

    static boolean isValidDayOfWeek(int dayOfWeek) {
        return dayOfWeek > 0 && dayOfWeek <= 7;
    }
}
