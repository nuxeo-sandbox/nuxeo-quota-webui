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
package nuxeo.quota.webui.operations.user;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;

import jakarta.inject.Inject;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features(AutomationFeature.class)
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.ecm.quota")
@Deploy("nuxeo.quota.webui.nuxeo-quota-webui-core")
@Deploy("nuxeo.quota.webui.nuxeo-quota-webui-core:OSGI-INF/test-userquota-contrib.xml")
public class TestUserQuotaOperations {

    @Inject
    protected CoreSession session;

    @Inject
    protected AutomationService automationService;

    @Test
    public void testGetConfigurationReturnsJson() throws Exception {
        var ctx = new OperationContext(session);
        var blob = (Blob) automationService.run(ctx, QuotaUserGetConfiguration.ID);
        assertNotNull(blob);
        var json = new JSONObject(blob.getString());
        assertTrue(json.has("xmlDefaults"));
        assertTrue(json.has("groupOverrides"));
        assertTrue(json.has("userOverrides"));
    }

    @Test
    public void testGetForCurrentUserReturnsJson() throws Exception {
        var ctx = new OperationContext(session);
        var blob = (Blob) automationService.run(ctx, QuotaUserGetForCurrentUser.ID);
        assertNotNull(blob);
        var json = new JSONObject(blob.getString());
        assertTrue(json.has("userId"));
        assertTrue(json.has("usedBytes"));
        assertTrue(json.has("maxTotalQuota"));
        assertTrue(json.has("maxUploadSize"));
    }

    @Test
    public void testAdminOpsReturn403ForNonAdmin() throws Exception {
        var ctx = new OperationContext(session);
        var params = new HashMap<String, Object>();
        params.put("username", "someuser");
        try {
            automationService.run(ctx, QuotaUserGetForUser.ID, params);
        } catch (NuxeoException e) {
            // Expected: non-admin call should throw 403
            assertTrue("Expected 403 Forbidden", e.getStatusCode() == 403);
        }
    }
}
