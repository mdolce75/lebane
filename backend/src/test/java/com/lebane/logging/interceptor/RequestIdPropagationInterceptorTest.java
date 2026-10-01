package com.lebane.logging.interceptor;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import com.lebane.logging.RequestContext;

class RequestIdPropagationInterceptorTest {

    private final RequestIdPropagationInterceptor interceptor = new RequestIdPropagationInterceptor();
    private final ClientHttpRequestExecution execution =
            (request, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void propagatesRequestIdFromMdc() throws Exception {
        MDC.put(RequestContext.REQUEST_ID_MDC_KEY, "req-777");
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://provider/x"));

        interceptor.intercept(request, new byte[0], execution);

        assertThat(request.getHeaders().getFirst(RequestContext.REQUEST_ID_HEADER)).isEqualTo("req-777");
    }

    @Test
    void doesNotOverrideExplicitHeader() throws Exception {
        MDC.put(RequestContext.REQUEST_ID_MDC_KEY, "req-777");
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://provider/x"));
        request.getHeaders().set(RequestContext.REQUEST_ID_HEADER, "explicit");

        interceptor.intercept(request, new byte[0], execution);

        assertThat(request.getHeaders().getFirst(RequestContext.REQUEST_ID_HEADER)).isEqualTo("explicit");
    }

    @Test
    void addsNothingOutsideARequest() throws Exception {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://provider/x"));

        interceptor.intercept(request, new byte[0], execution);

        assertThat(request.getHeaders().containsKey(RequestContext.REQUEST_ID_HEADER)).isFalse();
    }
}
