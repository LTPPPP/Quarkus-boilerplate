package com.example.app.util;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class TraceContextPropagator {

    public String getCurrentTraceId() {
        Span currentSpan = Span.fromContext(Context.current());
        SpanContext spanContext = currentSpan.getSpanContext();
        if (spanContext.isValid()) {
            return spanContext.getTraceId();
        }
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    public String getCurrentSpanId() {
        Span currentSpan = Span.fromContext(Context.current());
        SpanContext spanContext = currentSpan.getSpanContext();
        if (spanContext.isValid()) {
            return spanContext.getSpanId();
        }
        return "";
    }

    public Optional<String> extractTraceParent() {
        Span currentSpan = Span.fromContext(Context.current());
        SpanContext ctx = currentSpan.getSpanContext();
        if (ctx.isValid()) {
            String traceParent = String.format("00-%s-%s-%s",
                    ctx.getTraceId(),
                    ctx.getSpanId(),
                    ctx.getTraceFlags().asHex());
            return Optional.of(traceParent);
        }
        return Optional.empty();
    }
}
