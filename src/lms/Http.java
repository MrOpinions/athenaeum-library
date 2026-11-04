package lms;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Executors;

/** Router + static file server on the JDK's built-in HttpServer. */
final class Http {
    enum Access { PUBLIC, ANY, STAFF, ADMIN }

    static final class ApiException extends RuntimeException {
        final int status;
        ApiException(int status, String msg) { super(msg); this.status = status; }
    }

    /** Non-JSON response (CSV downloads). */
    static final class Raw {
        final byte[] bytes; final String type; final String filename;
        Raw(byte[] bytes, String type, String filename) { this.bytes = bytes; this.type = type; this.filename = filename; }
    }

    static final class Ctx {
        final Map<String, String> path = new HashMap<>();
        final Map<String, String> query = new HashMap<>();
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> user;
        String token;

        long uid() { return ((Number) user.get("id")).longValue(); }
        boolean staff() { return user != null && !"MEMBER".equals(user.get("role")); }
        long pid(String k) {
            try { return Long.parseLong(path.get(k)); } catch (Exception e) { throw new ApiException(400, "Bad id"); }
        }
        String str(String k) {
            Object v = body.get(k);
            if (v == null) return null;
            String s = v.toString().trim();
            return s.isEmpty() ? null : s;
        }
        String req(String k) {
            String s = str(k);
            if (s == null) throw new ApiException(400, k.replace('_', ' ') + " is required");
            return s;
        }
        Long lng(String k) {
            Object v = body.get(k);
            if (v == null || v.toString().trim().isEmpty()) return null;
            try { return v instanceof Number ? ((Number) v).longValue() : Long.parseLong(v.toString().trim()); }
            catch (NumberFormatException e) { throw new ApiException(400, k + " must be a number"); }
        }
        Double dbl(String k) {
            Object v = body.get(k);
            if (v == null || v.toString().trim().isEmpty()) return null;
            try { return v instanceof Number ? ((Number) v).doubleValue() : Double.parseDouble(v.toString().trim()); }
            catch (NumberFormatException e) { throw new ApiException(400, k + " must be a number"); }
        }
        String q(String k) { String v = query.get(k); return v == null || v.isEmpty() ? null : v; }
        int qint(String k, int def, int min, int max) {
            try { return Math.max(min, Math.min(max, Integer.parseInt(query.get(k)))); } catch (Exception e) { return def; }
        }
    }

    interface Handler { Object handle(Ctx c) throws Exception; }

    private static final class Route {
        final String method; final String[] segs; final Access access; final Handler h;
        Route(String m, String p, Access a, Handler h) { method = m; segs = split(p); access = a; this.h = h; }
    }

    private static final List<Route> ROUTES = new ArrayList<>();
    private static Path webRoot;

    static void route(String method, String pattern, Access a, Handler h) { ROUTES.add(new Route(method, pattern, a, h)); }
    static void get(String p, Access a, Handler h)    { route("GET", p, a, h); }
    static void post(String p, Access a, Handler h)   { route("POST", p, a, h); }
    static void put(String p, Access a, Handler h)    { route("PUT", p, a, h); }
    static void delete(String p, Access a, Handler h) { route("DELETE", p, a, h); }

    private static String[] split(String p) {
        List<String> out = new ArrayList<>();
        for (String s : p.split("/")) if (!s.isEmpty()) out.add(s);
        return out.toArray(new String[0]);
    }

    static HttpServer start(String host, int port, Path web) throws IOException {
        webRoot = web.toAbsolutePath().normalize();
        HttpServer s = HttpServer.create(new InetSocketAddress(host, port), 0);
        s.createContext("/", Http::dispatch);
        s.setExecutor(Executors.newFixedThreadPool(16));
        s.start();
        return s;
    }

