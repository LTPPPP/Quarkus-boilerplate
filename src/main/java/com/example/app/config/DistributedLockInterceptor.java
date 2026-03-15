package com.example.app.config;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import org.jboss.logging.Logger;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CDI interceptor that acquires a Redis distributed lock before method invocation
 * and releases it afterward. Used in conjunction with {@link DistributedLock} annotation.
 */
@DistributedLock(key = "")
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 100)
public class DistributedLockInterceptor {

    private static final Logger LOG = Logger.getLogger(DistributedLockInterceptor.class);
    private static final Pattern PARAM_PATTERN = Pattern.compile("\\{(\\w+)}");

    @Inject
    DistributedLockService lockService;

    @AroundInvoke
    public Object aroundInvoke(InvocationContext ctx) throws Exception {
        Method method = ctx.getMethod();
        DistributedLock annotation = method.getAnnotation(DistributedLock.class);
        if (annotation == null) {
            annotation = method.getDeclaringClass().getAnnotation(DistributedLock.class);
        }

        if (annotation == null || annotation.key().isEmpty()) {
            return ctx.proceed();
        }

        String resolvedKey = resolveKey(annotation.key(), method, ctx.getParameters());
        Duration ttl = Duration.ofSeconds(annotation.timeoutSeconds());

        LOG.debugf("Acquiring distributed lock: key=%s, ttl=%ds", resolvedKey, annotation.timeoutSeconds());

        return lockService.executeWithLock(resolvedKey, ttl, () -> {
            try {
                return ctx.proceed();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Exception in locked method: " + method.getName(), e);
            }
        });
    }

    /**
     * Resolves parameter placeholders in the lock key pattern.
     * E.g., "payment:{orderId}" with parameter orderId=UUID resolves to "payment:abc-123".
     */
    private String resolveKey(String keyPattern, Method method, Object[] params) {
        String resolved = keyPattern;
        Parameter[] parameters = method.getParameters();
        Matcher matcher = PARAM_PATTERN.matcher(keyPattern);

        while (matcher.find()) {
            String paramName = matcher.group(1);
            for (int i = 0; i < parameters.length; i++) {
                if (parameters[i].getName().equals(paramName) && i < params.length) {
                    String value = params[i] != null ? params[i].toString() : "null";
                    resolved = resolved.replace("{" + paramName + "}", value);
                    break;
                }
            }
        }
        return resolved;
    }
}
