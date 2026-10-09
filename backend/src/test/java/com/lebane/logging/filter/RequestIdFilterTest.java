package com.lebane.logging.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.lebane.logging.RequestContext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void reusesValidIncomingRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/departamentos");
        request.addHeader(RequestContext.REQUEST_ID_HEADER, "req-123_abc.DEF:9");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringChain = new AtomicReference<>();

        filter.doFilter(request, response, captureMdc(mdcDuringChain));

        assertThat(mdcDuringChain.get()).isEqualTo("req-123_abc.DEF:9");
        assertThat(response.getHeader(RequestContext.REQUEST_ID_HEADER)).isEqualTo("req-123_abc.DEF:9");
        assertThat(request.getAttribute(RequestContext.REQUEST_ID_ATTRIBUTE)).isEqualTo("req-123_abc.DEF:9");
    }

    @Test
    void generatesRequestIdWhenMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/departamentos");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringChain = new AtomicReference<>();

        filter.doFilter(request, response, captureMdc(mdcDuringChain));

        String generated = response.getHeader(RequestContext.REQUEST_ID_HEADER);
        assertThat(generated).isNotBlank().matches("[0-9a-f\\-]{36}");
        assertThat(mdcDuringChain.get()).isEqualTo(generated);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "abc def", "evil\r\nINJECTED", "<script>", "a/b"})
    void replacesInvalidRequestId(String invalid) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.addHeader(RequestContext.REQUEST_ID_HEADER, invalid);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader(RequestContext.REQUEST_ID_HEADER)).isNotEqualTo(invalid).hasSize(36);
    }

    @Test
    void replacesTooLongRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.addHeader(RequestContext.REQUEST_ID_HEADER, "a".repeat(129));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader(RequestContext.REQUEST_ID_HEADER)).hasSize(36);
    }

    @Test
    void clearsMdcAfterRequest() throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/x"), new MockHttpServletResponse(), (req, res) -> { });

        assertThat(MDC.get(RequestContext.REQUEST_ID_MDC_KEY)).isNull();
    }

    @Test
    void clearsMdcEvenWhenChainFails() {
        FilterChain failing = (req, res) -> {
            throw new ServletException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest("GET", "/x"),
                new MockHttpServletResponse(), failing)).isInstanceOf(ServletException.class);
        assertThat(MDC.get(RequestContext.REQUEST_ID_MDC_KEY)).isNull();
    }

    private static FilterChain captureMdc(AtomicReference<String> target) {
        return (req, res) -> target.set(MDC.get(RequestContext.REQUEST_ID_MDC_KEY));
    }
}
