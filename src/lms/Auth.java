package lms;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Password hashing (PBKDF2-HMAC-SHA256, per-user salt), session tokens, and login throttling. */
final class Auth {
    private static final int ITER = 120_000;
    private static final SecureRandom RNG = new SecureRandom();
    static final int SESSION_HOURS = 12;
    private static final String DUMMY = hashPassword("not-a-real-password");

    private static final Map<String, long[]> FAILS = new ConcurrentHashMap<>(); // email -> {count, lockedUntilMs}

    static String hashPassword(String pw) {
        byte[] salt = new byte[16];
        RNG.nextBytes(salt);
        return "pbkdf2$" + ITER + "$" + b64(salt) + "$" + b64(pbkdf2(pw, salt, ITER));
    }

    static boolean verify(String pw, String stored) {
        try {
            String[] p = stored.split("\\$");
            if (p.length != 4 || !p[0].equals("pbkdf2")) return false;
            byte[] want = Base64.getDecoder().decode(p[3]);
            byte[] got = pbkdf2(pw, Base64.getDecoder().decode(p[2]), Integer.parseInt(p[1]));
            return MessageDigest.isEqual(want, got);
        } catch (Exception e) { return false; }
    }

    private static byte[] pbkdf2(String pw, byte[] salt, int iter) {
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(new PBEKeySpec(pw.toCharArray(), salt, iter, 256)).getEncoded();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static String b64(byte[] b) { return Base64.getEncoder().encodeToString(b); }

    /** Returns the user row on success, null on bad credentials; throws when throttled. */
    static Map<String, Object> login(String email, String pw) throws Exception {
        String key = email.toLowerCase();
        long[] f = FAILS.get(key);
        if (f != null && f[1] > System.currentTimeMillis())
            throw new Http.ApiException(429, "Too many failed attempts. Try again in a few minutes.");
        Map<String, Object> u = Db.one("SELECT * FROM users WHERE email = ?", key);
        boolean ok = verify(pw, u == null ? DUMMY : (String) u.get("password_hash")) && u != null;
        if (ok) { FAILS.remove(key); return u; }
        long[] n = f == null ? new long[]{0, 0} : f;
        n[0]++;
        if (n[0] >= 5) { n[1] = System.currentTimeMillis() + 5 * 60_000L; n[0] = 0; }
        FAILS.put(key, n);
        return null;
    }

    static String newSession(Connection c, long userId) throws Exception {
        byte[] t = new byte[32];
        RNG.nextBytes(t);
        StringBuilder tok = new StringBuilder();
        for (byte x : t) tok.append(String.format("%02x", x));
        Db.update(c, "INSERT INTO sessions (token_hash, user_id, expires_at) VALUES (?,?,?)",
                Audit.sha256(tok.toString()), userId, LocalDateTime.now().plusHours(SESSION_HOURS));
        return tok.toString();
    }

    /** The logged-in user for a bearer token, or null. Only the token's hash is stored server-side. */
    static Map<String, Object> userFor(String token) throws Exception {
        if (token == null || token.length() < 32) return null;
        return Db.one("SELECT u.id, u.email, u.name, u.role, u.plan_id, u.card_no, u.phone, u.joined_on, u.expires_on, u.status "
                + "FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.token_hash = ? AND s.expires_at > ?",
                Audit.sha256(token), LocalDateTime.now());
    }

    static void logout(String token) throws Exception {
        if (token != null) Db.update("DELETE FROM sessions WHERE token_hash = ?", Audit.sha256(token));
    }
}
