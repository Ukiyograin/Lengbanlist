package org.leng.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JWT 鉴权管理器,从 WebServer 内嵌类抽离。
 */
public class AuthManager {

    private static final long TOKEN_EXP_MS = 86_400_000L;

    private final String secret;
    private final String username;
    private final String passwordHash;
    private final Set<String> revokedTokens = ConcurrentHashMap.newKeySet();

    public AuthManager(String secret, String username, String password) {
        this.secret = secret;
        this.username = username;
        this.passwordHash = sha256(password);
    }

    public String login(String user, String pass) {
        if (!username.equals(user) || !sha256(pass).equals(passwordHash)) return null;
        return createToken(username);
    }

    public boolean validateToken(String token) {
        if (token == null || revokedTokens.contains(token)) return false;
        return parseToken(token) != null;
    }

    public void revokeToken(String token) {
        if (token != null && !token.isEmpty()) {
            revokedTokens.add(token);
        }
    }

    public String getUsernameFromToken(String token) {
        JsonObject payload = parseToken(token);
        return payload != null ? payload.get("sub").getAsString() : null;
    }

    public String resolveActor(String token) {
        String name = getUsernameFromToken(token);
        if (name == null || name.trim().isEmpty()) {
            return "CONSOLE";
        }
        return "admin".equals(name) ? "CONSOLE" : name.trim();
    }

    private String createToken(String subject) {
        JsonObject header = new JsonObject();
        header.addProperty("alg", "HS256");
        header.addProperty("typ", "JWT");

        JsonObject payload = new JsonObject();
        long now = System.currentTimeMillis() / 1000;
        payload.addProperty("sub", subject);
        payload.addProperty("iat", now);
        payload.addProperty("exp", now + + TOKEN_EXP_MS / 1000);

        String encodedHeader = b64url(header.toString());
        String encodedPayload = b64url(payload.toString());
        String signingInput = encodedHeader + "." + encodedPayload;
        String signature = hmacSha256(signingInput, secret);

        return signingInput + "." + signature;
    }

    private JsonObject parseToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return null;
            if (!hmacSha256(parts[0] + "." + parts[1], secret).equals(parts[2])) return null;

            String json = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
            if (System.currentTimeMillis() / 1000 > payload.get("exp").getAsLong()) return null;
            return payload;
        } catch (Exception e) {
            return null;
        }
    }

    private static String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return b64url(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String sha256(String s) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String b64url(String data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String b64url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
}