package lms;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/** Tiny JDBC helper: a connection pool, row-maps for JSON, and a transaction wrapper. */
final class Db {
    interface Work<T> { T run(Connection c) throws Exception; }

    private static final BlockingQueue<Connection> POOL = new ArrayBlockingQueue<>(12);
    private static String url, user, pass;
    static final DateTimeFormatter TS = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    static void init(String jdbcUrl, String u, String p, int size) throws SQLException {
        url = jdbcUrl; user = u; pass = p;
        for (int k = 0; k < size; k++) POOL.add(open());
    }

    private static Connection open() throws SQLException { return DriverManager.getConnection(url, user, pass); }

    private static Connection take() throws SQLException {
        try {
            Connection c = POOL.poll(10, TimeUnit.SECONDS);
            if (c == null) throw new SQLException("Database busy (connection pool exhausted)");
            if (c.isClosed() || !c.isValid(2)) {
                try { c.close(); } catch (SQLException ignored) { }
                c = open();
            }
            return c;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException(e);
        }
    }

    private static void give(Connection c) { POOL.offer(c); }

    static <T> T with(Work<T> w) throws Exception {
        Connection c = take();
        try { return w.run(c); } finally { give(c); }
    }

    /** Runs w in one transaction. Audit entries queued inside are written after commit. */
    static <T> T tx(Work<T> w) throws Exception {
        Connection c = take();
        T r;
        try {
            c.setAutoCommit(false);
            r = w.run(c);
            c.commit();
        } catch (Exception e) {
            try { c.rollback(); } catch (SQLException ignored) { }
            Audit.clear();
            throw e;
        } finally {
            try { c.setAutoCommit(true); } catch (SQLException ignored) { }
            give(c);
        }
        Audit.flush();
        return r;
    }

    // ---------- helpers without an explicit connection ----------
    static List<Map<String, Object>> rows(String sql, Object... a) throws Exception { return with(c -> rows(c, sql, a)); }
    static Map<String, Object> one(String sql, Object... a) throws Exception { return with(c -> one(c, sql, a)); }
    static long num(String sql, Object... a) throws Exception { return with(c -> num(c, sql, a)); }
    static int update(String sql, Object... a) throws Exception { return with(c -> update(c, sql, a)); }
    static long insert(String sql, Object... a) throws Exception { return with(c -> insert(c, sql, a)); }

    // ---------- helpers on a connection ----------
    private static void bind(PreparedStatement ps, Object[] a) throws SQLException {
        for (int k = 0; k < a.length; k++) ps.setObject(k + 1, a[k]);
    }

    static List<Map<String, Object>> rows(Connection c, String sql, Object... a) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, a);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int n = md.getColumnCount();
                List<Map<String, Object>> out = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    for (int k = 1; k <= n; k++) m.put(md.getColumnLabel(k), norm(rs.getObject(k)));
                    out.add(m);
                }
                return out;
            }
        }
    }

    static Map<String, Object> one(Connection c, String sql, Object... a) throws SQLException {
        List<Map<String, Object>> r = rows(c, sql, a);
        return r.isEmpty() ? null : r.get(0);
    }

    static long num(Connection c, String sql, Object... a) throws SQLException {
        Map<String, Object> r = one(c, sql, a);
        if (r == null) return 0;
        Object v = r.values().iterator().next();
        return v instanceof Number ? ((Number) v).longValue() : 0;
    }

    static int update(Connection c, String sql, Object... a) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, a);
            return ps.executeUpdate();
        }
    }

    static long insert(Connection c, String sql, Object... a) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, a);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) { return rs.next() ? rs.getLong(1) : 0; }
        }
    }

    /** Makes a JDBC value JSON-friendly. */
    static Object norm(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDateTime) return ((LocalDateTime) v).format(TS);
        if (v instanceof Timestamp) return ((Timestamp) v).toLocalDateTime().format(TS);
        if (v instanceof java.sql.Date) return ((java.sql.Date) v).toLocalDate().toString();
        if (v instanceof LocalDate) return v.toString();
        if (v instanceof BigDecimal) return ((BigDecimal) v).doubleValue();
        if (v instanceof BigInteger) return ((BigInteger) v).longValue();
        if (v instanceof Integer || v instanceof Short || v instanceof Byte) return ((Number) v).longValue();
        return v;
    }
}
