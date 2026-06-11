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

import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.runtime.api.Framework;

import nuxeo.quota.webui.user.UserQuotaCounter;
import nuxeo.quota.webui.user.UserQuotaService;

/**
 * Returns the current user's quota usage and effective limits.
 *
 * @since 2025.1
 */
@Operation(id = QuotaUserGetForCurrentUser.ID, category = "Quotas", label = "Quota.User.GetForCurrentUser", description = ""
        + "Return the current user's quota usage and effective limits.")
public class QuotaUserGetForCurrentUser {

    public static final String ID = "Quota.User.GetForCurrentUser";

    @Context
    protected CoreSession session;

    @OperationMethod
    public Blob run() {
        var userId = session.getPrincipal().getName();
        var repo = session.getRepositoryName();

        var service = Framework.getService(UserQuotaService.class);
        var limits = service.getEffectiveLimitsForUser(userId, repo);

        var counter = new UserQuotaCounter();
        var usedBytes = counter.get(repo, userId);

        return Blobs.createJSONBlob(buildUserQuotaJson(userId, usedBytes, limits).toString());
    }
}
