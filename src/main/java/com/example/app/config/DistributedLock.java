package com.example.app.config;

import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to apply distributed locking around a method invocation.
 * The lock is acquired before the method executes and released after.
 *
 * <p>The {@code key} supports SpEL-like parameter references using {@code {paramName}} syntax.
 * For example: {@code @DistributedLock(key = "payment:{orderId}")} will resolve
 * {@code {orderId}} from the method parameter named {@code orderId}.</p>
 *
 * <p>Usage:</p>
 * <pre>
 * &#64;DistributedLock(key = "order:status:{orderId}", timeoutSeconds = 30)
 * public OrderEntity updateStatus(UUID orderId, OrderStatus newStatus) {
 *     // This method is protected by a distributed lock
 * }
 * </pre>
 */
@Inherited
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface DistributedLock {

    /**
     * Lock key pattern. Supports {@code {paramName}} placeholders
     * that will be resolved from method parameter names.
     */
    String key();

    /**
     * Lock TTL (auto-expiry) in seconds. Should be longer than the expected
     * method execution time to prevent premature lock release.
     */
    int timeoutSeconds() default 30;
}
