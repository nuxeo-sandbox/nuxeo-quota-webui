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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.kv.KeyValueService;
import org.nuxeo.runtime.kv.KeyValueStore;
import org.nuxeo.runtime.kv.KeyValueStoreProvider;

/**
 * KV facade for per-group and per-user quota overrides.
 * <p>
 * Two named stores are used:
 * <ul>
 * <li>{@code quota-group-overrides} — key format: {@code <repo>:<group>:<key>}
 * <li>{@code quota-user-overrides} — key format: {@code <repo>:<userId>:<key>}
 * </ul>
 * No descriptors are contributed; stores inherit the production {@code default} store
 * (Mongo or SQL) with the store name as namespace for cluster-shared storage.
 *
 * @since 2025.1
 */
public class UserQuotaOverrideStore {

    protected static final String GROUP_STORE = "quota-group-overrides";

    protected static final String USER_STORE = "quota-user-overrides";

    public static final String K_MAX_UPLOAD = "maxUploadSize";

    public static final String K_MAX_TOTAL = "maxTotalQuota";

    // --- Group overrides ---

    public Optional<Long> getGroupOverride(String repo, String group, String key) {
        return readOptional(groupStore(), buildKey(repo, group, key));
    }

    public void setGroupOverride(String repo, String group, String key, long value) {
        groupStore().put(buildKey(repo, group, key), value);
    }

    public void clearGroupOverride(String repo, String group, String key) {
        groupStore().put(buildKey(repo, group, key), (Long) null);
    }

    public void clearAllGroupOverrides(String repo, String group) {
        var store = groupStore();
        store.put(buildKey(repo, group, K_MAX_UPLOAD), (Long) null);
        store.put(buildKey(repo, group, K_MAX_TOTAL), (Long) null);
    }

    /** Returns a map: group -> (key -> value) for all group overrides in the given repo. */
    public Map<String, Map<String, Long>> listGroupOverrides(String repo) {
        return listOverrides(groupStore(), repo);
    }

    /** Batch get group overrides for the given groups and key. */
    public List<Long> collectGroupOverrides(String repo, List<String> groups, String key) {
        var store = groupStore();
        var keys = groups.stream().map(g -> buildKey(repo, g, key)).collect(Collectors.toList());
        var batch = store.getLongs(keys);
        var values = new ArrayList<Long>();
        for (var entry : batch.entrySet()) {
            if (entry.getValue() != null) {
                values.add(entry.getValue());
            }
        }
        return values;
    }

    // --- User overrides ---

    public Optional<Long> getUserOverride(String repo, String userId, String key) {
        return readOptional(userStore(), buildKey(repo, userId, key));
    }

    public void setUserOverride(String repo, String userId, String key, long value) {
        userStore().put(buildKey(repo, userId, key), value);
    }

    public void clearUserOverride(String repo, String userId, String key) {
        userStore().put(buildKey(repo, userId, key), (Long) null);
    }

    public void clearAllUserOverrides(String repo, String userId) {
        var store = userStore();
        store.put(buildKey(repo, userId, K_MAX_UPLOAD), (Long) null);
        store.put(buildKey(repo, userId, K_MAX_TOTAL), (Long) null);
    }

    /** Returns a map: userId -> (key -> value) for all user overrides in the given repo. */
    public Map<String, Map<String, Long>> listUserOverrides(String repo) {
        return listOverrides(userStore(), repo);
    }

    // --- Internal helpers ---

    protected KeyValueStore groupStore() {
        return getStore(GROUP_STORE);
    }

    protected KeyValueStore userStore() {
        return getStore(USER_STORE);
    }

    protected KeyValueStore getStore(String name) {
        var kvs = Framework.getService(KeyValueService.class);
        return kvs.getKeyValueStore(name);
    }

    protected String buildKey(String repo, String entity, String key) {
        return repo + ":" + entity + ":" + key;
    }

    protected Optional<Long> readOptional(KeyValueStore store, String key) {
        Long val = store.getLong(key);
        return val != null ? Optional.of(val) : Optional.empty();
    }

    /**
     * List all overrides whose key starts with {@code repo:}. Parses the key format
     * {@code repo:entity:key} into a nested map using a single batch get.
     */
    protected Map<String, Map<String, Long>> listOverrides(KeyValueStore store, String repo) {
        var result = new HashMap<String, Map<String, Long>>();
        var prefix = repo + ":";
        var provider = (KeyValueStoreProvider) store;
        List<String> keys;
        try (Stream<String> ks = provider.keyStream(prefix)) {
            keys = ks.collect(Collectors.toList());
        }
        if (keys.isEmpty()) {
            return result;
        }
        var batch = store.getLongs(keys);
        for (var entry : batch.entrySet()) {
            var val = entry.getValue();
            if (val == null) {
                continue;
            }
            var k = entry.getKey();
            var suffix = k.substring(prefix.length());
            var colon = suffix.indexOf(':');
            if (colon < 0) {
                continue;
            }
            var entity = suffix.substring(0, colon);
            var key = suffix.substring(colon + 1);
            result.computeIfAbsent(entity, e -> new HashMap<>()).put(key, val);
        }
        return result;
    }
}
