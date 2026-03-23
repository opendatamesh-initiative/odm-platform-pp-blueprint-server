package org.opendatamesh.platform.service.template.exceptions;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ServiceTemplateApiException {
    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
