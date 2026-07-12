package com.ringme.base.filter;

import tools.jackson.databind.json.JsonMapper;
import com.ringme.base.context.RequestContext;
import com.ringme.base.context.RequestContextHolder;
import com.ringme.base.utils.LogMasker;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

@Component("customRequestContextFilter")
@Order(Ordered.HIGHEST_PRECEDENCE)
@Log4j2
public class RequestContextFilter extends OncePerRequestFilter {

    private static final Logger logReq = LogManager.getLogger("request");
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    // Spring Framework 7 bỏ constructor không giới hạn của ContentCachingRequestWrapper; phải truyền hạn mức
    // cache body. Log vốn cắt còn 4KB nên 64KB là dư để hiển thị, đồng thời chặn rủi ro ngốn bộ nhớ khi body lớn.
    private static final int REQUEST_CONTENT_CACHE_LIMIT = 64 * 1024;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        ContentCachingRequestWrapper req =
                new ContentCachingRequestWrapper(request, REQUEST_CONTENT_CACHE_LIMIT);
        ContentCachingResponseWrapper res =
                new ContentCachingResponseWrapper(response);

        try {
            // Tạo uuid cho log để dễ debug lifecycle request
            String requestId = request.getHeader(RequestContext.REQUEST_ID);
            requestId = requestId != null ? requestId :
                    UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            ThreadContext.put("contextId", requestId);

            String languageCode = request.getHeader(RequestContext.LANGUAGE);
            String msisdn = request.getHeader(RequestContext.MSISDN);
            String deviceId = request.getHeader(RequestContext.DEVICE_ID);
            String userAgent = request.getHeader(RequestContext.USER_AGENT);
            String clientType = request.getHeader(RequestContext.CLIENT_TYPE);
            String role = request.getHeader(RequestContext.ROLE);
            String revision = request.getHeader(RequestContext.REVISION);

            RequestContext requestContext = RequestContext.builder()
                    .requestId(requestId)
                    .language((languageCode != null && !languageCode.isBlank()) ? languageCode : "en")
                    .msisdn(msisdn)
                    .deviceId(deviceId)
                    .userAgent(userAgent)
                    .clientType(clientType)
                    .role(role)
                    .revision(revision)
                    .build();

            RequestContextHolder.set(requestContext);

            filterChain.doFilter(req, res);
        } finally {
            logInfoRequest(req, res);
            res.copyBodyToResponse();

            ThreadContext.clearMap();
            RequestContextHolder.clear();
        }
    }

    private String extractHeaders(HttpServletRequest request) {
        try {
            return JSON_MAPPER.writeValueAsString(Collections.list(request.getHeaderNames())
                    .stream()
                    .collect(Collectors.toMap(
                            h -> h,
                            h -> LogMasker.maskHeaderValue(h, request.getHeader(h)),
                            (a, b) -> b,
                            LinkedHashMap::new
                    )));
        } catch (Exception e) {
            return null;
        }
    }

    private String extractHeaders(HttpServletResponse request) {
        try {
            return JSON_MAPPER.writeValueAsString(request.getHeaderNames()
                    .stream()
                    .collect(Collectors.toMap(
                            h -> h,
                            h -> LogMasker.maskHeaderValue(h, request.getHeader(h)),
                            (a, b) -> b,
                            LinkedHashMap::new
                    )));
        } catch (Exception e) {
            return null;
        }
    }

    private void logInfoRequest(ContentCachingRequestWrapper req,
                                ContentCachingResponseWrapper res) {

        String uri = req.getRequestURI();
        String query = req.getQueryString();

        String requestBody = safeBody(req.getContentAsByteArray(), 4 * 1024); // 4KB
        String responseBody = safeBody(res.getContentAsByteArray(), 4 * 1024); // 4KB

        logReq.info(
                "URI: {} | QUERY: {} | HEADER_REQ: {} | REQ: {} | HEADER_RESP: {} | RESP: {} | Exe: {} ms",
                uri,
                query,
                extractHeaders(req),
                requestBody,
                extractHeaders(res),
                responseBody,
                System.currentTimeMillis() - RequestContextHolder.getContext().getTimestamp()
        );
        logReq.info("------------------------------");
    }

    private String safeBody(byte[] bytes, int max) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        // Mask field nhạy cảm (password/token/...) trên body đầy đủ TRƯỚC khi cắt ngắn,
        // tránh trường hợp body bị cắt làm JSON không parse được rồi lọt dữ liệu nhạy cảm.
        String masked = LogMasker.maskJsonBody(new String(bytes, UTF_8));
        if (masked.length() > max) {
            return masked.substring(0, max) + "...(truncated)";
        }
        return masked;
    }
}

