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

import static nuxeo.quota.webui.operations.user.QuotaUserGetForUser.ensureAdmin;

import org.json.JSONObject;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.runtime.api.Framework;

import nuxeo.quota.webui.user.UserQuotaInitialComputationWork;

/**
 * Triggers asynchronous recomputation of the per-user counter for a specific user.
 * Admin-only.
 *
 * @since 2025.1
 */
@Operation(id = QuotaUserRecomputeForUser.ID, category = "Quotas", label = "Quota.User.RecomputeForUser", description = ""
        + "Recompute per-user quota for a specific user. Admin only.")
public class QuotaUserRecomputeForUser {

    public static final String ID = "Quota.User.RecomputeForUser";

    @Context
    protected CoreSession session;

    @Param(name = "username", required = true)
    protected String username;

    @OperationMethod
    public Blob run() {
        ensureAdmin(session);

        var work = new UserQuotaInitialComputationWork(session.getRepositoryName());
        Framework.getService(WorkManager.class).schedule(work);

        var json = new JSONObject();
        json.put("status", "scheduled");
        return Blobs.createJSONBlob(json.toString());
    }
}
