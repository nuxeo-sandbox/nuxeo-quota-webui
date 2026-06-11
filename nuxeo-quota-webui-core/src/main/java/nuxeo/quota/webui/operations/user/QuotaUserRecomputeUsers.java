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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.api.Framework;

import nuxeo.quota.webui.user.UserQuotaCounter;
import nuxeo.quota.webui.user.UserQuotaInitialComputationAction;
import nuxeo.quota.webui.user.UserQuotaRecomputeCompletionWork;
import nuxeo.quota.webui.user.UserQuotaService;

/**
 * Recomputes per-user quota counters for the given users. Admin only.
 *
 * @since 2025.1
 */
@Operation(id = QuotaUserRecomputeUsers.ID,
          category = "Quotas",
          label = "Quota.User.RecomputeUsers",
          description = "Recomputes per-user quota counters for the given users. Admin only.")
public class QuotaUserRecomputeUsers {

    public static final String ID = "Quota.User.RecomputeUsers";

    @Context
    protected CoreSession session;

    @Param(name = "users", required = true)
    protected List<String> users;

    @OperationMethod
    public Blob run() throws IOException {
        ensureAdmin(session);

        var userManager = Framework.getService(UserManager.class);
        var counter = new UserQuotaCounter();
        var repo = session.getRepositoryName();
        var validated = new ArrayList<String>();
        var skipped = new ArrayList<String>();

        for (var u : users) {
            if (u == null || u.isBlank()) {
                continue;
            }
            var p = userManager.getPrincipal(u);
            if (p == null) {
                skipped.add(u);
            } else {
                validated.add(u);
                counter.set(repo, u, 0L);
            }
        }

        if (validated.isEmpty()) {
            var json = new JSONObject()
                    .put("status", "skipped")
                    .put("skippedUsers", new JSONArray(skipped))
                    .put("message", "No valid users to recompute");
            return Blobs.createJSONBlob(json.toString());
        }

        // Build scoped NXQL
        var quoted = validated.stream()
                .map(u -> "'" + u.replace("'", "''") + "'")
                .collect(Collectors.joining(","));
        var nxql = "SELECT * FROM Document WHERE ecm:isProxy = 0 AND ecm:isVersion = 0"
                + " AND ecm:isTrashed IN (0,1)"
                + " AND dc:creator IN (" + quoted + ")";

        var command = new BulkCommand.Builder(
                UserQuotaInitialComputationAction.ACTION_NAME, nxql, session.getPrincipal().getName())
                .repository(repo)
                .param(UserQuotaInitialComputationAction.PARAM_USERS, (java.io.Serializable) validated)
                .param(UserQuotaInitialComputationAction.PARAM_TRIGGERED_BY,
                        session.getPrincipal().getName())
                .build();
        var commandId = Framework.getService(BulkService.class).submit(command);

        // Schedule completion event Work
        scheduleCompletionWork(repo, commandId, "users", users, validated, skipped);

        var json = new JSONObject()
                .put("status", "submitted")
                .put("commandId", commandId)
                .put("requestedUsers", new JSONArray(users))
                .put("processedUsers", new JSONArray(validated))
                .put("skippedUsers", new JSONArray(skipped));
        return Blobs.createJSONBlob(json.toString());
    }

    protected void scheduleCompletionWork(String repo, String commandId, String scope,
            List<String> requested, List<String> processed, List<String> skipped) {
        Framework.getService(org.nuxeo.ecm.core.work.api.WorkManager.class)
                .schedule(new UserQuotaRecomputeCompletionWork(repo, commandId, scope,
                        requested, processed, skipped));
    }
}
