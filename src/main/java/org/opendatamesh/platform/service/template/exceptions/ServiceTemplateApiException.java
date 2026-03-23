package org.opendatamesh.platform.service.template.exceptions;

import org.springframework.http.HttpStatus;

public abstract class ServiceTemplateApiException extends RuntimeException{

	/**
	 * 
	 */
	private static final long serialVersionUID = 3876573329263306459L;	
	
	public ServiceTemplateApiException() {
		super();
	}

	public ServiceTemplateApiException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public ServiceTemplateApiException(String message, Throwable cause) {
		super(message, cause);
	}

	public ServiceTemplateApiException(String message) {
		super(message);
	}

	public ServiceTemplateApiException(Throwable cause) {
		super(cause);
	}

	/**
	 * @return the errorName
	 */
	public String getErrorName() {
		return getClass().getSimpleName();	
	}

	/**
	 * @return the status
	 */
	public abstract HttpStatus getStatus();	
	

}