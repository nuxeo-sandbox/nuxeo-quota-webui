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

import static jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

import org.json.JSONObject;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.runtime.api.Framework;

import nuxeo.quota.webui.user.UserQuotaCounter;
import nuxeo.quota.webui.user.UserQuotaInitialComputationAction;

/**
 * Recomputes per-user quota counters for all users. Admin only.
 *
 * @since 2025.1
 */
@Operation(id = QuotaUserRecomputeAll.ID,
          category = "Quotas",
          label = "Quota.User.RecomputeAll",
          description = "Recomputes per-user quota counters for all users. Admin only.")
public class QuotaUserRecomputeAll {

    public static final String ID = "Quota.User.RecomputeAll";

    @Context
    protected CoreSession session;

    @OperationMethod
    public Blob run() throws IOException {
        if (!session.getPrincipal().isAdministrator()) {
            throw new NuxeoException("Administrator required", SC_FORBIDDEN);
        }

        var repo = session.getRepositoryName();

        // Reset all counters
        new UserQuotaCounter().resetAllForRepository(repo);

        // Build full-repository NXQL
        var nxql = "SELECT * FROM Document WHERE ecm:isProxy = 0 AND ecm:isVersion = 0"
                + " AND ecm:isTrashed IN (0,1)";

        var command = new BulkCommand.Builder(
                UserQuotaInitialComputationAction.ACTION_NAME, nxql, session.getPrincipal().getName())
                .repository(repo)
                .param(UserQuotaInitialComputationAction.PARAM_TRIGGERED_BY,
                        session.getPrincipal().getName())
                .build();
        var commandId = Framework.getService(BulkService.class).submit(command);

        // Schedule completion event Work
        var work = new nuxeo.quota.webui.user.UserQuotaRecomputeCompletionWork(
                repo, commandId, "all",
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        Framework.getService(org.nuxeo.ecm.core.work.api.WorkManager.class).schedule(work);

        var json = new JSONObject()
                .put("status", "submitted")
                .put("commandId", commandId);
        return Blobs.createJSONBlob(json.toString());
    }
}
