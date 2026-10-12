package se.blug.forum.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", ex.getReason() == null ? "Request rejected" : ex.getReason()));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> invalid(Exception ex) { return ResponseEntity.badRequest().body(Map.of("message", "Invalid request")); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> conflict(Exception ex) { return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "Conflicting data")); }
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> unexpected(RuntimeException ex) {
        org.slf4j.LoggerFactory.getLogger(getClass()).error("Unexpected server error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Internal server error"));
    }
}
