package org.opendatamesh.platform.service.template.exceptions;

import org.springframework.http.HttpStatus;

public class NotImplemented extends ServiceTemplateApiException {
    public NotImplemented(String message) {
        super(message);
    }

    public NotImplemented(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_IMPLEMENTED;
    }
}