    private static void dispatch(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if (path.startsWith("/api/")) api(ex, path); else staticFile(ex, path);
        } catch (Throwable t) {
            t.printStackTrace();
            try { send(ex, 500, "application/json", Json.write(Json.obj("error", "Server error")).getBytes(StandardCharsets.UTF_8), null); }
            catch (IOException ignored) { }
        } finally {
            ex.close();
        }
    }

    private static void api(HttpExchange ex, String path) throws IOException {
        Object result;
        int status = 200;
        try {
            String[] segs = split(path);
            Route hit = null; Ctx ctx = new Ctx();
            boolean pathMatched = false;
            for (Route r : ROUTES) {
                Map<String, String> params = match(r.segs, segs);
                if (params == null) continue;
                pathMatched = true;
                if (!r.method.equals(ex.getRequestMethod())) continue;
                hit = r; ctx.path.putAll(params); break;
            }
            if (hit == null) throw new ApiException(pathMatched ? 405 : 404, pathMatched ? "Method not allowed" : "Not found");

            String qs = ex.getRequestURI().getRawQuery();
            if (qs != null) for (String kv : qs.split("&")) {
                int eq = kv.indexOf('=');
                String k = dec(eq < 0 ? kv : kv.substring(0, eq));
                ctx.query.put(k, eq < 0 ? "" : dec(kv.substring(eq + 1)));
            }
            String auth = ex.getRequestHeaders().getFirst("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) ctx.token = auth.substring(7).trim();
            ctx.user = Auth.userFor(ctx.token);

            if (hit.access != Access.PUBLIC && ctx.user == null) throw new ApiException(401, "Please sign in");
            if (hit.access == Access.STAFF && !ctx.staff()) throw new ApiException(403, "Staff only");
            if (hit.access == Access.ADMIN && !"ADMIN".equals(ctx.user.get("role"))) throw new ApiException(403, "Admin only");

            String m = ex.getRequestMethod();
            if (m.equals("POST") || m.equals("PUT")) {
                byte[] raw = readAll(ex.getRequestBody(), 1_000_000);
                try { ctx.body = Json.parseObject(new String(raw, StandardCharsets.UTF_8)); }
                catch (RuntimeException e) { throw new ApiException(400, "Invalid JSON body"); }
            }
            result = hit.h.handle(ctx);
        } catch (ApiException e) {
            status = e.status; result = Json.obj("error", e.getMessage());
        } catch (SQLException e) {
            status = sqlStatus(e); result = Json.obj("error", sqlMessage(e));
            if (status == 500) e.printStackTrace();
        } catch (Exception e) {
            Throwable cause = e.getCause() instanceof SQLException ? e.getCause() : null;
            if (cause != null) { status = sqlStatus((SQLException) cause); result = Json.obj("error", sqlMessage((SQLException) cause)); }
            else { e.printStackTrace(); status = 500; result = Json.obj("error", "Server error"); }
        }
        if (result instanceof Raw) {
            Raw r = (Raw) result;
            send(ex, 200, r.type, r.bytes, r.filename);
        } else {
            send(ex, status, "application/json; charset=utf-8", Json.write(result).getBytes(StandardCharsets.UTF_8), null);
        }
    }

    private static int sqlStatus(SQLException e) {
        String st = e.getSQLState();
        if ("45000".equals(st)) return 400;                      // our triggers (SIGNAL)
        if (st != null && st.startsWith("23")) return 409;       // integrity constraint
        return 500;
    }

    private static String sqlMessage(SQLException e) {
        String st = e.getSQLState();
        if ("45000".equals(st)) return e.getMessage();
        if (st != null && st.startsWith("23")) {
            String m = e.getMessage() == null ? "" : e.getMessage();
            if (m.contains("Duplicate")) return "That value already exists";
            if (m.contains("foreign key")) return "Blocked: other records still depend on this";
            return "Rejected by a database constraint";
        }
        return "Server error";
    }

    private static Map<String, String> match(String[] pat, String[] segs) {
        if (pat.length != segs.length) return null;
        Map<String, String> m = new HashMap<>();
        for (int k = 0; k < pat.length; k++) {
            if (pat[k].startsWith("{")) m.put(pat[k].substring(1, pat[k].length() - 1), dec(segs[k]));
            else if (!pat[k].equals(segs[k])) return null;
        }
        return m;
    }

    private static String dec(String s) {
        try { return URLDecoder.decode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    private static byte[] readAll(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > max) throw new ApiException(413, "Request too large");
        }
        return out.toByteArray();
    }

    private static void send(HttpExchange ex, int status, String type, byte[] body, String filename) throws IOException {
        securityHeaders(ex);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        if (filename != null) ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        if ("HEAD".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(status, -1); return; }
        ex.sendResponseHeaders(status, body.length);
        ex.getResponseBody().write(body);
    }

    private static void securityHeaders(HttpExchange ex) {
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.getResponseHeaders().set("X-Frame-Options", "DENY");
        ex.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        ex.getResponseHeaders().set("Content-Security-Policy",
                "default-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
                + "font-src https://fonts.gstatic.com; img-src 'self' data:; script-src 'self'; frame-ancestors 'none'");
    }

    private static final Map<String, String> TYPES = new HashMap<>();
    static {
        TYPES.put("html", "text/html; charset=utf-8"); TYPES.put("css", "text/css; charset=utf-8");
        TYPES.put("js", "application/javascript; charset=utf-8"); TYPES.put("svg", "image/svg+xml");
        TYPES.put("png", "image/png"); TYPES.put("ico", "image/x-icon"); TYPES.put("json", "application/json");
    }

    private static void staticFile(HttpExchange ex, String path) throws IOException {
        if (!ex.getRequestMethod().equals("GET") && !ex.getRequestMethod().equals("HEAD")) {
            send(ex, 405, "text/plain", "Method not allowed".getBytes(StandardCharsets.UTF_8), null); return;
        }
        String rel = path.equals("/") ? "index.html" : path.substring(1);
        Path p = webRoot.resolve(rel).normalize();
        if (!p.startsWith(webRoot) || !Files.isRegularFile(p)) p = webRoot.resolve("index.html"); // SPA fallback
        String name = p.getFileName().toString();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
        String type = TYPES.containsKey(ext) ? TYPES.get(ext) : "application/octet-stream";
        send(ex, 200, type, Files.readAllBytes(p), null);
    }

    static Path path(String s) { return Paths.get(s); }
}
