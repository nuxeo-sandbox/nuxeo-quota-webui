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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features(AutomationFeature.class)
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy("nuxeo.quota.webui.nuxeo-quota-webui-core")
public class TestUserQuotaOverrideStore {

    protected static final String REPO = "test";

    protected UserQuotaOverrideStore getStore() {
        return new UserQuotaOverrideStore();
    }

    @Test
    public void testGroupSetGetClearRoundTrip() {
        var store = getStore();
        var group = "members";
        var value = 50 * 1024 * 1024L;

        // Initially empty
        assertFalse(store.getGroupOverride(REPO, group, UserQuotaOverrideStore.K_MAX_UPLOAD).isPresent());

        // Set
        store.setGroupOverride(REPO, group, UserQuotaOverrideStore.K_MAX_UPLOAD, value);
        var retrieved = store.getGroupOverride(REPO, group, UserQuotaOverrideStore.K_MAX_UPLOAD);
        assertTrue(retrieved.isPresent());
        assertEquals(value, retrieved.get().longValue());

        // Clear
        store.clearGroupOverride(REPO, group, UserQuotaOverrideStore.K_MAX_UPLOAD);
        assertFalse(store.getGroupOverride(REPO, group, UserQuotaOverrideStore.K_MAX_UPLOAD).isPresent());
    }

    @Test
    public void testUserSetGetClearRoundTrip() {
        var store = getStore();
        var user = "jdoe";
        var value = 10L * 1024 * 1024 * 1024;

        assertFalse(store.getUserOverride(REPO, user, UserQuotaOverrideStore.K_MAX_TOTAL).isPresent());

        store.setUserOverride(REPO, user, UserQuotaOverrideStore.K_MAX_TOTAL, value);
        var retrieved = store.getUserOverride(REPO, user, UserQuotaOverrideStore.K_MAX_TOTAL);
        assertTrue(retrieved.isPresent());
        assertEquals(value, retrieved.get().longValue());

        store.clearUserOverride(REPO, user, UserQuotaOverrideStore.K_MAX_TOTAL);
        assertFalse(store.getUserOverride(REPO, user, UserQuotaOverrideStore.K_MAX_TOTAL).isPresent());
    }

    @Test
    public void testListGroupOverrides() {
        var store = getStore();
        store.setGroupOverride(REPO, "members", UserQuotaOverrideStore.K_MAX_UPLOAD, 50L);
        store.setGroupOverride(REPO, "members", UserQuotaOverrideStore.K_MAX_TOTAL, 2000L);
        store.setGroupOverride(REPO, "powerusers", UserQuotaOverrideStore.K_MAX_UPLOAD, 200L);

        var overrides = store.listGroupOverrides(REPO);
        assertEquals(2, overrides.size());
        assertTrue(overrides.containsKey("members"));
        assertTrue(overrides.containsKey("powerusers"));
        assertEquals(2, overrides.get("members").size());
        assertEquals(1, overrides.get("powerusers").size());
        assertEquals(Long.valueOf(50L), overrides.get("members").get(UserQuotaOverrideStore.K_MAX_UPLOAD));
    }

    @Test
    public void testRepoIsolation() {
        var store = getStore();
        store.setGroupOverride("repo1", "members", UserQuotaOverrideStore.K_MAX_UPLOAD, 100L);
        store.setGroupOverride("repo2", "members", UserQuotaOverrideStore.K_MAX_UPLOAD, 200L);

        var r1 = store.getGroupOverride("repo1", "members", UserQuotaOverrideStore.K_MAX_UPLOAD);
        var r2 = store.getGroupOverride("repo2", "members", UserQuotaOverrideStore.K_MAX_UPLOAD);
        assertTrue(r1.isPresent());
        assertTrue(r2.isPresent());
        assertEquals(100L, r1.get().longValue());
        assertEquals(200L, r2.get().longValue());
    }
}
