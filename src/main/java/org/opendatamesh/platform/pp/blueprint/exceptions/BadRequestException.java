package org.opendatamesh.platform.pp.blueprint.exceptions;

import org.springframework.http.HttpStatus;

public class BadRequestException extends BlueprintApiException {
    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
