package com.cancerweb.backend.exception;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.mongodb.MongoTimeoutException;

/** Turns exceptions into RFC 9457 problem responses with a readable {@code detail}. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail tooLarge(MaxUploadSizeExceededException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, "Image is larger than 50 MB");
    }

    @ExceptionHandler(PredictionNotFoundException.class)
    public ProblemDetail notFound(PredictionNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    // GridFS throws the raw driver exception, repositories throw Spring's translated one.
    @ExceptionHandler({ DataAccessResourceFailureException.class, MongoTimeoutException.class })
    public ProblemDetail databaseUnavailable(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Cannot reach MongoDB. Check MONGODB_URI in Backend/.env and your Atlas IP access list.");
    }

    @ExceptionHandler(MlServiceException.class)
    public ProblemDetail mlUnavailable(MlServiceException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }
}
