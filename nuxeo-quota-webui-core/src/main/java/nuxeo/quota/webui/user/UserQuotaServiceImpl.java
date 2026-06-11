/*
 * (C) Copyright 2025 Hyland (http://hyland.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Thibaud Arguillere
 */
package nuxeo.quota.webui.user;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.utils.SizeUtils;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.cache.Cache;
import org.nuxeo.ecm.core.cache.CacheService;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * Default implementation of {@link UserQuotaService}.
 * <p>
 * Resolution order per key (maxUploadSize, maxTotalQuota):
 * <ol>
 * <li>User override (KV store)
 * <li>Group overrides, max-wins across all user's groups (KV store)
 * <li>XML group defaults, max-wins across all user's groups
 * <li>XML {@code *} wildcard default
 * <li>{@code Long.MAX_VALUE} (unlimited)
 * </ol>
 * Administrators bypass enforcement entirely.
 *
 * @since 2025.1
 */
public class UserQuotaServiceImpl extends DefaultComponent implements UserQuotaService {

    private static final Logger log = LogManager.getLogger(UserQuotaServiceImpl.class);

    public static final String XP_USER_QUOTAS = "userQuotas";

    public static final String CACHE_NAME = "quota-user-limits";

    protected static final long UNLIMITED_SENTINEL = -1L;

    /**
     * Specificity rank for resolution stages. Higher wins when aggregating the {@code source}
     * field across the two keys (upload + total) into a single {@link UserQuotaLimits}.
     */
    protected static final Map<String, Integer> SOURCE_RANK = Map.of(
            UserQuotaLimits.SOURCE_USER_OVERRIDE, 5,
            UserQuotaLimits.SOURCE_GROUP_OVERRIDE, 4,
            UserQuotaLimits.SOURCE_GROUP_DEFAULT, 3,
            UserQuotaLimits.SOURCE_WILDCARD_DEFAULT, 2,
            UserQuotaLimits.SOURCE_UNLIMITED, 1);

    protected Map<String, UserQuotaDescriptor> registry = new HashMap<>();

    protected UserQuotaOverrideStore overrideStore;

    protected volatile boolean enabled = true;

    /** Internal: a resolved long value paired with the resolution stage and matched group (if any). */
    protected record Resolved(long value, String source, String matchedGroup) {}

    @Override
    public void activate(ComponentContext context) {
        super.activate(context);
        var prop = Framework.getProperty(PROPERTY_DISABLED, "false");
        enabled = !Boolean.parseBoolean(prop);
        if (!enabled) {
            log.warn("Per-user quota listener is DISABLED via {} — counters and enforcement will be skipped",
                    PROPERTY_DISABLED);
        }
    }

    @Override
    public UserQuotaLimits getEffectiveLimits(NuxeoPrincipal p, String repositoryName) {
        return getEffectiveLimitsForUser(p.getName(), repositoryName);
    }

    @Override
    public List<UserQuotaDescriptor> getXmlDefaults() {
        return new ArrayList<>(registry.values());
    }

    @Override
    public UserQuotaLimits getEffectiveLimitsForUser(String userId, String repositoryName) {
        var cache = getCache();
        var cacheKey = repositoryName + "|" + userId;
        var cached = (UserQuotaLimits) cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        var limits = computeLimits(userId, repositoryName);
        cache.put(cacheKey, limits);
        return limits;
    }

    protected UserQuotaLimits computeLimits(String userId, String repositoryName) {
        var userManager = Framework.getService(UserManager.class);
        var p = userManager.getPrincipal(userId);
        if (p == null) {
            log.warn("User not found: {}", userId);
            return new UserQuotaLimits(UNLIMITED_SENTINEL, UNLIMITED_SENTINEL,
                    UserQuotaLimits.SOURCE_UNLIMITED, null);
        }
        if (p.isAdministrator()) {
            return new UserQuotaLimits(UNLIMITED_SENTINEL, UNLIMITED_SENTINEL,
                    UserQuotaLimits.SOURCE_ADMIN_BYPASS, null);
        }
        var upload = resolveKey(p, repositoryName, UserQuotaOverrideStore.K_MAX_UPLOAD);
        var total = resolveKey(p, repositoryName, UserQuotaOverrideStore.K_MAX_TOTAL);

        // Pick the most-specific source across the two keys. matchedGroup is taken from
        // whichever resolved value contributed the winning source (or any if both share it).
        var winner = pickMoreSpecific(upload, total);

        return new UserQuotaLimits(upload.value(), total.value(), winner.source(), winner.matchedGroup());
    }

    protected Cache getCache() {
        var cs = Framework.getService(CacheService.class);
        return cs.getCache(CACHE_NAME);
    }

    @Override
    public void invalidateCache() {
        var cache = getCache();
        if (cache != null) {
            cache.invalidateAll();
        }
    }

