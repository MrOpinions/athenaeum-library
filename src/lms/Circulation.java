package lms;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

import lms.Http.ApiException;

/**
 * Borrowing rules: issue, return, renew, holds queue, fines. Every method runs inside the caller's
 * transaction (Db.tx) and takes row locks (FOR UPDATE) so two desks can't hand out the same copy.
 */
final class Circulation {
    static final String CUR = "₹";      // rupee sign; change here (and CUR in web/app.js) for another currency
    static final double FINE_BLOCK = 100.00;  // unpaid fines at/above this block new loans
    static final int HOLD_HOURS = 72;         // how long a ready hold waits at the desk

    static long L(Object o) { return o instanceof Number ? ((Number) o).longValue() : 0; }
    static double D(Object o) { return o instanceof Number ? ((Number) o).doubleValue() : 0; }
    static LocalDateTime endOfDay(LocalDate d) { return d.atTime(23, 59, 59); }
    static double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    /** Pure rule, covered by SelfCheck: whole days late x daily rate, capped. Due dates are end-of-day. */
    static double fineFor(LocalDateTime due, LocalDateTime returned, double ratePerDay, double cap) {
        if (!returned.isAfter(due)) return 0;
        long days = ChronoUnit.DAYS.between(due.toLocalDate(), returned.toLocalDate());
        if (days <= 0) return 0;
        return Math.min(round2(days * ratePerDay), cap);
    }

    // ------------------------------------------------------------------ notifications / fines
    static void notify(Connection c, long userId, String kind, String msg, String refKey) throws SQLException {
        Db.update(c, "INSERT IGNORE INTO notifications (user_id, kind, message, created_at, ref_key) VALUES (?,?,?,?,?)",
                userId, kind, msg.length() > 300 ? msg.substring(0, 300) : msg, LocalDateTime.now(), refKey);
    }

    static void addFine(Connection c, long loanId, long userId, double amount, String reason, String title) throws SQLException {
        if (amount <= 0) return;
        Db.insert(c, "INSERT INTO fines (loan_id, user_id, amount, reason, created_at) VALUES (?,?,?,?,?)",
                loanId, userId, amount, reason, LocalDateTime.now());
        notify(c, userId, "FINE", String.format("A %s fine of %s%.2f was added for \"%s\".", reason.toLowerCase(), CUR, amount, title), null);
    }

