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
package nuxeo.quota.webui.operations;

import org.json.JSONObject;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;

import nuxeo.quota.webui.QuotaUtils;

/**
 * Returns whether the current user is allowed to manage quotas, as resolved by
 * {@link QuotaUtils#isQuotaAdmin}.
 * <p>
 * Used by the UI to decide whether to render quota edit controls. Server-side
 * operations always re-validate via {@link QuotaUtils#ensureAdmin}.
 *
 * @since 2025.1
 */
@Operation(id = QuotaCanManageQuotas.ID,
        category = "Quotas",
        label = "Quota: Can Manage Quotas",
        description = "Returns {\"canManage\": true|false} for the current user, honoring "
                + "nuxeo.quota.webui.admin.groups and nuxeo.quota.webui.admin.users.")
public class QuotaCanManageQuotas {

    public static final String ID = "Quota.CanManageQuotas";

    @Context
    protected CoreSession session;

    @OperationMethod
    public Blob run() {
        var json = new JSONObject();
        json.put("canManage", QuotaUtils.isQuotaAdmin(session.getPrincipal()));
        return Blobs.createJSONBlob(json.toString());
    }
}
