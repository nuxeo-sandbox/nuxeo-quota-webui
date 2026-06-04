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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
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
import org.nuxeo.ecm.core.bulk.CoreBulkFeature;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features({ AutomationFeature.class, CoreBulkFeature.class })
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.ecm.quota")
@Deploy("nuxeo.quota.webui.nuxeo-quota-webui-core")
@Deploy("nuxeo.quota.webui.nuxeo-quota-webui-core:OSGI-INF/test-userquota-contrib.xml")
public class TestUserQuotaRecompute {

    @Inject
    protected CoreSession session;

    @Inject
    protected AutomationService automationService;

    @Test
    public void testRecomputeUsersAcceptsValidUsers() throws Exception {
        var ctx = new OperationContext(session);
        var params = new HashMap<String, Object>();
        params.put("users", Arrays.asList("Administrator"));
        var blob = (Blob) automationService.run(ctx, QuotaUserRecomputeUsers.ID, params);
        assertNotNull(blob);
        var json = new JSONObject(blob.getString());
        assertTrue("Should have status", json.has("status"));
        var status = json.getString("status");
        assertTrue("Status should be 'submitted'", "submitted".equals(status));
        assertTrue("Should have commandId", json.has("commandId"));
        assertTrue("Should have requestedUsers", json.has("requestedUsers"));
        assertEquals(1, json.getJSONArray("requestedUsers").length());
    }

    @Test
    public void testRecomputeUsersSkipsUnknownUser() throws Exception {
        var ctx = new OperationContext(session);
        var params = new HashMap<String, Object>();
        params.put("users", Arrays.asList("nonexistent_user"));
        var blob = (Blob) automationService.run(ctx, QuotaUserRecomputeUsers.ID, params);
        assertNotNull(blob);
        var json = new JSONObject(blob.getString());
        assertEquals("Status should be 'skipped' when no valid users",
                "skipped", json.getString("status"));
        assertTrue("Should have skippedUsers", json.has("skippedUsers"));
        assertEquals(1, json.getJSONArray("skippedUsers").length());
    }

    @Test
    public void testRecomputeAllReturnsCommandId() throws Exception {
        var ctx = new OperationContext(session);
        var blob = (Blob) automationService.run(ctx, QuotaUserRecomputeAll.ID);
        assertNotNull(blob);
        var json = new JSONObject(blob.getString());
        assertTrue("Should have status", json.has("status"));
        var status = json.getString("status");
        assertTrue("Status should be 'submitted'", "submitted".equals(status));
        assertTrue("Should have commandId", json.has("commandId"));
    }
}