    // ------------------------------------------------------------------ issue
    static Map<String, Object> issue(Connection c, long staffId, Long userId, String cardNo, String barcode) throws Exception {
        Map<String, Object> copy = Db.one(c, "SELECT cp.id, cp.book_id, cp.status, b.title FROM copies cp JOIN books b ON b.id = cp.book_id "
                + "WHERE cp.barcode = ? FOR UPDATE OF cp", barcode);
        if (copy == null) throw new ApiException(404, "No copy with barcode " + barcode);

        String sel = "SELECT u.id, u.name, u.status, u.expires_on, u.card_no, p.max_loans, p.loan_days FROM users u JOIN plans p ON p.id = u.plan_id ";
        Map<String, Object> u = userId != null
                ? Db.one(c, sel + "WHERE u.id = ? FOR UPDATE OF u", userId)
                : Db.one(c, sel + "WHERE u.card_no = ? FOR UPDATE OF u", cardNo);
        if (u == null) throw new ApiException(404, "Member not found");

        long uid = L(u.get("id")), copyId = L(copy.get("id")), bookId = L(copy.get("book_id"));
        String title = (String) copy.get("title");

        if (!"ACTIVE".equals(u.get("status"))) throw new ApiException(409, u.get("name") + "'s account is suspended");
        if (LocalDate.parse((String) u.get("expires_on")).isBefore(LocalDate.now()))
            throw new ApiException(409, "Membership expired on " + u.get("expires_on") + " — renew it first");
        long active = Db.num(c, "SELECT COUNT(*) FROM loans WHERE user_id = ? AND returned_at IS NULL", uid);
        if (active >= L(u.get("max_loans"))) throw new ApiException(409, "Loan limit reached (" + active + " of " + L(u.get("max_loans")) + ")");
        double owed = D(Db.one(c, "SELECT COALESCE(SUM(amount),0) AS t FROM fines WHERE user_id = ? AND status = 'UNPAID'", uid).get("t"));
        if (owed >= FINE_BLOCK) throw new ApiException(409, String.format("Unpaid fines of %s%.2f block new loans (limit %s%.2f)", CUR, owed, CUR, FINE_BLOCK));
        if (Db.num(c, "SELECT COUNT(*) FROM loans WHERE user_id = ? AND returned_at IS NULL AND due_at < ?", uid, LocalDateTime.now()) > 0)
            throw new ApiException(409, "Member has overdue items — return them first");
        if (Db.num(c, "SELECT COUNT(*) FROM loans l JOIN copies x ON x.id = l.copy_id WHERE l.user_id = ? AND x.book_id = ? AND l.returned_at IS NULL", uid, bookId) > 0)
            throw new ApiException(409, "Member already has a copy of this book");

        String st = (String) copy.get("status");
        if ("ON_HOLD".equals(st)) {
            Map<String, Object> res = Db.one(c, "SELECT user_id FROM reservations WHERE copy_id = ? AND status = 'READY' FOR UPDATE", copyId);
            if (res == null || L(res.get("user_id")) != uid) throw new ApiException(409, "This copy is held for another member");
            Db.update(c, "UPDATE copies SET status = 'AVAILABLE' WHERE id = ?", copyId); // hold is claimed below
        } else if (!"AVAILABLE".equals(st)) {
            throw new ApiException(409, "This copy is " + st.toLowerCase().replace('_', ' '));
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime due = endOfDay(now.toLocalDate().plusDays(L(u.get("loan_days"))));
        long loanId = Db.insert(c, "INSERT INTO loans (copy_id, user_id, issued_at, due_at, issued_by) VALUES (?,?,?,?,?)",
                copyId, uid, now, due, staffId);

        // the member no longer needs any hold they had on this book
        Map<String, Object> mine = Db.one(c, "SELECT id, copy_id FROM reservations WHERE user_id = ? AND book_id = ? AND status IN ('WAITING','READY') FOR UPDATE", uid, bookId);
        if (mine != null) {
            Db.update(c, "UPDATE reservations SET status = 'FULFILLED' WHERE id = ?", mine.get("id"));
            if (mine.get("copy_id") != null && L(mine.get("copy_id")) != copyId) {
                Db.update(c, "UPDATE copies SET status = 'AVAILABLE' WHERE id = ? AND status = 'ON_HOLD'", mine.get("copy_id"));
                promote(c, bookId);
            }
        }
        notify(c, uid, "LOAN", "Issued \"" + title + "\" — due " + due.toLocalDate() + ".", null);
        Audit.log(staffId, "ISSUE", "loan #" + loanId + " \"" + title + "\" (" + barcode + ") -> " + u.get("name") + " [" + u.get("card_no") + "]");
        return Json.obj("loan_id", loanId, "title", title, "member", u.get("name"), "due_at", due.format(Db.TS));
    }

    // ------------------------------------------------------------------ return
    static Map<String, Object> giveBack(Connection c, long staffId, String barcode, String cond) throws Exception {
        Map<String, Object> l = Db.one(c, "SELECT l.id, l.user_id, l.due_at, cp.id AS copy_id, cp.book_id, cp.price, b.title, u.name AS member, p.fine_per_day "
                + "FROM loans l JOIN copies cp ON cp.id = l.copy_id JOIN books b ON b.id = cp.book_id "
                + "JOIN users u ON u.id = l.user_id JOIN plans p ON p.id = u.plan_id "
                + "WHERE cp.barcode = ? AND l.returned_at IS NULL FOR UPDATE OF l", barcode);
        if (l == null) throw new ApiException(404, "No active loan for barcode " + barcode);

        long loanId = L(l.get("id")), uid = L(l.get("user_id")), bookId = L(l.get("book_id"));
        String title = (String) l.get("title");
        double price = D(l.get("price"));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime due = LocalDateTime.parse((String) l.get("due_at"));

        Db.update(c, "UPDATE loans SET returned_at = ? WHERE id = ?", now, loanId); // trigger frees the copy

        double overdue = fineFor(due, now, D(l.get("fine_per_day")), price > 0 ? price : 500);
        addFine(c, loanId, uid, overdue, "OVERDUE", title);
        double total = overdue;
        String condition = cond == null ? "OK" : cond.toUpperCase();
        if ("LOST".equals(condition)) {
            double f = price > 0 ? price : 300;
            Db.update(c, "UPDATE copies SET status = 'LOST' WHERE id = ?", l.get("copy_id"));
            addFine(c, loanId, uid, f, "LOST", title); total += f;
        } else if ("DAMAGED".equals(condition)) {
            double f = Math.max(round2(price * 0.25), 20.0);
            Db.update(c, "UPDATE copies SET status = 'DAMAGED' WHERE id = ?", l.get("copy_id"));
            addFine(c, loanId, uid, f, "DAMAGE", title); total += f;
        } else if (!"OK".equals(condition)) {
            throw new ApiException(400, "condition must be OK, DAMAGED or LOST");
        } else {
            promote(c, bookId);
        }
        notify(c, uid, "RETURN", "Thanks for returning \"" + title + "\".", null);
        Audit.log(staffId, "RETURN", "loan #" + loanId + " \"" + title + "\" (" + barcode + ") from " + l.get("member")
                + (total > 0 ? String.format(" fines %.2f", total) : "") + ("OK".equals(condition) ? "" : " " + condition));
        return Json.obj("loan_id", loanId, "title", title, "member", l.get("member"), "condition", condition, "fine", round2(total));
    }

    // ------------------------------------------------------------------ renew
    static Map<String, Object> renew(Connection c, Map<String, Object> actor, long loanId) throws Exception {
        Map<String, Object> l = Db.one(c, "SELECT l.id, l.user_id, l.due_at, l.returned_at, l.renewals, cp.book_id, b.title, p.max_renewals, p.loan_days "
                + "FROM loans l JOIN copies cp ON cp.id = l.copy_id JOIN books b ON b.id = cp.book_id "
                + "JOIN users u ON u.id = l.user_id JOIN plans p ON p.id = u.plan_id WHERE l.id = ? FOR UPDATE OF l", loanId);
        if (l == null) throw new ApiException(404, "Loan not found");
        boolean staff = !"MEMBER".equals(actor.get("role"));
        if (!staff && L(l.get("user_id")) != L(actor.get("id"))) throw new ApiException(403, "Not your loan");
        if (l.get("returned_at") != null) throw new ApiException(409, "Already returned");
        LocalDateTime due = LocalDateTime.parse((String) l.get("due_at"));
        if (due.isBefore(LocalDateTime.now())) throw new ApiException(409, "Overdue items must be returned, not renewed");
        if (L(l.get("renewals")) >= L(l.get("max_renewals"))) throw new ApiException(409, "Renewal limit reached");
        if (Db.num(c, "SELECT COUNT(*) FROM reservations WHERE book_id = ? AND status = 'WAITING'", l.get("book_id")) > 0)
            throw new ApiException(409, "Another member is waiting for this book");
        LocalDateTime nd = endOfDay(due.toLocalDate().plusDays(L(l.get("loan_days"))));
        Db.update(c, "UPDATE loans SET due_at = ?, renewals = renewals + 1 WHERE id = ?", nd, loanId);
        Audit.log(L(actor.get("id")), "RENEW", "loan #" + loanId + " \"" + l.get("title") + "\" new due " + nd.toLocalDate());
        return Json.obj("loan_id", loanId, "due_at", nd.format(Db.TS), "renewals", L(l.get("renewals")) + 1);
    }

    // ------------------------------------------------------------------ holds queue
    static Map<String, Object> reserve(Connection c, long userId, long bookId) throws Exception {
        Map<String, Object> book = Db.one(c, "SELECT id, title FROM books WHERE id = ?", bookId);
        if (book == null) throw new ApiException(404, "Book not found");
        if (Db.num(c, "SELECT COUNT(*) FROM copies WHERE book_id = ? AND status = 'AVAILABLE'", bookId) > 0)
            throw new ApiException(409, "A copy is on the shelf right now — ask at the desk to borrow it");
        if (Db.num(c, "SELECT COUNT(*) FROM copies WHERE book_id = ? AND status NOT IN ('LOST','WITHDRAWN')", bookId) == 0)
            throw new ApiException(409, "No circulating copies of this book");
        if (Db.num(c, "SELECT COUNT(*) FROM loans l JOIN copies x ON x.id = l.copy_id WHERE l.user_id = ? AND x.book_id = ? AND l.returned_at IS NULL", userId, bookId) > 0)
            throw new ApiException(409, "You already have this book");
        if (Db.num(c, "SELECT COUNT(*) FROM reservations WHERE user_id = ? AND book_id = ? AND status IN ('WAITING','READY')", userId, bookId) > 0)
            throw new ApiException(409, "You're already in the queue for this book");
        long id = Db.insert(c, "INSERT INTO reservations (book_id, user_id, created_at) VALUES (?,?,?)", bookId, userId, LocalDateTime.now());
        long pos = Db.num(c, "SELECT COUNT(*) FROM reservations WHERE book_id = ? AND status = 'WAITING' AND id <= ?", bookId, id);
        Audit.log(userId, "RESERVE", "hold #" + id + " on \"" + book.get("title") + "\" (position " + pos + ")");
        return Json.obj("reservation_id", id, "position", pos);
    }

    static void cancelReservation(Connection c, Map<String, Object> actor, long resId) throws Exception {
        Map<String, Object> r = Db.one(c, "SELECT r.id, r.user_id, r.book_id, r.status, r.copy_id, b.title FROM reservations r JOIN books b ON b.id = r.book_id WHERE r.id = ? FOR UPDATE OF r", resId);
        if (r == null) throw new ApiException(404, "Reservation not found");
        boolean staff = !"MEMBER".equals(actor.get("role"));
        if (!staff && L(r.get("user_id")) != L(actor.get("id"))) throw new ApiException(403, "Not your reservation");
        String st = (String) r.get("status");
        if (!"WAITING".equals(st) && !"READY".equals(st)) throw new ApiException(409, "Reservation is already " + st.toLowerCase());
        Db.update(c, "UPDATE reservations SET status = 'CANCELLED' WHERE id = ?", resId);
        if ("READY".equals(st) && r.get("copy_id") != null) {
            Db.update(c, "UPDATE copies SET status = 'AVAILABLE' WHERE id = ? AND status = 'ON_HOLD'", r.get("copy_id"));
            promote(c, L(r.get("book_id")));
        }
        Audit.log(L(actor.get("id")), "CANCEL_HOLD", "hold #" + resId + " on \"" + r.get("title") + "\"");
    }

    /** Hands free copies to the head of the waiting queue (oldest first). Safe to call any time. */
    static void promote(Connection c, long bookId) throws Exception {
        while (true) {
            Map<String, Object> res = Db.one(c, "SELECT r.id, r.user_id, b.title FROM reservations r JOIN books b ON b.id = r.book_id "
                    + "WHERE r.book_id = ? AND r.status = 'WAITING' ORDER BY r.id LIMIT 1 FOR UPDATE OF r", bookId);
            if (res == null) return;
            Map<String, Object> cp = Db.one(c, "SELECT id, shelf FROM copies WHERE book_id = ? AND status = 'AVAILABLE' ORDER BY id LIMIT 1 FOR UPDATE", bookId);
            if (cp == null) return;
            LocalDateTime now = LocalDateTime.now(), exp = now.plusHours(HOLD_HOURS);
            Db.update(c, "UPDATE copies SET status = 'ON_HOLD' WHERE id = ?", cp.get("id"));
            Db.update(c, "UPDATE reservations SET status = 'READY', ready_at = ?, expires_at = ?, copy_id = ? WHERE id = ?", now, exp, cp.get("id"), res.get("id"));
            notify(c, L(res.get("user_id")), "HOLD", "\"" + res.get("title") + "\" is ready for pickup at the desk until "
                    + exp.format(DateTimeFormatter.ofPattern("d MMM, HH:mm")) + ".", null);
        }
    }

    // ------------------------------------------------------------------ fines
    /** Pays one fine (fineId != null) or all of a member's unpaid fines. Returns the amount settled. */
    static double pay(Connection c, Map<String, Object> actor, Long fineId, long forUser, String method) throws Exception {
        boolean staff = !"MEMBER".equals(actor.get("role"));
        String m = method == null ? "CASH" : method.toUpperCase();
        List<Map<String, Object>> rows;
        if (fineId != null) {
            rows = Db.rows(c, "SELECT id, user_id, amount FROM fines WHERE id = ? AND status = 'UNPAID' FOR UPDATE", fineId);
        } else {
            rows = Db.rows(c, "SELECT id, user_id, amount FROM fines WHERE user_id = ? AND status = 'UNPAID' FOR UPDATE", forUser);
        }
        if (rows.isEmpty()) throw new ApiException(404, "Nothing to pay");
        double sum = 0;
        for (Map<String, Object> r : rows) {
            if (!staff && L(r.get("user_id")) != L(actor.get("id"))) throw new ApiException(403, "Not your fine");
            Db.update(c, "UPDATE fines SET status = 'PAID', paid_at = ?, method = ? WHERE id = ?", LocalDateTime.now(), m, r.get("id"));
            sum += D(r.get("amount"));
        }
        Audit.log(L(actor.get("id")), "FINE_PAID", String.format("%.2f via %s for member #%d (%d fine%s)", sum, m, L(rows.get(0).get("user_id")), rows.size(), rows.size() == 1 ? "" : "s"));
        return round2(sum);
    }

    static void waive(Connection c, long staffId, long fineId) throws Exception {
        int n = Db.update(c, "UPDATE fines SET status = 'WAIVED', paid_at = ?, method = 'WAIVED' WHERE id = ? AND status = 'UNPAID'", LocalDateTime.now(), fineId);
        if (n == 0) throw new ApiException(404, "No unpaid fine with that id");
        Audit.log(staffId, "FINE_WAIVED", "fine #" + fineId);
    }

    // ------------------------------------------------------------------ periodic maintenance
    static void maintenance() throws Exception {
        Db.tx(c -> {
            LocalDateTime now = LocalDateTime.now();
            for (Map<String, Object> r : Db.rows(c, "SELECT r.id, r.book_id, r.copy_id, r.user_id, b.title FROM reservations r JOIN books b ON b.id = r.book_id "
                    + "WHERE r.status = 'READY' AND r.expires_at < ? FOR UPDATE OF r", now)) {
                Db.update(c, "UPDATE reservations SET status = 'EXPIRED' WHERE id = ?", r.get("id"));
                if (r.get("copy_id") != null) Db.update(c, "UPDATE copies SET status = 'AVAILABLE' WHERE id = ? AND status = 'ON_HOLD'", r.get("copy_id"));
                notify(c, L(r.get("user_id")), "HOLD", "Your hold on \"" + r.get("title") + "\" expired.", null);
                Audit.log(null, "HOLD_EXPIRED", "hold #" + r.get("id") + " \"" + r.get("title") + "\"");
                promote(c, L(r.get("book_id")));
            }
            String today = now.format(DateTimeFormatter.BASIC_ISO_DATE);
            String q = "SELECT l.id, l.user_id, l.due_at, b.title FROM loans l JOIN copies cp ON cp.id = l.copy_id JOIN books b ON b.id = cp.book_id WHERE l.returned_at IS NULL AND ";
            for (Map<String, Object> r : Db.rows(c, q + "l.due_at BETWEEN ? AND ?", now, now.plusDays(2)))
                notify(c, L(r.get("user_id")), "DUE", "\"" + r.get("title") + "\" is due " + ((String) r.get("due_at")).substring(0, 10) + ".", "due-" + r.get("id"));
            for (Map<String, Object> r : Db.rows(c, q + "l.due_at < ?", now))
                notify(c, L(r.get("user_id")), "OVERDUE", "\"" + r.get("title") + "\" is overdue — fines are building up.", "od-" + r.get("id") + "-" + today);
            try (CallableStatement cs = c.prepareCall("{call sp_purge_sessions()}")) { cs.execute(); }
            return null;
        });
    }
}
