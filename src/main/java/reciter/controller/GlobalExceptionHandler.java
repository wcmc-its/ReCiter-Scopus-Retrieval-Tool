package reciter.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates request-handling failures into clean JSON responses.
 *
 * <p>Validation failures ({@link IllegalArgumentException} from
 * {@link ScopusController#validate}) and unreadable request bodies become HTTP 400 instead of
 * a 500 with a stack trace. Genuinely unexpected errors are left to Spring Boot's default
 * handler, which already returns a 500 without leaking the stack trace to the caller.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger slf4jLogger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception e) {
        String message = (e instanceof HttpMessageNotReadableException)
                ? "Request body is missing or not valid JSON."
                : e.getMessage();
        slf4jLogger.warn("Rejected Scopus request: {}", message);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", HttpStatus.BAD_REQUEST.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.badRequest().body(body);
    }
}
