package com.miyuki.Inventory.Management.logistics.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps domain exceptions to RFC 7807 problem responses. The parent class already turns bean-validation
 * failures, malformed JSON and bad path types into 400s.
 * If you merged these handlers into your own @RestControllerAdvice, just add the optimistic-lock one.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail notFound(NotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail conflict(ConflictException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail badRequest(BadRequestException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Two people changed the same record at once (@Version check failed). The client should reload and retry. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail staleVersion(ObjectOptimisticLockingFailureException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "The record was changed by someone else. Reload it and try again.");
    }

    /** Last line of defence: unique/FK/check violations that slip past service-level checks (e.g. races). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integrity(DataIntegrityViolationException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Request conflicts with existing data or violates a constraint");
    }
}
