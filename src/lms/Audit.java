package lms;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Tamper-evident audit log: every entry stores the SHA-256 of (previous hash + its own fields),
 * so editing or deleting any past row breaks the chain from that point on.
 * Entries are queued during a transaction and written after it commits.
 */
final class Audit {
    private static final String GENESIS = "0000000000000000000000000000000000000000000000000000000000000000";
    private static final ThreadLocal<List<Object[]>> PENDING = new ThreadLocal<List<Object[]>>() {
        @Override protected List<Object[]> initialValue() { return new ArrayList<>(); }
    };

    static void log(Number actor, String action, String detail) {
        PENDING.get().add(new Object[]{actor, action, detail});
    }

    /** For code that runs outside Db.tx. */
    static void now(Number actor, String action, String detail) { log(actor, action, detail); flush(); }

    static void clear() { PENDING.get().clear(); }

    static void flush() {
        List<Object[]> l = PENDING.get();
        List<Object[]> todo = new ArrayList<>(l);
        l.clear();
        for (Object[] e : todo) {
            try { write((Number) e[0], (String) e[1], (String) e[2]); }
            catch (Exception ex) { System.err.println("audit write failed: " + ex); }
        }
    }

    static String hash(String prev, long ts, Object actor, String action, String detail) {
        return sha256(prev + "|" + ts + "|" + (actor == null ? "" : actor) + "|" + action + "|" + detail);
    }

    private static synchronized void write(Number actor, String action, String detail) throws Exception {
        final String d = detail.length() > 500 ? detail.substring(0, 500) : detail;
        Db.with(c -> {
            Map<String, Object> last = Db.one(c, "SELECT hash FROM audit_log ORDER BY id DESC LIMIT 1");
            String prev = last == null ? GENESIS : (String) last.get("hash");
            long ts = System.currentTimeMillis();
            Long a = actor == null ? null : actor.longValue();
            Db.insert(c, "INSERT INTO audit_log (ts_ms, actor_id, action, detail, prev_hash, hash) VALUES (?,?,?,?,?,?)",
                    ts, a, action, d, prev, hash(prev, ts, a, action, d));
            return null;
        });
    }

    static Map<String, Object> verify() throws Exception {
        List<Map<String, Object>> rows = Db.rows("SELECT id, ts_ms, actor_id, action, detail, prev_hash, hash FROM audit_log ORDER BY id");
        String prev = GENESIS;
        long checked = 0;
        for (Map<String, Object> r : rows) {
            Object actor = r.get("actor_id");
            String expect = hash(prev, ((Number) r.get("ts_ms")).longValue(), actor, (String) r.get("action"), (String) r.get("detail"));
            if (!prev.equals(r.get("prev_hash")) || !expect.equals(r.get("hash"))) {
                return Json.obj("ok", false, "checked", checked, "brokenAt", r.get("id"));
            }
            prev = (String) r.get("hash");
            checked++;
        }
        return Json.obj("ok", true, "checked", checked, "brokenAt", null);
    }

    static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
