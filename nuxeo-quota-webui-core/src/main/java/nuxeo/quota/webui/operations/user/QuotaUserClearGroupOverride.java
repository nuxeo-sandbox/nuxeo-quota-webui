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
import static nuxeo.quota.webui.QuotaUtils.ensureAdmin;
import static nuxeo.quota.webui.QuotaUtils.requireNotBlank;

import org.json.JSONObject;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.api.Framework;

import nuxeo.quota.webui.user.UserQuotaOverrideStore;
import nuxeo.quota.webui.user.UserQuotaService;

/**
 * Clears a group override for one or both keys. Admin-only.
 *
 * @since 2025.1
 */
@Operation(id = QuotaUserClearGroupOverride.ID, category = "Quotas", label = "Quota.User.ClearGroupOverride", description = ""
        + "Clear per-group quota overrides. Admin only.")
public class QuotaUserClearGroupOverride {

    public static final String ID = "Quota.User.ClearGroupOverride";

    @Context
    protected CoreSession session;

    @Param(name = "group", required = true)
    protected String group;

    @Param(name = "key", required = false)
    protected String key;

    @OperationMethod
    public Blob run() {
        ensureAdmin(session);
        requireNotBlank(group, "Group");

        var service = Framework.getService(UserQuotaService.class);
        var store = service.getOverrideStore();
        var repo = session.getRepositoryName();

        if (key == null || key.isBlank()) {
            store.clearAllGroupOverrides(repo, group);
        } else if (UserQuotaOverrideStore.K_MAX_UPLOAD.equals(key) || UserQuotaOverrideStore.K_MAX_TOTAL.equals(key)) {
            store.clearGroupOverride(repo, group, key);
        } else {
            throw new NuxeoException("Invalid key: " + key, SC_BAD_REQUEST);
        }

        // Invalidate limits cache — group changes can affect any user
        service.invalidateCache();

        var json = new JSONObject();
        json.put("cleared", true);
        return Blobs.createJSONBlob(json.toString());
    }
}
