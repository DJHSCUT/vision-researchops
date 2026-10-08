package com.djh.researchops.util;

import org.slf4j.MDC;
import java.util.UUID;

/** 同步 ChatClient 请求范围；结束时恢复调用线程原有 MDC，避免请求间串号。 */
public final class AiRequestLogContext implements AutoCloseable {
    public static final String REQUEST_ID = "agentRequestId";
    private final String previous = MDC.get(REQUEST_ID);

    private AiRequestLogContext() {
        MDC.put(REQUEST_ID, UUID.randomUUID().toString());
    }

    public static AiRequestLogContext open() { return new AiRequestLogContext(); }

    @Override
    public void close() {
        if (previous == null) MDC.remove(REQUEST_ID);
        else MDC.put(REQUEST_ID, previous);
    }
}
