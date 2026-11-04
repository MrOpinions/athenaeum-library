package lms;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import lms.Http.ApiException;
import lms.Http.Ctx;
import lms.Http.Raw;

import static lms.Circulation.L;
import static lms.Http.*;
import static lms.Http.Access.*;
import static lms.Json.obj;

/** All REST routes. Members see their own data; STAFF = librarian or admin. */
final class Api {
    private static final SecureRandom RNG = new SecureRandom();

    static final String BOOK_SELECT =
            "SELECT b.id, b.title, b.isbn, b.publisher, b.published_year, b.language, b.pages, b.category_id, cat.name AS category, cat.color, "
          + "(SELECT GROUP_CONCAT(a.name ORDER BY a.name SEPARATOR ', ') FROM book_authors ba JOIN authors a ON a.id = ba.author_id WHERE ba.book_id = b.id) AS authors, "
          + "s.total_copies, s.available_copies, s.avg_rating, s.review_count, s.times_borrowed "
          + "FROM books b JOIN categories cat ON cat.id = b.category_id JOIN v_book_stats s ON s.book_id = b.id";

    // ------------------------------------------------------------------ shared helpers
    static Map<String, Object> profile(long id) throws Exception {
        return Db.one("SELECT u.id, u.email, u.name, u.role, u.card_no, u.phone, u.joined_on, u.expires_on, u.status, u.plan_id, "
                + "p.name AS plan, p.max_loans, p.loan_days, p.max_renewals, p.fine_per_day "
                + "FROM users u JOIN plans p ON p.id = u.plan_id WHERE u.id = ?", id);
    }

    static void checkPassword(String pw) {
        if (pw == null || pw.length() < 8) throw new ApiException(400, "Password must be at least 8 characters");
    }

