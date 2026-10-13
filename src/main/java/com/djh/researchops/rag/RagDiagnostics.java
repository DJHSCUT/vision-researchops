package com.djh.researchops.rag;

import com.djh.researchops.exception.BusinessException;
import io.grpc.Status;
import java.util.IdentityHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Logs every frame/cause/type, but never arbitrary SDK messages (which may contain requests or credentials). */
final class RagDiagnostics {
    private static final Logger LOG = LoggerFactory.getLogger(RagDiagnostics.class);

    private RagDiagnostics() { }

    static BusinessException failure(String stage, String collection, String operation,
                                     Throwable failure, int code, String publicMessage) {
        error(stage, collection, operation, failure);
        var result = new BusinessException(code, publicMessage);
        result.initCause(failure);
        return result;
    }

    static void error(String stage, String collection, String operation, Throwable failure) {
        var seen = new IdentityHashMap<Throwable, Boolean>();
        Throwable root = failure;
        while (root.getCause() != null && seen.put(root, true) == null) root = root.getCause();
        var stack = new java.io.StringWriter();
        safeTrace(failure, new IdentityHashMap<>()).printStackTrace(new java.io.PrintWriter(stack));
        LOG.error("RAG failure stage={} collection={} operation={} exceptionType={} rootType={} grpcStatus={}\n{}",
                stage, collection, operation, failure.getClass().getName(), root.getClass().getName(),
                Status.fromThrowable(failure).getCode(), stack);
    }

    static int searchHttpStatus(Throwable failure) {
        return switch (Status.fromThrowable(failure).getCode()) {
            case UNAVAILABLE, DEADLINE_EXCEEDED, RESOURCE_EXHAUSTED, CANCELLED -> 503;
            case INVALID_ARGUMENT, UNIMPLEMENTED, FAILED_PRECONDITION, INTERNAL, DATA_LOSS,
                    UNKNOWN, NOT_FOUND, ALREADY_EXISTS, PERMISSION_DENIED, UNAUTHENTICATED, OUT_OF_RANGE, ABORTED ->
                    hasGrpcCause(failure) ? 502 : 500;
            default -> 500;
        };
    }

    private static boolean hasGrpcCause(Throwable failure) {
        var seen = new IdentityHashMap<Throwable, Boolean>();
        for (Throwable current = failure; current != null && seen.put(current, true) == null; current = current.getCause()) {
            if (current instanceof io.grpc.StatusException || current instanceof io.grpc.StatusRuntimeException) return true;
        }
        return false;
    }

    private static Throwable safeTrace(Throwable original, IdentityHashMap<Throwable, Boolean> seen) {
        if (original == null || seen.put(original, true) != null) return null;
        String message = "[SDK/request message omitted]";
        if (original instanceof io.grpc.StatusException || original instanceof io.grpc.StatusRuntimeException) {
            message = "grpcStatus=" + Status.fromThrowable(original).getCode();
        } else if ("Invalid value type for EQ. Can either be a string or Number".equals(original.getMessage())) {
            message = original.getMessage(); // Known constant diagnostic, contains no user input.
        } else if (java.util.Set.of("document", "documentId", "source", "title", "chunkIndex", "isDemo",
                "content", "chunkId", "score").stream()
                .anyMatch(field -> ("Invalid RAG result field: " + field).equals(original.getMessage()))) {
            message = original.getMessage(); // Field names only, never payload values.
        }
        var projected = new SafeTrace(original.getClass().getName(), message, safeTrace(original.getCause(), seen));
        projected.setStackTrace(original.getStackTrace());
        for (Throwable suppressed : original.getSuppressed()) {
            Throwable safe = safeTrace(suppressed, seen);
            if (safe != null) projected.addSuppressed(safe);
        }
        return projected;
    }

    private static final class SafeTrace extends RuntimeException {
        private final String originalType;
        SafeTrace(String originalType, String message, Throwable cause) {
            super(message, cause);
            this.originalType = originalType;
        }
        @Override public String toString() { return originalType + ": " + getMessage(); }
    }
}
