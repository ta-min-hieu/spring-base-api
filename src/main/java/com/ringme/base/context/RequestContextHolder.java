package com.ringme.base.context;

import java.util.Optional;

public final class RequestContextHolder {

    private static final ThreadLocal<RequestContext> CONTEXT = new ThreadLocal<>();

    private RequestContextHolder() {
    }

    public static void set(RequestContext requestContext) {
        CONTEXT.set(requestContext);
    }

    public static Optional<RequestContext> getContextRaw() {
        return Optional.ofNullable(CONTEXT.get());
    }

    public static RequestContext getContext() {
        return getContextRaw().orElse(new RequestContext());
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
