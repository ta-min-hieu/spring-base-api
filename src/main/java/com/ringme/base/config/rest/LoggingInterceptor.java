package com.ringme.base.config.rest;

import com.ringme.base.utils.LogMasker;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Log4j2
public class LoggingInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request,
                                        byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        // Đường nóng (prod): KHÔNG log => không buffer body => giữ streaming, tránh áp lực memory/GC khi tải cao.
        if (!log.isDebugEnabled()) {
            return execution.execute(request, body);
        }

        // Chỉ khi bật DEBUG mới log; header và body được mask để không lộ Authorization/token/password.
        log.debug("HTTP Request => Method: {}, URI: {}, Headers: {}, Body: {}",
                request.getMethod(), request.getURI(), maskHeaders(request.getHeaders()),
                LogMasker.maskJsonBody(new String(body, StandardCharsets.UTF_8)));

        ClientHttpResponse response = execution.execute(request, body);

        byte[] responseBodyBytes = response.getBody().readAllBytes();
        log.debug("HTTP Response <= Status: {}, StatusText: {}, Headers: {}, Body: {}",
                response.getStatusCode(), response.getStatusText(), maskHeaders(response.getHeaders()),
                LogMasker.maskJsonBody(new String(responseBodyBytes, StandardCharsets.UTF_8)));

        return new BufferingClientHttpResponseWrapper(response, responseBodyBytes);
    }

    private String maskHeaders(HttpHeaders headers) {
        // Spring Framework 7: HttpHeaders không còn implement Map nên dùng headerSet() thay cho entrySet().
        return headers.headerSet().stream()
                .map(e -> e.getKey() + "="
                        + (LogMasker.isSensitiveHeader(e.getKey()) ? LogMasker.MASK : e.getValue()))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    private record BufferingClientHttpResponseWrapper(ClientHttpResponse original,
                                                      byte[] body) implements ClientHttpResponse {

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return original.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return original.getStatusText();
        }

        @Override
        public void close() {
            original.close();
        }

        @Override
        public HttpHeaders getHeaders() {
            return original.getHeaders();
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }
    }
}
