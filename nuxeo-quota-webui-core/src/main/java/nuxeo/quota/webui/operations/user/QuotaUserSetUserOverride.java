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

import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static nuxeo.quota.webui.operations.user.QuotaUserGetForUser.ensureAdmin;
import static nuxeo.quota.webui.operations.user.QuotaUserSetGroupOverride.parseSize;

import org.json.JSONObject;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.NuxeoException;

import nuxeo.quota.webui.user.UserQuotaOverrideStore;

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

        if (username == null || username.isBlank()) {
            throw new NuxeoException("Username is required", SC_BAD_REQUEST);
        }

        var store = new UserQuotaOverrideStore();
        var repo = session.getRepositoryName();

        if (maxUploadSize != null) {
            store.setUserOverride(repo, username, UserQuotaOverrideStore.K_MAX_UPLOAD, parseSize(maxUploadSize));
        }
        if (maxTotalQuota != null) {
            store.setUserOverride(repo, username, UserQuotaOverrideStore.K_MAX_TOTAL, parseSize(maxTotalQuota));
        }

        var json = new JSONObject();
        json.put("username", username);
        json.put("maxUploadSize", maxUploadSize);
        json.put("maxTotalQuota", maxTotalQuota);
        json.put("status", "ok");

        return Blobs.createJSONBlob(json.toString());
    }
}
