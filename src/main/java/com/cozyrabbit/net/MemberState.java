package com.cozyrabbit.net;

/**
 * What one person in a study room shares with the others. Only these fields leave the computer:
 * a nickname, a hat color and today's / this week's study numbers.
 */
public record MemberState(String id, String name, String hatColor, Status status, String subject,
                          String subjectColor, long todaySeconds, int todayFish, int rareFish,
                          long weekSeconds, int streak) {

    public enum Status { FISHING, COFFEE, DOZING }

    static final char FIELD = '\u001f';
    static final char RECORD = '\u001e';

    /** Removes the separator characters so a name can never break the message format. */
    static String clean(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c >= 0x20) {
                b.append(c);
            }
        }
        String out = b.toString().trim();
        return out.length() > 40 ? out.substring(0, 40) : out;
    }

    String encode() {
        return String.join(String.valueOf(FIELD), clean(id), clean(name), clean(hatColor), status.name(),
                clean(subject), clean(subjectColor), Long.toString(todaySeconds), Integer.toString(todayFish),
                Integer.toString(rareFish), Long.toString(weekSeconds), Integer.toString(streak));
    }

    static MemberState decode(String s) {
        String[] f = s.split(String.valueOf(FIELD), -1);
        if (f.length < 11) {
            throw new IllegalArgumentException("Bad member record");
        }
        Status st;
        try {
            st = Status.valueOf(f[3]);
        } catch (IllegalArgumentException e) {
            st = Status.DOZING;
        }
        return new MemberState(f[0], f[1], f[2], st, f[4], f[5], parseLong(f[6]), (int) parseLong(f[7]),
                (int) parseLong(f[8]), parseLong(f[9]), (int) parseLong(f[10]));
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
