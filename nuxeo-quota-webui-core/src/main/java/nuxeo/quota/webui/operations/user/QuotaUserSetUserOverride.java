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

import static nuxeo.quota.webui.QuotaUtils.ensureAdmin;
import static nuxeo.quota.webui.QuotaUtils.parseQuotaSize;
import static nuxeo.quota.webui.QuotaUtils.requireNotBlank;

import org.json.JSONObject;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.runtime.api.Framework;

import nuxeo.quota.webui.user.UserQuotaOverrideStore;
import nuxeo.quota.webui.user.UserQuotaService;

/**
 * Sets a user override for maxUploadSize and/or maxTotalQuota. Admin-only.
 *
 * @since 2025.1
 */
@Operation(id = QuotaUserSetUserOverride.ID, category = "Quotas", label = "Quota.User.SetUserOverride", description = ""
        + "Set per-user quota overrides. Admin only.")
public class QuotaUserSetUserOverride {

    public static final String ID = "Quota.User.SetUserOverride";

    @Context
    protected CoreSession session;

    @Param(name = "username", required = true)
    protected String username;

    @Param(name = "maxUploadSize", required = false)
    protected String maxUploadSize;

    @Param(name = "maxTotalQuota", required = false)
    protected String maxTotalQuota;

    @OperationMethod
    public Blob run() {
        ensureAdmin(session);
        requireNotBlank(username, "Username");

        var service = Framework.getService(UserQuotaService.class);
        var store = service.getOverrideStore();
        var repo = session.getRepositoryName();

        if (maxUploadSize != null) {
            store.setUserOverride(repo, username, UserQuotaOverrideStore.K_MAX_UPLOAD, parseQuotaSize(maxUploadSize));
        }
        if (maxTotalQuota != null) {
            store.setUserOverride(repo, username, UserQuotaOverrideStore.K_MAX_TOTAL, parseQuotaSize(maxTotalQuota));
        }

        // Invalidate limits cache for this user
        service.invalidateCacheForUser(username, repo);

        var json = new JSONObject();
        json.put("username", username);
        json.put("maxUploadSize", maxUploadSize);
        json.put("maxTotalQuota", maxTotalQuota);
        json.put("status", "ok");

        return Blobs.createJSONBlob(json.toString());
    }
}
