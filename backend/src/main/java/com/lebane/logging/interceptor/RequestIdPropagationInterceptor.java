package com.lebane.logging.interceptor;

import java.io.IOException;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import com.lebane.logging.RequestContext;

/**
 * Propaga el {@code X-Request-Id} del request actual a las llamadas HTTP salientes (p. ej. proveedor de
 * direcciones). traceparent (W3C) lo propaga automáticamente la instrumentación de Micrometer sobre
 * RestClient.Builder.
 */
public class RequestIdPropagationInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (!request.getHeaders().containsKey(RequestContext.REQUEST_ID_HEADER)) {
            RequestContext.currentRequestId()
                    .ifPresent(id -> request.getHeaders().set(RequestContext.REQUEST_ID_HEADER, id));
        }
        return execution.execute(request, body);
    }
}
