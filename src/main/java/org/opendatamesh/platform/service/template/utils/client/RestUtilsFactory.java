package org.opendatamesh.platform.service.template.utils.client;

import org.springframework.web.client.RestTemplate;

public abstract class RestUtilsFactory {

    private RestUtilsFactory() {
        // Prevent instantiation
    }

    public static RestUtils getRestUtils(RestTemplate restTemplate) {
        return RestTemplateWrapper.wrap(restTemplate).build();
    }
}
