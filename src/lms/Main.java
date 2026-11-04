package lms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class Main {
    static String env(String k, String def) {
        String v = System.getenv(k);
        return v == null || v.isEmpty() ? def : v;
    }

    public static void main(String[] args) throws Exception {
        Path home = Paths.get(env("LMS_HOME", "."));
        String host = env("LMS_DB_HOST", "127.0.0.1"), port = env("LMS_DB_PORT", "3306");
        String user = env("LMS_DB_USER", "root"), pass = env("LMS_DB_PASSWORD", ""), name = env("LMS_DB_NAME", "library_ms");
        if (!name.matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("LMS_DB_NAME may only contain letters, digits and _");
        String opts = "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8&connectionCollation=utf8mb4_unicode_ci";
        String base = "jdbc:mysql://" + host + ":" + port + "/";

        try (Connection c = DriverManager.getConnection(base + opts, user, pass); Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE IF NOT EXISTS `" + name + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        Db.init(base + name + opts, user, pass, 8);

        if (Db.num("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'users'") == 0) {
            System.out.println("Creating schema ...");
            runSchema(home.resolve("sql").resolve("schema.sql"), base + name + opts, user, pass);
        }
        if (Db.num("SELECT COUNT(*) FROM users") == 0 && !"0".equals(env("LMS_SEED", "1"))) {
            System.out.println("Loading demo data ...");
            Seed.run();
        }

        Api.register();
        String bind = env("LMS_HOST", "127.0.0.1");
        int p = Integer.parseInt(env("LMS_PORT", "8080"));
        Http.start(bind, p, home.resolve("web"));

        ScheduledExecutorService jobs = Executors.newSingleThreadScheduledExecutor();
        jobs.scheduleWithFixedDelay(() -> {
            try { Circulation.maintenance(); } catch (Exception e) { System.err.println("maintenance failed: " + e); }
        }, 2, 300, TimeUnit.SECONDS);

        if (!bind.equals("127.0.0.1") && !bind.equals("localhost")) {
            Map<String, Object> a = Db.one("SELECT password_hash FROM users WHERE email = 'admin@library.test'");
            if (a != null && Auth.verify(Seed.DEMO_PASSWORD, (String) a.get("password_hash")))
                System.out.println("WARNING: the demo admin still has the documented demo password and this server is reachable from your network. Change it (Profile > Change password) or start with LMS_SEED=0 on a fresh database.");
        }
        System.out.println("Library is up: http://localhost:" + p + (bind.equals("127.0.0.1") ? "  (this computer only; set LMS_HOST=0.0.0.0 to share on your network)" : ""));
    }

    /** Statements are separated by a line holding only "-- @@". */
    static void runSchema(Path file, String url, String user, String pass) throws Exception {
        String sql = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        try (Connection c = DriverManager.getConnection(url, user, pass); Statement st = c.createStatement()) {
            for (String part : sql.split("(?m)^-- @@\\s*$")) {
                StringBuilder b = new StringBuilder();
                for (String line : part.split("\\R")) if (!line.trim().startsWith("--")) b.append(line).append('\n');
                String stmt = b.toString().trim();
                if (!stmt.isEmpty()) st.execute(stmt);
            }
        }
    }
}
