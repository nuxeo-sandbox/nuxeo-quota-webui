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
import java.util.List;

import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.kv.KeyValueService;
import org.nuxeo.runtime.kv.KeyValueStore;
import org.nuxeo.runtime.kv.KeyValueStoreProvider;

/**
 * KV facade for per-user byte counters.
 * <p>
 * Uses the {@code quota-user-counters} named store. No descriptor is contributed, so it
 * inherits the production {@code default} store (Mongo or SQL) with the store name as
 * namespace — automatically cluster-shared.
 *
 * @since 2025.1
 */
public class UserQuotaCounter {

    protected static final String STORE_NAME = "quota-user-counters";

    protected KeyValueStore getStore() {
        KeyValueService kvs = Framework.getService(KeyValueService.class);
        return kvs.getKeyValueStore(STORE_NAME);
    }

    protected String buildKey(String repo, String userId) {
        return repo + ":" + userId;
    }

    public long get(String repo, String userId) {
        Long val = getStore().getLong(buildKey(repo, userId));
        return val != null ? val : 0L;
    }

    public long addAndGet(String repo, String userId, long delta) {
        return getStore().addAndGet(buildKey(repo, userId), delta);
    }

    public void set(String repo, String userId, long value) {
        getStore().put(buildKey(repo, userId), value);
    }

    public void reset(String repo, String userId) {
        getStore().put(buildKey(repo, userId), (Long) null);
    }

    /** Reset all counters for the given repository. */
    public void resetAllForRepository(String repo) {
        var store = getStore();
        if (store instanceof KeyValueStoreProvider provider) {
            var prefix = repo + ":";
            try (var keys = provider.keyStream(prefix)) {
                keys.forEach(k -> provider.put(k, (Long) null));
            }
        } else {
            throw new NuxeoException("Cannot reset all counters: store is not a KeyValueStoreProvider");
        }
    }

    /**
     * Returns the list of userIds that currently have a counter entry for the given repository.
     *
     * @since 2025.1
     */
    public List<String> listUsersForRepository(String repo) {
        var store = getStore();
        if (!(store instanceof KeyValueStoreProvider provider)) {
            throw new NuxeoException("Cannot list users: store is not a KeyValueStoreProvider");
        }
        var prefix = repo + ":";
        var users = new ArrayList<String>();
        try (var keys = provider.keyStream(prefix)) {
            keys.forEach(k -> users.add(k.substring(prefix.length())));
        }
        return users;
    }
}
