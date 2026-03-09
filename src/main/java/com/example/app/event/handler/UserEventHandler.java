package com.example.app.event.handler;

import com.example.app.domain.UserEntity;
import com.example.app.event.domain.UserEventPayload;
import com.example.app.repository.UserRepository;
import com.example.app.tenant.service.TenantAwareCache;
import com.example.app.util.JsonUtil;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class UserEventHandler {

    private static final Logger LOG = Logger.getLogger(UserEventHandler.class);
    private static final Duration PREFS_TTL = Duration.ofDays(365);
    private static final Duration PROFILE_CACHE_TTL = Duration.ofHours(1);
    private static final Duration SEARCH_INDEX_TTL = Duration.ofDays(30);

    @Inject
    TenantAwareCache tenantAwareCache;

    @Inject
    UserRepository userRepository;

    public void onUserCreated(UserEventPayload payload, String tenantId) {
        LOG.infof("Handling USER_CREATED for user %s in tenant %s", payload.getUserId(), tenantId);

        sendWelcomeNotification(payload, tenantId);
        initUserPreferences(payload, tenantId);
        cacheUserProfile(payload, tenantId);
        updateSearchIndex(payload, tenantId);

        LOG.infof("USER_CREATED handling complete for user %s", payload.getUserId());
    }

    public void onUserUpdated(UserEventPayload payload, String tenantId) {
        LOG.infof("Handling USER_UPDATED for user %s in tenant %s", payload.getUserId(), tenantId);

        tenantAwareCache.evict("user:" + payload.getUserId());
        cacheUserProfile(payload, tenantId);
        updateSearchIndex(payload, tenantId);
        syncUserProfileAcrossServices(payload, tenantId);

        LOG.infof("USER_UPDATED handling complete for user %s", payload.getUserId());
    }

    public void onUserDeleted(UserEventPayload payload, String tenantId) {
        LOG.infof("Handling USER_DELETED for user %s in tenant %s", payload.getUserId(), tenantId);

        cleanupUserResources(payload, tenantId);
        removeFromSearchIndex(payload, tenantId);
        anonymizeUserData(payload, tenantId);

        LOG.infof("USER_DELETED handling complete for user %s", payload.getUserId());
    }

    private void sendWelcomeNotification(UserEventPayload payload, String tenantId) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "WELCOME");
        notification.put("recipientEmail", payload.getEmail());
        notification.put("recipientName", payload.getFullName());
        notification.put("tenantId", tenantId);
        notification.put("userId", payload.getUserId());
        notification.put("subject", "Welcome to " + tenantId);
        notification.put("template", "welcome-email");
        notification.put("vars", Map.of(
                "name", payload.getFullName() != null ? payload.getFullName() : "User",
                "role", payload.getRole() != null ? payload.getRole() : "USER"
        ));

        String notifKey = "notification:pending:" + payload.getUserId() + ":welcome";
        tenantAwareCache.put(notifKey, JsonUtil.toJson(notification), Duration.ofHours(24));

        LOG.infof("Welcome notification queued for %s <%s> in tenant %s",
                payload.getFullName(), payload.getEmail(), tenantId);
    }

    private void initUserPreferences(UserEventPayload payload, String tenantId) {
        Map<String, Object> preferences = new HashMap<>();
        preferences.put("theme", "light");
        preferences.put("language", "en");
        preferences.put("timezone", "UTC");
        preferences.put("notifications", Map.of(
                "email", true,
                "push", true,
                "sms", false,
                "digest", "daily"
        ));
        preferences.put("privacy", Map.of(
                "profileVisible", true,
                "activityVisible", false
        ));

        String prefsKey = "user:prefs:" + payload.getUserId();
        tenantAwareCache.put(prefsKey, JsonUtil.toJson(preferences), PREFS_TTL);
        LOG.infof("Default preferences initialized for user %s", payload.getUserId());
    }

    private void cacheUserProfile(UserEventPayload payload, String tenantId) {
        Map<String, String> profile = Map.of(
                "userId", payload.getUserId(),
                "email", payload.getEmail() != null ? payload.getEmail() : "",
                "fullName", payload.getFullName() != null ? payload.getFullName() : "",
                "role", payload.getRole() != null ? payload.getRole() : "USER"
        );

        String cacheKey = "user:" + payload.getUserId();
        tenantAwareCache.put(cacheKey, JsonUtil.toJson(profile), PROFILE_CACHE_TTL);
    }

    private void updateSearchIndex(UserEventPayload payload, String tenantId) {
        Map<String, Object> searchDoc = new HashMap<>();
        searchDoc.put("id", payload.getUserId());
        searchDoc.put("email", payload.getEmail());
        searchDoc.put("fullName", payload.getFullName());
        searchDoc.put("role", payload.getRole());
        searchDoc.put("tenantId", tenantId);

        String indexKey = "search:user:" + payload.getUserId();
        tenantAwareCache.put(indexKey, JsonUtil.toJson(searchDoc), SEARCH_INDEX_TTL);

        String emailIndexKey = "search:user:email:" + (payload.getEmail() != null ? payload.getEmail().toLowerCase() : "");
        tenantAwareCache.put(emailIndexKey, payload.getUserId(), SEARCH_INDEX_TTL);

        LOG.infof("Search index updated for user %s [%s] in tenant %s",
                payload.getUserId(), payload.getFullName(), tenantId);
    }

    private void removeFromSearchIndex(UserEventPayload payload, String tenantId) {
        tenantAwareCache.evict("search:user:" + payload.getUserId());
        if (payload.getEmail() != null) {
            tenantAwareCache.evict("search:user:email:" + payload.getEmail().toLowerCase());
        }
        LOG.infof("Search index entries removed for deleted user %s", payload.getUserId());
    }

    private void syncUserProfileAcrossServices(UserEventPayload payload, String tenantId) {
        Map<String, String> syncPayload = Map.of(
                "userId", payload.getUserId(),
                "fullName", payload.getFullName() != null ? payload.getFullName() : "",
                "email", payload.getEmail() != null ? payload.getEmail() : "",
                "role", payload.getRole() != null ? payload.getRole() : "",
                "action", "PROFILE_SYNC"
        );

        String syncKey = "sync:user:profile:" + payload.getUserId();
        tenantAwareCache.put(syncKey, JsonUtil.toJson(syncPayload), Duration.ofMinutes(30));
        LOG.infof("Profile sync queued for user %s across services", payload.getUserId());
    }

    private void cleanupUserResources(UserEventPayload payload, String tenantId) {
        tenantAwareCache.evict("user:" + payload.getUserId());
        tenantAwareCache.evict("user:prefs:" + payload.getUserId());
        tenantAwareCache.evict("user:sessions:" + payload.getUserId());
        tenantAwareCache.evict("sync:user:profile:" + payload.getUserId());

        String cleanupKey = "notification:pending:" + payload.getUserId() + ":welcome";
        tenantAwareCache.evict(cleanupKey);

        LOG.infof("All cached resources cleaned up for deleted user %s in tenant %s", payload.getUserId(), tenantId);
    }

    @Transactional
    private void anonymizeUserData(UserEventPayload payload, String tenantId) {
        try {
            UUID userId = UUID.fromString(payload.getUserId());
            userRepository.findByIdOptional(userId).ifPresent(user -> {
                user.setEmail("deleted-" + userId + "@anonymized.local");
                user.setFullName("Deleted User");
                user.setPasswordHash("DELETED");
                user.setStatus(UserEntity.UserStatus.INACTIVE);
                userRepository.persist(user);
                LOG.infof("User data anonymized for GDPR compliance: userId=%s, tenant=%s", userId, tenantId);
            });
        } catch (Exception e) {
            LOG.warnf(e, "Failed to anonymize user data for userId=%s (may already be deleted)", payload.getUserId());
        }
    }
}
