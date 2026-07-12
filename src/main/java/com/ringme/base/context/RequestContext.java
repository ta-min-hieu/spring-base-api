package com.ringme.base.context;

import tools.jackson.databind.json.JsonMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
public final class RequestContext {
    public static final String REQUEST_ID = "X-Request-ID";
    public static final String LANGUAGE = "Accept-Language";
    public static final String MSISDN = "X-Msisdn";
    public static final String DEVICE_ID = "X-Device-Id";
    public static final String ROLE = "X-Role";
    public static final String USER_AGENT = "User-Agent";
    public static final String CLIENT_TYPE = "Client-Type";
    public static final String REVISION = "Revision";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Builder.Default
    private String language = "en";
    private String msisdn;
    private String deviceId;
    private String userAgent;
    private String requestId;
    private String role;
    private String clientType;
    private String revision;
    @Builder.Default
    private long timestamp = System.currentTimeMillis();

    @Override
    public String toString() {
        try {
            return JSON_MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            return super.toString();
        }
    }
}