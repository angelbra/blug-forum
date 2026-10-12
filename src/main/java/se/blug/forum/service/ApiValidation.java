package se.blug.forum.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class ApiValidation {
    private ApiValidation() {}
    public static String required(String value, int max, String field) {
        if (value == null || value.isBlank() || value.length() > max)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + field);
        return value;
    }
    public static String optional(String value, int max, String field) {
        if (value != null && value.length() > max)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + field);
        return value;
    }
    public static String password(String value) {
        required(value, 72, "password");
        if (value.length() < 8 || value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must have at least 8 characters and at most 72 UTF-8 bytes");
        return value;
    }
    public static String email(String value) {
        required(value, 254, "email");
        if (!value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email");
        return value;
    }
}
