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

import static nuxeo.quota.webui.QuotaUtils.buildUserQuotaJson;
import static nuxeo.quota.webui.QuotaUtils.ensureAdmin;

import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.runtime.api.Framework;

import nuxeo.quota.webui.user.UserQuotaCounter;
import nuxeo.quota.webui.user.UserQuotaService;

/**
 * Returns a specific user's quota usage and effective limits. Admin-only.
 *
 * @since 2025.1
 */
@Operation(id = QuotaUserGetForUser.ID, category = "Quotas", label = "Quota.User.GetForUser", description = ""
        + "Return a specific user's quota usage and effective limits. Admin only.")
public class QuotaUserGetForUser {

    public static final String ID = "Quota.User.GetForUser";

    @Context
    protected CoreSession session;

    @Param(name = "username", required = true)
    protected String username;

    @OperationMethod
    public Blob run() {
        ensureAdmin(session);

        var repo = session.getRepositoryName();

        var service = Framework.getService(UserQuotaService.class);
        var limits = service.getEffectiveLimitsForUser(username, repo);

        var counter = new UserQuotaCounter();
        var usedBytes = counter.get(repo, username);

        return Blobs.createJSONBlob(buildUserQuotaJson(username, usedBytes, limits).toString());
    }
}
