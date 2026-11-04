package lms;

import java.time.LocalDateTime;
import java.util.*;

/** Pure-logic checks (no database): `java -cp out lms.SelfCheck`. Exits non-zero on the first failure. */
public final class SelfCheck {
    static void check(boolean ok, String what) {
        if (!ok) { System.err.println("FAIL: " + what); System.exit(1); }
        System.out.println("ok   " + what);
    }

    public static void main(String[] a) {
        // JSON round trip, including escapes and unicode
        Map<String, Object> m = Json.obj("s", "a\"b\\c\nd ₹", "n", 12L, "d", 1.5, "t", true, "z", null, "l", Arrays.asList(1L, "x"));
        check(Json.parseObject(Json.write(m)).equals(m), "json round trip");

        // password hashing: verifies, rejects, salts differ
        String h1 = Auth.hashPassword("correct horse"), h2 = Auth.hashPassword("correct horse");
        check(Auth.verify("correct horse", h1) && !Auth.verify("wrong", h1) && !h1.equals(h2), "pbkdf2 verify + unique salt");

        // fines: end-of-day due dates, whole days late, capped
        LocalDateTime due = LocalDateTime.of(2026, 3, 10, 23, 59, 59);
        check(Circulation.fineFor(due, LocalDateTime.of(2026, 3, 10, 18, 0), 2, 500) == 0, "returned on due day: no fine");
        check(Circulation.fineFor(due, LocalDateTime.of(2026, 3, 11, 9, 0), 2, 500) == 2, "one day late: 1 x rate");
        check(Circulation.fineFor(due, LocalDateTime.of(2026, 3, 20, 9, 0), 2, 500) == 20, "ten days late: 10 x rate");
        check(Circulation.fineFor(due, LocalDateTime.of(2027, 3, 20, 9, 0), 2, 100) == 100, "fine capped at book price");

        // audit chain: changing any field changes the hash
        String g = "0000000000000000000000000000000000000000000000000000000000000000";
        String x = Audit.hash(g, 1000, 5, "ISSUE", "loan #1");
        check(x.equals(Audit.hash(g, 1000, 5, "ISSUE", "loan #1")) && !x.equals(Audit.hash(g, 1000, 5, "ISSUE", "loan #2")), "audit hash is deterministic and tamper-evident");

        // sample ISBNs carry a valid ISBN-13 checksum
        String isbn = Seed.isbn13(7);
        int sum = 0;
        for (int i = 0; i < 13; i++) sum += (isbn.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        check(isbn.length() == 13 && sum % 10 == 0, "generated ISBN-13 checksum");
        System.out.println("all checks passed");
    }
}
