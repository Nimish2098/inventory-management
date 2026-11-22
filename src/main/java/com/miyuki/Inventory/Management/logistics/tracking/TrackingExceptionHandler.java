package com.miyuki.Inventory.Management.logistics.tracking;

import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Redis being down is a temporary 503 (clients should buffer and retry), not a 500 bug. */
@RestControllerAdvice(assignableTypes = {TrackingIngestController.class, TrackingQueryController.class})
public class TrackingExceptionHandler {

    @ExceptionHandler({RedisConnectionFailureException.class, QueryTimeoutException.class})
    public ResponseEntity<ProblemDetail> redisUnavailable(RuntimeException ex) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "Tracking pipeline is temporarily unavailable. Please retry.");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "5")
                .body(body);
    }
}