    @Override
    public void invalidateCacheForUser(String userId, String repositoryName) {
        var cache = getCache();
        if (cache != null) {
            cache.invalidate(repositoryName + "|" + userId);
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Resolve a single key using the full resolution chain, returning the value and its source. */
    protected Resolved resolveKey(NuxeoPrincipal p, String repositoryName, String key) {
        var store = getOverrideStore();

        // (a) user override
        var userOverride = store.getUserOverride(repositoryName, p.getName(), key);
        if (userOverride.isPresent()) {
            return new Resolved(userOverride.get(), UserQuotaLimits.SOURCE_USER_OVERRIDE, null);
        }

        // (b) group overrides, max-wins (-1 treated as +infinity)
        var groupOverrideMatch = collectGroupOverridesWithGroup(store, repositoryName, p.getAllGroups(), key);
        var groupOverrideWin = maxWinWithGroup(groupOverrideMatch);
        if (groupOverrideWin.isPresent()) {
            var r = groupOverrideWin.get();
            return new Resolved(r.value(), UserQuotaLimits.SOURCE_GROUP_OVERRIDE, r.matchedGroup());
        }

        // (c) XML group defaults, max-wins (-1 treated as +infinity)
        var groupDefaultMatch = collectXmlGroupValuesWithGroup(p, key);
        var groupDefaultWin = maxWinWithGroup(groupDefaultMatch);
        if (groupDefaultWin.isPresent()) {
            var r = groupDefaultWin.get();
            return new Resolved(r.value(), UserQuotaLimits.SOURCE_GROUP_DEFAULT, r.matchedGroup());
        }

        // (d) XML wildcard default
        var wildcard = registry.get("*");
        if (wildcard != null) {
            var v = parseSize(wildcard, key);
            if (v == UNLIMITED_SENTINEL || v >= 0) {
                return new Resolved(v, UserQuotaLimits.SOURCE_WILDCARD_DEFAULT, null);
            }
        }

        // (e) unlimited
        return new Resolved(Long.MAX_VALUE, UserQuotaLimits.SOURCE_UNLIMITED, null);
    }

    /** Pick the {@link Resolved} with the more specific source (user > group > wildcard > unlimited). */
    protected static Resolved pickMoreSpecific(Resolved a, Resolved b) {
        int rankA = SOURCE_RANK.getOrDefault(a.source(), 0);
        int rankB = SOURCE_RANK.getOrDefault(b.source(), 0);
        return rankA >= rankB ? a : b;
    }

    /**
     * From a list of values, return the max-wins result:
     * {@code -1} as +infinity wins over everything; otherwise the largest positive value.
     */
    static Optional<Long> maxWin(List<Long> values) {
        if (values.isEmpty()) {
            return Optional.empty();
        }
        long max = Long.MIN_VALUE;
        boolean hasUnlimited = false;
        for (long v : values) {
            if (v == UNLIMITED_SENTINEL) {
                hasUnlimited = true;
            } else if (v > max) {
                max = v;
            }
        }
        if (hasUnlimited) {
            return Optional.of(UNLIMITED_SENTINEL);
        }
        if (max >= 0) {
            return Optional.of(max);
        }
        return Optional.empty();
    }

    /**
     * Max-wins across {@code (group, value)} pairs. Returns the winning value with the group
     * that contributed it; if multiple groups tie, the first one encountered is reported.
     */
    protected static Optional<Resolved> maxWinWithGroup(List<Resolved> values) {
        if (values.isEmpty()) {
            return Optional.empty();
        }
        Resolved unlimited = null;
        Resolved best = null;
        for (var r : values) {
            if (r.value() == UNLIMITED_SENTINEL) {
                if (unlimited == null) {
                    unlimited = r;
                }
            } else if (r.value() >= 0 && (best == null || r.value() > best.value())) {
                best = r;
            }
        }
        if (unlimited != null) {
            return Optional.of(unlimited);
        }
        return Optional.ofNullable(best);
    }

    /**
     * Collect group-override values along with the contributing group name. Only entries with
     * a non-null value are returned. Uses a single batch KV read.
     */
    protected List<Resolved> collectGroupOverridesWithGroup(UserQuotaOverrideStore store, String repositoryName,
            List<String> groups, String key) {
        var results = new ArrayList<Resolved>();
        for (var entry : store.collectGroupOverridesWithGroup(repositoryName, groups, key)) {
            results.add(new Resolved(entry.getValue(), UserQuotaLimits.SOURCE_GROUP_OVERRIDE, entry.getKey()));
        }
        return results;
    }

    /** Collect non-null XML group default values with the contributing group name. */
    protected List<Resolved> collectXmlGroupValuesWithGroup(NuxeoPrincipal p, String key) {
        var values = new ArrayList<Resolved>();
        var groups = p.getAllGroups();
        for (UserQuotaDescriptor d : registry.values()) {
            if ("*".equals(d.group)) {
                continue;
            }
            if (groups.contains(d.group)) {
                long v = parseSize(d, key);
                if (v != Long.MIN_VALUE) {
                    values.add(new Resolved(v, UserQuotaLimits.SOURCE_GROUP_DEFAULT, d.group));
                }
            }
        }
        return values;
    }

    /** Parse a size value from a descriptor for the given key. */
    protected long parseSize(UserQuotaDescriptor d, String key) {
        String val;
        if (UserQuotaOverrideStore.K_MAX_UPLOAD.equals(key)) {
            val = d.maxUploadSize;
        } else {
            val = d.maxTotalQuota;
        }
        if (val == null) {
            return Long.MIN_VALUE; // not set/unspecified
        }
        if ("-1".equals(val)) {
            return UNLIMITED_SENTINEL;
        }
        return SizeUtils.parseSizeInBytes(val);
    }

    @Override
    public synchronized UserQuotaOverrideStore getOverrideStore() {
        if (overrideStore == null) {
            overrideStore = new UserQuotaOverrideStore();
        }
        return overrideStore;
    }

    // --- Contribution handling ---

    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance component) {
        if (XP_USER_QUOTAS.equals(extensionPoint) && contribution instanceof UserQuotaDescriptor d) {
            registry.put(d.group, d);
            invalidateCache();
        }
    }

    @Override
    public void unregisterContribution(Object contribution, String extensionPoint, ComponentInstance component) {
        if (XP_USER_QUOTAS.equals(extensionPoint) && contribution instanceof UserQuotaDescriptor d) {
            registry.remove(d.group);
            invalidateCache();
        }
    }
}
