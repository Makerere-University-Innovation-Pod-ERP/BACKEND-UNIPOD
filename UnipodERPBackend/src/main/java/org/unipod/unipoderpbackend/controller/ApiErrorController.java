package org.unipod.unipoderpbackend.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.exception.ProblemCodes;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Fallback error endpoint for failures raised outside the MVC exception handlers
 * (for example container-level errors). It always returns the same RFC 9457
 * problem envelope and never exposes a stack trace.
 */
@RestController
public class ApiErrorController implements ErrorController {
    private static final Logger log = LoggerFactory.getLogger(ApiErrorController.class);
    private static final Pattern SAFE_REFERENCE = Pattern.compile("[A-Za-z0-9-]{1,64}");

    @RequestMapping("/error")
    public ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
        HttpStatus status = resolveStatus(request);
        String detail = switch (status.value()) {
            case 400 -> "The request could not be processed.";
            case 401 -> "A valid bearer token is required.";
            case 403 -> "You do not have permission to perform this action.";
            case 404 -> "The requested resource was not found.";
            case 405 -> "The HTTP method is not supported for this resource.";
            case 415 -> "The request Content-Type is not supported.";
            default -> "The request could not be completed.";
        };

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", ProblemCodes.forStatus(status));

        if (status.is5xxServerError()) {
            String reference = reference(request);
            Throwable failure = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
            log.error(
                    "Unhandled container error [reference={}] for {} {}",
                    reference,
                    request.getMethod(),
                    request.getRequestURI(),
                    failure
            );
            problem.setDetail(
                    "The server could not complete the request. Quote reference %s when contacting support."
                            .formatted(reference)
            );
            problem.setProperty("reference", reference);
        }

        ResponseEntity.BodyBuilder response = ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
            response.header("WWW-Authenticate", "Bearer");
        }
        return response.body(problem);
    }

    private static HttpStatus resolveStatus(HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (attribute instanceof Integer statusCode) {
            HttpStatus status = HttpStatus.resolve(statusCode);
            if (status != null) {
                return status;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static String reference(HttpServletRequest request) {
        String header = request.getHeader("X-Request-Id");
        if (header != null && SAFE_REFERENCE.matcher(header).matches()) {
            return header;
        }
        return UUID.randomUUID().toString();
    }
}
