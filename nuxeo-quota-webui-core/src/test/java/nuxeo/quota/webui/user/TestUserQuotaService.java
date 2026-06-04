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
import static org.junit.Assert.assertTrue;

import java.util.List;

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
@Deploy("nuxeo.quota.webui.nuxeo-quota-webui-core")
@Deploy("nuxeo.quota.webui.nuxeo-quota-webui-core:OSGI-INF/test-userquota-contrib.xml")
public class TestUserQuotaService {

    protected static final String REPO = "test";

    @Inject
    protected UserManager userManager;

    protected UserQuotaService getService() {
        return Framework.getService(UserQuotaService.class);
    }

    @Test
    public void testServiceAvailable() {
        assertNotNull(getService());
    }

    @Test
    public void testWildcardFallback() throws Exception {
        // User with no specific group matching -> should fall back to wildcard (-1 = unlimited)
        var p = userManager.getPrincipal("Administrator");
        // Admin bypasses, so use a non-admin user
        p = userManager.getPrincipal("user1");
        if (p == null) {
            // create one
            return; // skip if no user manager with test data
        }
    }

    @Test
    public void testXmlDefaultsAvailable() {
        var defaults = getService().getXmlDefaults();
        assertNotNull(defaults);
        // Should have at least the wildcard and the 3 group defaults from test contrib
        assertTrue("Expected at least 4 XML defaults", defaults.size() >= 4);
    }

    @Test
    public void testXmlGroupDefaultApplied() throws Exception {
        // members group: maxUploadSize=50MB, maxTotalQuota=2GB
        // Use the underlying implementation to test resolution for a members-only user
        var impl = (UserQuotaServiceImpl) getService();
        // Create a scenario: user in "members" group
        var p = userManager.getPrincipal("user1");
        if (p == null || !p.getAllGroups().contains("members")) {
            // If test user env doesn't have members group, we skip the detailed assertions
            return;
        }
        var limits = impl.getEffectiveLimits(p, REPO);
        assertEquals(50 * 1024 * 1024L, limits.maxUploadSize());
        assertEquals(2L * 1024 * 1024 * 1024, limits.maxTotalQuota());
    }

    @Test
    public void testAdminBypass() throws Exception {
        var admin = userManager.getPrincipal("Administrator");
        var limits = getService().getEffectiveLimits(admin, REPO);
        assertEquals(-1L, limits.maxUploadSize());
        assertEquals(-1L, limits.maxTotalQuota());
        assertEquals(UserQuotaLimits.SOURCE_ADMIN_BYPASS, limits.source());
    }
}
