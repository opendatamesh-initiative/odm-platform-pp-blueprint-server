package org.opendatamesh.platform.pp.blueprint.exceptions;

import org.springframework.http.HttpStatus;

public abstract class BlueprintApiException extends RuntimeException{

	/**
	 * 
	 */
	private static final long serialVersionUID = 3876573329263306459L;	
	
	public BlueprintApiException() {
		super();
	}

	public BlueprintApiException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public BlueprintApiException(String message, Throwable cause) {
		super(message, cause);
	}

	public BlueprintApiException(String message) {
		super(message);
	}

	public BlueprintApiException(Throwable cause) {
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