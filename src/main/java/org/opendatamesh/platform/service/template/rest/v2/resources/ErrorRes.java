package org.opendatamesh.platform.service.template.rest.v2.resources;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ErrorRes {
    @JsonProperty("status")
    private int status;

    @JsonProperty("title")
    private String title;

    @JsonProperty("detail")
    private String detail;

    @JsonProperty("instance")
    private String instance;

    public ErrorRes() {
    }

    public ErrorRes(int status, String title, String detail, String instance) {
        this.status = status;
        this.title = title;
        this.detail = detail;
        this.instance = instance;
    }

    public static ErrorRes of(int status, String title, String detail, String instance) {
        return new ErrorRes(status, title, detail, instance);
    }

    // Getters and Setters
    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }
}