    static String tempPassword() {
        String a = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 12; i++) b.append(a.charAt(RNG.nextInt(a.length())));
        return b.toString();
    }

    static long createUser(Connection cn, String name, String email, String pw, String role, long planId, String phone) throws Exception {
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new ApiException(400, "Enter a valid email address");
        String card;
        do { card = String.format("LB%08d", ThreadLocalRandom.current().nextInt(100_000_000)); }
        while (Db.num(cn, "SELECT COUNT(*) FROM users WHERE card_no = ?", card) > 0);
        LocalDate today = LocalDate.now();
        return Db.insert(cn, "INSERT INTO users (email, name, password_hash, role, plan_id, card_no, phone, joined_on, expires_on) VALUES (?,?,?,?,?,?,?,?,?)",
                email.toLowerCase(), name, Auth.hashPassword(pw), role, planId, card, phone, today, today.plusYears(1));
    }

    static String like(String q) { return "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"; }

    static long saveBook(Connection cn, Long id, Ctx x) throws Exception {
        String title = x.req("title");
        String isbn = x.req("isbn").replace("-", "").replace(" ", "");
        if (!isbn.matches("\\d{10}|\\d{13}")) throw new ApiException(400, "ISBN must be 10 or 13 digits");
        Long cat = x.lng("category_id");
        if (cat == null) throw new ApiException(400, "category is required");
        Long year = x.lng("published_year"), pages = x.lng("pages");
        if (year != null && (year < 1000 || year > 2100)) throw new ApiException(400, "Year looks wrong");
        if (pages != null && pages <= 0) throw new ApiException(400, "Pages must be positive");
        List<String> names = new ArrayList<>();
        Object a = x.body.get("authors");
        if (a instanceof List) { for (Object o : (List<?>) a) if (o != null && !o.toString().trim().isEmpty()) names.add(o.toString().trim()); }
        else if (a != null) for (String s : a.toString().split("[,;]")) if (!s.trim().isEmpty()) names.add(s.trim());
        if (names.isEmpty()) throw new ApiException(400, "At least one author is required");
        String lang = x.str("language") == null ? "English" : x.str("language");
        if (id == null) {
            id = Db.insert(cn, "INSERT INTO books (isbn, title, publisher, published_year, category_id, language, pages, description, added_on) VALUES (?,?,?,?,?,?,?,?,?)",
                    isbn, title, x.str("publisher"), year, cat, lang, pages, x.str("description"), LocalDate.now());
        } else {
            if (Db.update(cn, "UPDATE books SET isbn=?, title=?, publisher=?, published_year=?, category_id=?, language=?, pages=?, description=? WHERE id=?",
                    isbn, title, x.str("publisher"), year, cat, lang, pages, x.str("description"), id) == 0
                    && Db.num(cn, "SELECT COUNT(*) FROM books WHERE id = ?", id) == 0) throw new ApiException(404, "Book not found");
            Db.update(cn, "DELETE FROM book_authors WHERE book_id = ?", id);
        }
        for (String n : names) {
            Db.update(cn, "INSERT IGNORE INTO authors (name) VALUES (?)", n);
            Db.update(cn, "INSERT IGNORE INTO book_authors (book_id, author_id) VALUES (?, (SELECT id FROM authors WHERE name = ?))", id, n);
        }
        return id;
    }

    static void addCopies(Connection cn, long bookId, int n, String shelf, double price) throws Exception {
        long have = Db.num(cn, "SELECT COUNT(*) FROM copies WHERE book_id = ?", bookId);
        for (int k = 1; k <= n; k++)
            Db.insert(cn, "INSERT INTO copies (book_id, barcode, shelf, price, acquired_on) VALUES (?,?,?,?,?)",
                    bookId, String.format("BK%05d-%02d", bookId, have + k), shelf, price, LocalDate.now());
    }

    static Raw csv(String name, List<Map<String, Object>> rows) {
        StringBuilder b = new StringBuilder("﻿");
        if (!rows.isEmpty()) {
            b.append(String.join(",", rows.get(0).keySet())).append("\r\n");
            for (Map<String, Object> r : rows) {
                boolean first = true;
                for (Object v : r.values()) {
                    if (!first) b.append(',');
                    first = false;
                    String s = v == null ? "" : v.toString();
                    if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0 && !(v instanceof Number)) s = "'" + s; // spreadsheet formula injection
                    b.append('"').append(s.replace("\"", "\"\"")).append('"');
                }
                b.append("\r\n");
            }
        }
        return new Raw(b.toString().getBytes(StandardCharsets.UTF_8), "text/csv; charset=utf-8", name);
    }

    // ------------------------------------------------------------------ routes
    static void register() {
        get("/api/health", PUBLIC, c -> {
            Map<String, Object> r = obj("ok", true);
            // Offer one-click demo logins only while the seeded admin still has the documented demo password.
            Map<String, Object> a = Db.one("SELECT password_hash FROM users WHERE email = 'admin@library.test'");
            if (a != null && Auth.verify(Seed.DEMO_PASSWORD, (String) a.get("password_hash"))) { r.put("demo", true); r.put("demo_password", Seed.DEMO_PASSWORD); }
            return r;
        });

        // ---------------- auth & profile
        post("/api/auth/login", PUBLIC, c -> {
            Map<String, Object> u = Auth.login(c.req("email"), c.req("password"));
            if (u == null) throw new ApiException(401, "Wrong email or password");
            long id = L(u.get("id"));
            String tok = Db.with(cn -> Auth.newSession(cn, id));
            return obj("token", tok, "user", profile(id));
        });
        post("/api/auth/register", PUBLIC, c -> {
            String pw = c.req("password");
            checkPassword(pw);
            String name = c.req("name");
            long id = Db.tx(cn -> {
                long plan = Db.num(cn, "SELECT id FROM plans WHERE name = 'Student'");
                long uid = createUser(cn, name, c.req("email"), pw, "MEMBER", plan, c.str("phone"));
                Audit.log(uid, "REGISTER", "self sign-up " + c.str("email"));
                return uid;
            });
            String tok = Db.with(cn -> Auth.newSession(cn, id));
            return obj("token", tok, "user", profile(id));
        });
        post("/api/auth/logout", ANY, c -> { Auth.logout(c.token); return obj("ok", true); });

        get("/api/me", ANY, c -> {
            Map<String, Object> p = profile(c.uid());
            p.put("active_loans", Db.num("SELECT COUNT(*) FROM loans WHERE user_id = ? AND returned_at IS NULL", c.uid()));
            p.put("unpaid_fines", Db.one("SELECT COALESCE(SUM(amount),0) AS t FROM fines WHERE user_id = ? AND status = 'UNPAID'", c.uid()).get("t"));
            p.put("unread", Db.num("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND read_at IS NULL", c.uid()));
            return p;
        });
        put("/api/me", ANY, c -> {
            Db.update("UPDATE users SET name = ?, phone = ? WHERE id = ?", c.req("name"), c.str("phone"), c.uid());
            return profile(c.uid());
        });
        post("/api/me/password", ANY, c -> {
            Map<String, Object> u = Db.one("SELECT password_hash FROM users WHERE id = ?", c.uid());
            if (!Auth.verify(c.req("old_password"), (String) u.get("password_hash"))) throw new ApiException(403, "Current password is wrong");
            checkPassword(c.req("new_password"));
            Db.update("UPDATE users SET password_hash = ? WHERE id = ?", Auth.hashPassword(c.req("new_password")), c.uid());
            Audit.now(c.uid(), "PASSWORD", "password changed");
            return obj("ok", true);
        });

        // ---------------- catalog
        get("/api/categories", PUBLIC, c -> Db.rows("SELECT cat.id, cat.name, cat.color, COUNT(b.id) AS books FROM categories cat "
                + "LEFT JOIN books b ON b.category_id = cat.id GROUP BY cat.id, cat.name, cat.color ORDER BY cat.name"));
        post("/api/categories", STAFF, c -> {
            long id = Db.tx(cn -> {
                long n = Db.insert(cn, "INSERT INTO categories (name, color) VALUES (?,?)", c.req("name"), c.str("color") == null ? "#8b7d6b" : c.str("color"));
                Audit.log(c.uid(), "CATEGORY_ADD", c.req("name"));
                return n;
            });
            return obj("id", id);
        });
        get("/api/plans", ANY, c -> Db.rows("SELECT * FROM plans ORDER BY id"));

        get("/api/books", PUBLIC, c -> {
            StringBuilder w = new StringBuilder(" WHERE 1=1");
            List<Object> args = new ArrayList<>();
            String q = c.q("q");
            if (q != null) {
                w.append(" AND (b.title LIKE ? OR b.isbn LIKE ? OR b.publisher LIKE ? OR EXISTS (SELECT 1 FROM book_authors ba JOIN authors a ON a.id = ba.author_id WHERE ba.book_id = b.id AND a.name LIKE ?))");
                for (int i = 0; i < 4; i++) args.add(like(q));
            }
            if (c.q("category") != null) { w.append(" AND b.category_id = ?"); args.add(c.qint("category", 0, 0, Integer.MAX_VALUE)); }
            if ("1".equals(c.q("available"))) w.append(" AND s.available_copies > 0");
            String order;
            switch (c.q("sort") == null ? "title" : c.q("sort")) {
                case "rating":  order = "s.avg_rating IS NULL, s.avg_rating DESC, s.review_count DESC, b.title"; break;
                case "popular": order = "s.times_borrowed DESC, b.title"; break;
                case "newest":  order = "b.id DESC"; break;
                case "year":    order = "b.published_year DESC, b.title"; break;
                default:        order = "b.title";
            }
            int size = c.qint("size", 12, 1, 60), page = c.qint("page", 1, 1, 100000);
            long total = Db.num("SELECT COUNT(*) FROM books b JOIN v_book_stats s ON s.book_id = b.id" + w, args.toArray());
            args.add(size); args.add((page - 1) * size);
            List<Map<String, Object>> items = Db.rows(BOOK_SELECT + w + " ORDER BY " + order + " LIMIT ? OFFSET ?", args.toArray());
            return obj("items", items, "total", total, "page", page, "size", size);
        });

        get("/api/books/{id}", PUBLIC, c -> {
            long id = c.pid("id");
            Map<String, Object> b = Db.one(BOOK_SELECT + " WHERE b.id = ?", id);
            if (b == null) throw new ApiException(404, "Book not found");
            Map<String, Object> extra = Db.one("SELECT description, added_on FROM books WHERE id = ?", id);
            b.putAll(extra);
            b.put("waiting", Db.num("SELECT COUNT(*) FROM reservations WHERE book_id = ? AND status = 'WAITING'", id));
            List<Map<String, Object>> revs = Db.rows("SELECT r.id, r.rating, r.body, r.created_at, r.user_id, u.name FROM reviews r JOIN users u ON u.id = r.user_id "
                    + "WHERE r.book_id = ? ORDER BY r.id DESC LIMIT 50", id);
            if (c.user == null) for (Map<String, Object> r : revs) { r.put("name", "A reader"); r.remove("user_id"); }
            b.put("reviews", revs);
            b.put("shelves", Db.rows("SELECT DISTINCT shelf FROM copies WHERE book_id = ? AND status = 'AVAILABLE' AND shelf IS NOT NULL", id));
            b.put("related", Db.rows(BOOK_SELECT + " WHERE b.category_id = ? AND b.id <> ? ORDER BY s.times_borrowed DESC LIMIT 6", b.get("category_id"), id));
            if (c.user != null) {
                b.put("favorite", Db.num("SELECT COUNT(*) FROM favorites WHERE user_id = ? AND book_id = ?", c.uid(), id) > 0);
                b.put("my_loan", Db.one("SELECT l.id, l.due_at, l.renewals FROM loans l JOIN copies cp ON cp.id = l.copy_id "
                        + "WHERE l.user_id = ? AND cp.book_id = ? AND l.returned_at IS NULL", c.uid(), id));
                b.put("my_hold", Db.one("SELECT r.id, r.status, r.expires_at, (SELECT COUNT(*) FROM reservations x WHERE x.book_id = r.book_id AND x.status = 'WAITING' AND x.id <= r.id) AS position "
                        + "FROM reservations r WHERE r.user_id = ? AND r.book_id = ? AND r.status IN ('WAITING','READY')", c.uid(), id));
                b.put("can_review", Db.num("SELECT COUNT(*) FROM loans l JOIN copies cp ON cp.id = l.copy_id WHERE l.user_id = ? AND cp.book_id = ?", c.uid(), id) > 0);
            }
            if (c.staff()) b.put("copies", Db.rows("SELECT cp.id, cp.barcode, cp.shelf, cp.status, cp.price, "
                    + "(SELECT CONCAT(u.name, ' (', u.card_no, ')') FROM loans l JOIN users u ON u.id = l.user_id WHERE l.copy_id = cp.id AND l.returned_at IS NULL LIMIT 1) AS borrower, "
                    + "(SELECT l.due_at FROM loans l WHERE l.copy_id = cp.id AND l.returned_at IS NULL LIMIT 1) AS due_at "
                    + "FROM copies cp WHERE cp.book_id = ? ORDER BY cp.id", id));
            return b;
        });

        post("/api/books", STAFF, c -> {
            int copies = c.lng("copies") == null ? 1 : (int) Math.max(0, Math.min(20, c.lng("copies")));
            long id = Db.tx(cn -> {
                long bid = saveBook(cn, null, c);
                addCopies(cn, bid, copies, c.str("shelf") == null ? "NEW" : c.str("shelf"), c.dbl("price") == null ? 0 : c.dbl("price"));
                Audit.log(c.uid(), "BOOK_ADD", "#" + bid + " \"" + c.req("title") + "\" x" + copies);
                return bid;
            });
            return obj("id", id);
        });
        put("/api/books/{id}", STAFF, c -> {
            Db.tx(cn -> {
                saveBook(cn, c.pid("id"), c);
                Audit.log(c.uid(), "BOOK_EDIT", "#" + c.pid("id") + " \"" + c.req("title") + "\"");
                return null;
            });
            return obj("id", c.pid("id"));
        });
        delete("/api/books/{id}", ADMIN, c -> {
            Db.tx(cn -> {
                Map<String, Object> b = Db.one(cn, "SELECT title FROM books WHERE id = ?", c.pid("id"));
                if (b == null) throw new ApiException(404, "Book not found");
                Db.update(cn, "DELETE FROM books WHERE id = ?", c.pid("id"));
                Audit.log(c.uid(), "BOOK_DELETE", "#" + c.pid("id") + " \"" + b.get("title") + "\"");
                return null;
            });
            return obj("ok", true);
        });
        post("/api/books/{id}/copies", STAFF, c -> {
            int n = (int) Math.max(1, Math.min(20, c.lng("count") == null ? 1 : c.lng("count")));
            Db.tx(cn -> {
                if (Db.num(cn, "SELECT COUNT(*) FROM books WHERE id = ?", c.pid("id")) == 0) throw new ApiException(404, "Book not found");
                addCopies(cn, c.pid("id"), n, c.str("shelf") == null ? "NEW" : c.str("shelf"), c.dbl("price") == null ? 0 : c.dbl("price"));
                Circulation.promote(cn, c.pid("id"));
                Audit.log(c.uid(), "COPIES_ADD", n + " copies for book #" + c.pid("id"));
                return null;
            });
            return obj("ok", true);
        });
        put("/api/copies/{id}", STAFF, c -> {
            Db.tx(cn -> {
                Map<String, Object> cp = Db.one(cn, "SELECT id, book_id, status, barcode FROM copies WHERE id = ? FOR UPDATE", c.pid("id"));
                if (cp == null) throw new ApiException(404, "Copy not found");
                String st = c.str("status") == null ? (String) cp.get("status") : c.str("status").toUpperCase();
                if (!Arrays.asList("AVAILABLE", "DAMAGED", "WITHDRAWN", "LOST").contains(st) && !st.equals(cp.get("status")))
                    throw new ApiException(400, "Status can be AVAILABLE, DAMAGED, WITHDRAWN or LOST");
                if (("ISSUED".equals(cp.get("status")) || "ON_HOLD".equals(cp.get("status"))) && !st.equals(cp.get("status")))
                    throw new ApiException(409, "Copy is " + cp.get("status") + " — return or release it first");
                Db.update(cn, "UPDATE copies SET status = ?, shelf = COALESCE(?, shelf) WHERE id = ?", st, c.str("shelf"), c.pid("id"));
                if ("AVAILABLE".equals(st)) Circulation.promote(cn, L(cp.get("book_id")));
                Audit.log(c.uid(), "COPY_EDIT", cp.get("barcode") + " -> " + st);
                return null;
            });
            return obj("ok", true);
        });
        get("/api/copies/lookup/{barcode}", STAFF, c -> {
            Map<String, Object> r = Db.one("SELECT cp.id, cp.barcode, cp.status, cp.shelf, b.id AS book_id, b.title, "
                    + "(SELECT GROUP_CONCAT(a.name SEPARATOR ', ') FROM book_authors ba JOIN authors a ON a.id = ba.author_id WHERE ba.book_id = b.id) AS authors, "
                    + "(SELECT u.name FROM loans l JOIN users u ON u.id = l.user_id WHERE l.copy_id = cp.id AND l.returned_at IS NULL LIMIT 1) AS borrower, "
                    + "(SELECT l.due_at FROM loans l WHERE l.copy_id = cp.id AND l.returned_at IS NULL LIMIT 1) AS due_at, "
                    + "(SELECT u.name FROM reservations r JOIN users u ON u.id = r.user_id WHERE r.copy_id = cp.id AND r.status = 'READY' LIMIT 1) AS held_for "
                    + "FROM copies cp JOIN books b ON b.id = cp.book_id WHERE cp.barcode = ?", c.path.get("barcode"));
            if (r == null) throw new ApiException(404, "No copy with that barcode");
            return r;
        });

        // ---------------- reviews & favourites
        post("/api/books/{id}/reviews", ANY, c -> {
            long rating = c.lng("rating") == null ? 0 : c.lng("rating");
            if (rating < 1 || rating > 5) throw new ApiException(400, "Rating must be 1 to 5");
            if (Db.num("SELECT COUNT(*) FROM loans l JOIN copies cp ON cp.id = l.copy_id WHERE l.user_id = ? AND cp.book_id = ?", c.uid(), c.pid("id")) == 0)
                throw new ApiException(403, "Only members who have borrowed this book can review it");
            String body = c.str("body");
            if (body != null && body.length() > 1000) body = body.substring(0, 1000);
            Db.update("INSERT INTO reviews (book_id, user_id, rating, body, created_at) VALUES (?,?,?,?,?) "
                    + "ON DUPLICATE KEY UPDATE rating = VALUES(rating), body = VALUES(body), created_at = VALUES(created_at)",
                    c.pid("id"), c.uid(), rating, body, LocalDateTime.now());
            return obj("ok", true);
        });
        delete("/api/reviews/{id}", ANY, c -> {
            Map<String, Object> r = Db.one("SELECT user_id FROM reviews WHERE id = ?", c.pid("id"));
            if (r == null) throw new ApiException(404, "Review not found");
            if (!c.staff() && L(r.get("user_id")) != c.uid()) throw new ApiException(403, "Not your review");
            Db.update("DELETE FROM reviews WHERE id = ?", c.pid("id"));
            return obj("ok", true);
        });
        post("/api/books/{id}/favorite", ANY, c -> {
            if (Db.update("DELETE FROM favorites WHERE user_id = ? AND book_id = ?", c.uid(), c.pid("id")) > 0) return obj("favorite", false);
            Db.update("INSERT IGNORE INTO favorites (user_id, book_id) VALUES (?,?)", c.uid(), c.pid("id"));
            return obj("favorite", true);
        });
        get("/api/me/favorites", ANY, c -> Db.rows(BOOK_SELECT + " JOIN favorites f ON f.book_id = b.id WHERE f.user_id = ? ORDER BY b.title", c.uid()));

        // ---------------- circulation
        post("/api/loans/issue", STAFF, c -> Db.tx(cn -> Circulation.issue(cn, c.uid(), c.lng("user_id"), c.str("card_no"), c.req("barcode"))));
        post("/api/loans/return", STAFF, c -> Db.tx(cn -> Circulation.giveBack(cn, c.uid(), c.req("barcode"), c.str("condition"))));
        post("/api/loans/{id}/renew", ANY, c -> Db.tx(cn -> Circulation.renew(cn, c.user, c.pid("id"))));

        get("/api/loans", ANY, c -> {
            StringBuilder w = new StringBuilder(" WHERE 1=1");
            List<Object> args = new ArrayList<>();
            if (!c.staff()) { w.append(" AND l.user_id = ?"); args.add(c.uid()); }
            else if (c.q("user_id") != null) { w.append(" AND l.user_id = ?"); args.add(c.qint("user_id", 0, 0, Integer.MAX_VALUE)); }
            String st = c.q("status") == null ? "active" : c.q("status");
            String order = "l.id DESC";
            if ("active".equals(st)) { w.append(" AND l.returned_at IS NULL"); order = "l.due_at"; }
            else if ("overdue".equals(st)) { w.append(" AND l.returned_at IS NULL AND l.due_at < ?"); args.add(LocalDateTime.now()); order = "l.due_at"; }
            else if ("returned".equals(st)) { w.append(" AND l.returned_at IS NOT NULL"); order = "l.returned_at DESC"; }
            if (c.q("q") != null) {
                w.append(" AND (b.title LIKE ? OR u.name LIKE ? OR u.card_no LIKE ? OR cp.barcode LIKE ?)");
                for (int i = 0; i < 4; i++) args.add(like(c.q("q")));
            }
            String from = " FROM loans l JOIN copies cp ON cp.id = l.copy_id JOIN books b ON b.id = cp.book_id JOIN users u ON u.id = l.user_id JOIN categories cat ON cat.id = b.category_id";
            int size = c.qint("size", 20, 1, 100), page = c.qint("page", 1, 1, 100000);
            long total = Db.num("SELECT COUNT(*)" + from + w, args.toArray());
            args.add(size); args.add((page - 1) * size);
            List<Map<String, Object>> items = Db.rows("SELECT l.id, l.issued_at, l.due_at, l.returned_at, l.renewals, b.id AS book_id, b.title, cat.color, cp.barcode, "
                    + "u.id AS user_id, u.name AS member, u.card_no, (l.returned_at IS NULL AND l.due_at < NOW()) AS overdue, "
                    + "(l.returned_at IS NULL AND l.renewals < (SELECT p.max_renewals FROM plans p WHERE p.id = u.plan_id) AND l.due_at >= NOW()) AS renewable"
                    + from + w + " ORDER BY " + order + " LIMIT ? OFFSET ?", args.toArray());
            return obj("items", items, "total", total, "page", page, "size", size);
        });

        post("/api/books/{id}/reserve", ANY, c -> {
            long target = c.staff() && c.lng("user_id") != null ? c.lng("user_id") : c.uid();
            return Db.tx(cn -> Circulation.reserve(cn, target, c.pid("id")));
        });
        delete("/api/reservations/{id}", ANY, c -> {
            Db.tx(cn -> { Circulation.cancelReservation(cn, c.user, c.pid("id")); return null; });
            return obj("ok", true);
        });
        get("/api/reservations", ANY, c -> {
            StringBuilder w = new StringBuilder(" WHERE r.status IN ('WAITING','READY')");
            List<Object> args = new ArrayList<>();
            if ("all".equals(c.q("status"))) w = new StringBuilder(" WHERE 1=1");
            if (!c.staff()) { w.append(" AND r.user_id = ?"); args.add(c.uid()); }
            return Db.rows("SELECT r.id, r.status, r.created_at, r.ready_at, r.expires_at, b.id AS book_id, b.title, cat.color, u.id AS user_id, u.name AS member, u.card_no, "
                    + "cp.barcode, cp.shelf, (SELECT COUNT(*) FROM reservations x WHERE x.book_id = r.book_id AND x.status = 'WAITING' AND x.id <= r.id) AS position "
                    + "FROM reservations r JOIN books b ON b.id = r.book_id JOIN categories cat ON cat.id = b.category_id JOIN users u ON u.id = r.user_id LEFT JOIN copies cp ON cp.id = r.copy_id"
                    + w + " ORDER BY r.id DESC LIMIT 200", args.toArray());
        });

        get("/api/fines", ANY, c -> {
            StringBuilder w = new StringBuilder(" WHERE 1=1");
            List<Object> args = new ArrayList<>();
            if (!c.staff()) { w.append(" AND f.user_id = ?"); args.add(c.uid()); }
            else if (c.q("user_id") != null) { w.append(" AND f.user_id = ?"); args.add(c.qint("user_id", 0, 0, Integer.MAX_VALUE)); }
            if (c.q("status") != null && !"all".equals(c.q("status"))) { w.append(" AND f.status = ?"); args.add(c.q("status").toUpperCase()); }
            return Db.rows("SELECT f.id, f.amount, f.reason, f.status, f.created_at, f.paid_at, f.method, b.title, u.id AS user_id, u.name AS member, u.card_no "
                    + "FROM fines f JOIN loans l ON l.id = f.loan_id JOIN copies cp ON cp.id = l.copy_id JOIN books b ON b.id = cp.book_id JOIN users u ON u.id = f.user_id"
                    + w + " ORDER BY f.id DESC LIMIT 300", args.toArray());
        });
        post("/api/fines/pay-all", ANY, c -> {
            long target = c.staff() && c.lng("user_id") != null ? c.lng("user_id") : c.uid();
            return obj("paid", Db.tx(cn -> Circulation.pay(cn, c.user, null, target, c.str("method"))));
        });
        post("/api/fines/{id}/pay", ANY, c -> obj("paid", Db.tx(cn -> Circulation.pay(cn, c.user, c.pid("id"), 0, c.str("method")))));
        post("/api/fines/{id}/waive", STAFF, c -> {
            Db.tx(cn -> { Circulation.waive(cn, c.uid(), c.pid("id")); return null; });
            return obj("ok", true);
        });

        // ---------------- members (staff)
        get("/api/members", STAFF, c -> {
            StringBuilder w = new StringBuilder(" WHERE 1=1");
            List<Object> args = new ArrayList<>();
            if (c.q("q") != null) { w.append(" AND (u.name LIKE ? OR u.email LIKE ? OR u.card_no LIKE ?)"); for (int i = 0; i < 3; i++) args.add(like(c.q("q"))); }
            if (c.q("role") != null) { w.append(" AND u.role = ?"); args.add(c.q("role").toUpperCase()); }
            if (c.q("status") != null) { w.append(" AND u.status = ?"); args.add(c.q("status").toUpperCase()); }
            return Db.rows("SELECT u.id, u.name, u.email, u.card_no, u.role, u.status, u.joined_on, u.expires_on, p.name AS plan, "
                    + "(SELECT COUNT(*) FROM loans l WHERE l.user_id = u.id AND l.returned_at IS NULL) AS active_loans, "
                    + "(SELECT COALESCE(SUM(f.amount),0) FROM fines f WHERE f.user_id = u.id AND f.status = 'UNPAID') AS unpaid "
                    + "FROM users u JOIN plans p ON p.id = u.plan_id" + w + " ORDER BY u.name LIMIT 100", args.toArray());
        });
        get("/api/members/{id}", STAFF, c -> {
            Map<String, Object> p = profile(c.pid("id"));
            if (p == null) throw new ApiException(404, "Member not found");
            p.put("unpaid_fines", Db.one("SELECT COALESCE(SUM(amount),0) AS t FROM fines WHERE user_id = ? AND status = 'UNPAID'", c.pid("id")).get("t"));
            p.put("loans", Db.rows("SELECT l.id, l.issued_at, l.due_at, l.returned_at, b.id AS book_id, b.title, cp.barcode, (l.returned_at IS NULL AND l.due_at < NOW()) AS overdue "
                    + "FROM loans l JOIN copies cp ON cp.id = l.copy_id JOIN books b ON b.id = cp.book_id WHERE l.user_id = ? ORDER BY l.id DESC LIMIT 15", c.pid("id")));
            p.put("fines", Db.rows("SELECT f.id, f.amount, f.reason, f.status, f.created_at, b.title FROM fines f JOIN loans l ON l.id = f.loan_id "
                    + "JOIN copies cp ON cp.id = l.copy_id JOIN books b ON b.id = cp.book_id WHERE f.user_id = ? ORDER BY f.id DESC LIMIT 15", c.pid("id")));
            return p;
        });
        post("/api/members", STAFF, c -> {
            String temp = c.str("password") == null ? tempPassword() : c.str("password");
            checkPassword(temp);
            String role = c.str("role") == null ? "MEMBER" : c.str("role").toUpperCase();
            if (!Arrays.asList("MEMBER", "LIBRARIAN", "ADMIN").contains(role)) throw new ApiException(400, "Bad role");
            if (!"MEMBER".equals(role) && !"ADMIN".equals(c.user.get("role"))) throw new ApiException(403, "Only an admin can create staff accounts");
            long plan = c.lng("plan_id") == null ? Db.num("SELECT id FROM plans WHERE name = 'Student'") : c.lng("plan_id");
            long id = Db.tx(cn -> {
                long uid = createUser(cn, c.req("name"), c.req("email"), temp, role, plan, c.str("phone"));
                Audit.log(c.uid(), "MEMBER_ADD", "#" + uid + " " + c.req("name") + " (" + role + ")");
                return uid;
            });
            Map<String, Object> out = obj("id", id, "profile", profile(id));
            if (c.str("password") == null) out.put("temp_password", temp);
            return out;
        });
        put("/api/members/{id}", STAFF, c -> {
            Db.tx(cn -> {
                Map<String, Object> u = Db.one(cn, "SELECT id, role, expires_on FROM users WHERE id = ? FOR UPDATE", c.pid("id"));
                if (u == null) throw new ApiException(404, "Member not found");
                if (c.str("role") != null) {
                    if (!"ADMIN".equals(c.user.get("role"))) throw new ApiException(403, "Only an admin can change roles");
                    String role = c.str("role").toUpperCase();
                    if (!Arrays.asList("MEMBER", "LIBRARIAN", "ADMIN").contains(role)) throw new ApiException(400, "Bad role");
                    if (c.pid("id") == c.uid() && !"ADMIN".equals(role)) throw new ApiException(409, "You can't demote yourself");
                    Db.update(cn, "UPDATE users SET role = ? WHERE id = ?", role, c.pid("id"));
                }
                if (c.str("status") != null) {
                    String s = c.str("status").toUpperCase();
                    if (!"ACTIVE".equals(s) && !"SUSPENDED".equals(s)) throw new ApiException(400, "Bad status");
                    if (c.pid("id") == c.uid() && "SUSPENDED".equals(s)) throw new ApiException(409, "You can't suspend yourself");
                    Db.update(cn, "UPDATE users SET status = ? WHERE id = ?", s, c.pid("id"));
                }
                if (c.lng("plan_id") != null) Db.update(cn, "UPDATE users SET plan_id = ? WHERE id = ?", c.lng("plan_id"), c.pid("id"));
                if (c.str("name") != null) Db.update(cn, "UPDATE users SET name = ? WHERE id = ?", c.str("name"), c.pid("id"));
                if (c.body.containsKey("phone")) Db.update(cn, "UPDATE users SET phone = ? WHERE id = ?", c.str("phone"), c.pid("id"));
                if (c.lng("extend_months") != null) {
                    long m = Math.max(1, Math.min(60, c.lng("extend_months")));
                    LocalDate cur = LocalDate.parse((String) u.get("expires_on"));
                    LocalDate base = cur.isBefore(LocalDate.now()) ? LocalDate.now() : cur;
                    Db.update(cn, "UPDATE users SET expires_on = ? WHERE id = ?", base.plusMonths(m), c.pid("id"));
                }
                Audit.log(c.uid(), "MEMBER_EDIT", "#" + c.pid("id") + " " + c.body.keySet());
                return null;
            });
            return profile(c.pid("id"));
        });
        post("/api/members/{id}/reset-password", ADMIN, c -> {
            String temp = tempPassword();
            if (Db.update("UPDATE users SET password_hash = ? WHERE id = ?", Auth.hashPassword(temp), c.pid("id")) == 0) throw new ApiException(404, "Member not found");
            Db.update("DELETE FROM sessions WHERE user_id = ?", c.pid("id"));
            Audit.now(c.uid(), "PASSWORD_RESET", "member #" + c.pid("id"));
            return obj("temp_password", temp);
        });

        // ---------------- acquisition requests
        post("/api/requests", ANY, c -> {
            long id = Db.insert("INSERT INTO book_requests (user_id, title, author, note, created_at) VALUES (?,?,?,?,?)",
                    c.uid(), c.req("title"), c.str("author"), c.str("note"), LocalDateTime.now());
            return obj("id", id);
        });
        get("/api/requests", ANY, c -> Db.rows("SELECT r.id, r.title, r.author, r.note, r.status, r.created_at, u.name AS member FROM book_requests r JOIN users u ON u.id = r.user_id "
                + "WHERE (? OR r.user_id = ?) ORDER BY r.id DESC LIMIT 100", c.staff(), c.uid()));
        put("/api/requests/{id}", STAFF, c -> {
            String st = c.req("status").toUpperCase();
            if (!Arrays.asList("PENDING", "APPROVED", "REJECTED", "PURCHASED").contains(st)) throw new ApiException(400, "Bad status");
            Db.tx(cn -> {
                Map<String, Object> r = Db.one(cn, "SELECT user_id, title FROM book_requests WHERE id = ? FOR UPDATE", c.pid("id"));
                if (r == null) throw new ApiException(404, "Request not found");
                Db.update(cn, "UPDATE book_requests SET status = ?, decided_by = ? WHERE id = ?", st, c.uid(), c.pid("id"));
                Circulation.notify(cn, L(r.get("user_id")), "REQUEST", "Your request for \"" + r.get("title") + "\" is now " + st.toLowerCase() + ".", null);
                Audit.log(c.uid(), "REQUEST", "#" + c.pid("id") + " \"" + r.get("title") + "\" -> " + st);
                return null;
            });
            return obj("ok", true);
        });

        // ---------------- notifications
        get("/api/notifications", ANY, c -> obj(
                "items", Db.rows("SELECT id, kind, message, created_at, read_at FROM notifications WHERE user_id = ? ORDER BY id DESC LIMIT 30", c.uid()),
                "unread", Db.num("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND read_at IS NULL", c.uid())));
        post("/api/notifications/read-all", ANY, c -> {
            Db.update("UPDATE notifications SET read_at = ? WHERE user_id = ? AND read_at IS NULL", LocalDateTime.now(), c.uid());
            return obj("ok", true);
        });

        // ---------------- dashboards
        get("/api/dashboard", STAFF, c -> {
            LocalDateTime now = LocalDateTime.now();
            Map<String, Object> k = Db.one("SELECT (SELECT COUNT(*) FROM books) AS books, "
                    + "(SELECT COUNT(*) FROM copies WHERE status NOT IN ('LOST','WITHDRAWN')) AS copies, "
                    + "(SELECT COUNT(*) FROM copies WHERE status = 'AVAILABLE') AS available, "
                    + "(SELECT COUNT(*) FROM users WHERE role = 'MEMBER' AND status = 'ACTIVE') AS members, "
                    + "(SELECT COUNT(*) FROM loans WHERE returned_at IS NULL) AS active_loans, "
                    + "(SELECT COUNT(*) FROM v_overdue) AS overdue, "
                    + "(SELECT COALESCE(SUM(amount),0) FROM fines WHERE status = 'UNPAID') AS unpaid_fines, "
                    + "(SELECT COALESCE(SUM(amount),0) FROM fines WHERE status = 'PAID' AND paid_at >= ?) AS collected_30d, "
                    + "(SELECT COUNT(*) FROM reservations WHERE status IN ('WAITING','READY')) AS holds, "
                    + "(SELECT COUNT(*) FROM book_requests WHERE status = 'PENDING') AS pending_requests, "
                    + "(SELECT COUNT(*) FROM loans WHERE DATE(issued_at) = CURDATE()) AS issued_today, "
                    + "(SELECT COUNT(*) FROM loans WHERE DATE(returned_at) = CURDATE()) AS returned_today", now.minusDays(30));
            LocalDateTime since = now.toLocalDate().withDayOfMonth(1).minusMonths(5).atStartOfDay();
            return obj("kpi", k,
                    "monthly", Db.rows("SELECT DATE_FORMAT(issued_at, '%Y-%m') AS m, COUNT(*) AS n FROM loans WHERE issued_at >= ? GROUP BY DATE_FORMAT(issued_at, '%Y-%m') ORDER BY m", since),
                    "fines_monthly", Db.rows("SELECT DATE_FORMAT(paid_at, '%Y-%m') AS m, SUM(amount) AS n FROM fines WHERE status = 'PAID' AND paid_at >= ? GROUP BY DATE_FORMAT(paid_at, '%Y-%m') ORDER BY m", since),
                    "by_category", Db.rows("SELECT cat.name, cat.color, COUNT(*) AS n FROM loans l JOIN copies cp ON cp.id = l.copy_id JOIN books b ON b.id = cp.book_id "
                            + "JOIN categories cat ON cat.id = b.category_id GROUP BY cat.id, cat.name, cat.color ORDER BY n DESC"),
                    "by_weekday", Db.rows("SELECT DAYOFWEEK(issued_at) AS d, COUNT(*) AS n FROM loans GROUP BY DAYOFWEEK(issued_at) ORDER BY d"),
                    "top_books", Db.rows("SELECT b.id, b.title, COUNT(*) AS n FROM loans l JOIN copies cp ON cp.id = l.copy_id JOIN books b ON b.id = cp.book_id GROUP BY b.id, b.title ORDER BY n DESC, b.title LIMIT 8"),
                    "overdue", Db.rows("SELECT * FROM v_overdue ORDER BY days_overdue DESC LIMIT 8"),
                    "activity", Db.rows("SELECT a.id, a.ts_ms, a.action, a.detail, u.name AS actor FROM audit_log a LEFT JOIN users u ON u.id = a.actor_id ORDER BY a.id DESC LIMIT 10"));
        });

        get("/api/me/summary", ANY, c -> {
            long me = c.uid();
            Map<String, Object> s = Db.one("SELECT (SELECT COUNT(*) FROM loans WHERE user_id = ? AND returned_at IS NULL) AS active, "
                    + "(SELECT COUNT(*) FROM loans WHERE user_id = ? AND returned_at IS NULL AND due_at < NOW()) AS overdue, "
                    + "(SELECT COUNT(*) FROM loans WHERE user_id = ? AND returned_at IS NOT NULL) AS read_count, "
                    + "(SELECT COALESCE(SUM(amount),0) FROM fines WHERE user_id = ? AND status = 'UNPAID') AS unpaid, "
                    + "(SELECT COUNT(*) FROM reservations WHERE user_id = ? AND status = 'WAITING') AS waiting, "
                    + "(SELECT COUNT(*) FROM reservations WHERE user_id = ? AND status = 'READY') AS ready, "
                    + "(SELECT COUNT(*) FROM favorites WHERE user_id = ?) AS favorites", me, me, me, me, me, me, me);
            String own = " AND b.id NOT IN (SELECT x.book_id FROM loans l2 JOIN copies x ON x.id = l2.copy_id WHERE l2.user_id = ?)";
            List<Map<String, Object>> rec = Db.rows(BOOK_SELECT + " WHERE b.category_id IN (SELECT b2.category_id FROM loans l3 JOIN copies c3 ON c3.id = l3.copy_id "
                    + "JOIN books b2 ON b2.id = c3.book_id WHERE l3.user_id = ?)" + own + " ORDER BY s.avg_rating DESC, s.times_borrowed DESC LIMIT 6", me, me);
            if (rec.size() < 6) {
                List<Map<String, Object>> more = Db.rows(BOOK_SELECT + " WHERE 1=1" + own + " ORDER BY s.times_borrowed DESC, s.avg_rating DESC LIMIT 12", me);
                Set<Object> have = new HashSet<>();
                for (Map<String, Object> r : rec) have.add(r.get("id"));
                for (Map<String, Object> r : more) if (rec.size() < 6 && have.add(r.get("id"))) rec.add(r);
            }
            return obj("stats", s, "recommended", rec,
                    "trending", Db.rows(BOOK_SELECT + " WHERE b.id IN (SELECT id FROM (SELECT cp.book_id AS id FROM loans l JOIN copies cp ON cp.id = l.copy_id "
                            + "WHERE l.issued_at >= ? GROUP BY cp.book_id ORDER BY COUNT(*) DESC LIMIT 6) t)", LocalDateTime.now().minusDays(45)));
        });

        get("/api/export/{what}", STAFF, c -> {
            switch (c.path.get("what")) {
                case "loans": return csv("loans.csv", Db.rows("SELECT l.id, b.title, cp.barcode, u.name AS member, u.card_no, l.issued_at, l.due_at, l.returned_at, l.renewals "
                        + "FROM loans l JOIN copies cp ON cp.id = l.copy_id JOIN books b ON b.id = cp.book_id JOIN users u ON u.id = l.user_id ORDER BY l.id DESC"));
                case "fines": return csv("fines.csv", Db.rows("SELECT f.id, u.name AS member, u.card_no, b.title, f.reason, f.amount, f.status, f.created_at, f.paid_at, f.method "
                        + "FROM fines f JOIN loans l ON l.id = f.loan_id JOIN copies cp ON cp.id = l.copy_id JOIN books b ON b.id = cp.book_id JOIN users u ON u.id = f.user_id ORDER BY f.id DESC"));
                case "books": return csv("books.csv", Db.rows(BOOK_SELECT + " ORDER BY b.title"));
                case "members": return csv("members.csv", Db.rows("SELECT u.id, u.name, u.email, u.card_no, u.role, u.status, p.name AS plan, u.joined_on, u.expires_on FROM users u JOIN plans p ON p.id = u.plan_id ORDER BY u.name"));
                default: throw new ApiException(404, "Unknown export");
            }
        });

        get("/api/audit", STAFF, c -> Db.rows("SELECT a.id, a.ts_ms, a.action, a.detail, a.hash, u.name AS actor FROM audit_log a LEFT JOIN users u ON u.id = a.actor_id "
                + "ORDER BY a.id DESC LIMIT " + c.qint("limit", 100, 1, 500)));
        get("/api/audit/verify", STAFF, c -> Audit.verify());
    }
}
