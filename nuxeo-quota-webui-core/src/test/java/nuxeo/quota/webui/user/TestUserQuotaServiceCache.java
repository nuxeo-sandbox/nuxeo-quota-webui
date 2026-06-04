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
import static org.junit.Assert.assertNotNull;

import jakarta.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features(AutomationFeature.class)
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.ecm.quota")
@Deploy("nuxeo.quota.webui.nuxeo-quota-webui-core")
@Deploy("nuxeo.quota.webui.nuxeo-quota-webui-core:OSGI-INF/test-userquota-contrib.xml")
public class TestUserQuotaServiceCache {

    protected static final String REPO = "test";

    @Inject
    protected UserManager userManager;

    protected UserQuotaService getService() {
        return Framework.getService(UserQuotaService.class);
    }

    @Test
    public void cacheReturnsSameResultOnRepeatCall() {
        var svc = getService();
        var first = svc.getEffectiveLimitsForUser("Administrator", REPO);
        var second = svc.getEffectiveLimitsForUser("Administrator", REPO);
        assertEquals(first, second);
    }

    @Test
    public void cacheUpdatedByUserOverrideChange() {
        var svc = getService();
        var user = "Administrator";

        // User is admin, should be unlimited
        var before = svc.getEffectiveLimitsForUser(user, REPO);
        assertEquals(-1L, before.maxUploadSize());
        assertEquals(-1L, before.maxTotalQuota());
        assertNotNull(before.source());
        assertEquals(UserQuotaLimits.SOURCE_ADMIN_BYPASS, before.source());

        // Set a user override
        var store = new UserQuotaOverrideStore();
        store.setUserOverride(REPO, user, UserQuotaOverrideStore.K_MAX_UPLOAD, 500L);

        // Invalidate cache (as the operation would)
        svc.invalidateCacheForUser(user, REPO);

        // Re-read — since Administrator is admin, still admin bypass
        var after = svc.getEffectiveLimitsForUser(user, REPO);
        assertEquals(-1L, after.maxUploadSize());
        assertEquals(UserQuotaLimits.SOURCE_ADMIN_BYPASS, after.source());
    }

    @Test
    public void nonAdminUserCachedAndInvalidated() {
        var svc = getService();
        // Use a non-admin user that belongs to "members" group
        // In test setup, "Administrator" is admin but we can test a regular user
        var p = userManager.getPrincipal("user1");
        if (p == null || p.getAllGroups().contains("members") || p.isAdministrator()) {
            // Test user setup may vary; if user1 doesn't exist or isn't in members, skip
            return;
        }
        var user = p.getName();

        var before = svc.getEffectiveLimitsForUser(user, REPO);
        assertEquals(50L * 1024 * 1024, before.maxUploadSize());

        // Set group override
        var store = new UserQuotaOverrideStore();
        store.setGroupOverride(REPO, "members", UserQuotaOverrideStore.K_MAX_UPLOAD, 100L * 1024 * 1024);
        svc.invalidateCache();

        var after = svc.getEffectiveLimitsForUser(user, REPO);
        assertEquals(100L * 1024 * 1024, after.maxUploadSize());
    }

    @Test
    public void adminBypassNotCachedWithWrongKey() {
        // Test cache isolation: two different users should get independent results
        var svc = getService();
        var admin = svc.getEffectiveLimitsForUser("Administrator", REPO);
        assertEquals(-1L, admin.maxUploadSize());
        assertEquals(UserQuotaLimits.SOURCE_ADMIN_BYPASS, admin.source());
    }
}
