package org.opendatamesh.platform.pp.blueprint.utils.client;


import java.util.Map;

import org.opendatamesh.platform.pp.blueprint.exceptions.client.ClientException;
import org.opendatamesh.platform.pp.blueprint.utils.client.http.HttpEntity;
import org.opendatamesh.platform.pp.blueprint.utils.client.http.HttpMethod;

interface RestUtilsTemplate {

    <T> T exchange(String url, HttpMethod method, HttpEntity<?> requestEntity, Class<T> responseType, Object... uriVariables) throws ClientException;

    <T> T exchange(String url, HttpMethod method, HttpEntity<?> requestEntity, Class<T> responseType, Map<String, ?> uriVariables) throws ClientException;

}
