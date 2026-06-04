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
package nuxeo.quota.webui.user;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.EventContextImpl;
import org.nuxeo.ecm.core.work.AbstractWork;
import org.nuxeo.runtime.api.Framework;

/**
 * Work that waits for a user quota recompute BAF command to complete and fires
 * the {@code userQuotaRecomputeDone} event.
 *
 * @since 2025.1
 */
public class UserQuotaRecomputeCompletionWork extends AbstractWork {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LogManager.getLogger(UserQuotaRecomputeCompletionWork.class);

    protected final String repositoryName;

    protected final String commandId;

    protected final String scope; // "all" or "users"

    protected final List<String> requestedUsers;

    protected final List<String> processedUsers;

    protected final List<String> skippedUsers;

    public UserQuotaRecomputeCompletionWork(String repositoryName, String commandId, String scope,
            List<String> requestedUsers, List<String> processedUsers, List<String> skippedUsers) {
        super(repositoryName + ":userQuotaRecomputeDone:" + commandId);
        this.repositoryName = repositoryName;
        this.commandId = commandId;
        this.scope = scope;
        this.requestedUsers = requestedUsers;
        this.processedUsers = processedUsers;
        this.skippedUsers = skippedUsers;
        setDocuments(repositoryName, List.of());
    }

    @Override
    public String getCategory() {
        return "quota";
    }

    @Override
    public String getTitle() {
        return "User quota recompute completion: " + commandId;
    }

    @Override
    public boolean isIdempotent() {
        return false;
    }

    @Override
    public void work() {
        var bulkService = Framework.getService(BulkService.class);
        try {
            // Wait up to 24h, polling every 5 seconds
            var done = bulkService.await(commandId, Duration.ofHours(24));
            if (!done) {
                log.warn("User quota recompute command {} did not complete within 24h", commandId);
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("User quota recompute command {} was interrupted", commandId, e);
            return;
        }

        /*
         * Determine the effective user list for payload population.
         * - For scope="users", processedUsers is provided by the operation entry point.
         * - For scope="all", processedUsers is empty: scan the KV store post-completion to
         *   discover every userId that has a non-null counter for this repository.
         */
        var counter = new UserQuotaCounter();
        var effectiveProcessed = processedUsers;
        if (effectiveProcessed == null || effectiveProcessed.isEmpty()) {
            effectiveProcessed = counter.listUsersForRepository(repositoryName);
        }

        // Read final per-user totals (only users with > 0 bytes are exposed)
        var userTotals = new LinkedHashMap<String, Long>();
        for (var userId : effectiveProcessed) {
            var bytes = counter.get(repositoryName, userId);
            if (bytes > 0) {
                userTotals.put(userId, bytes);
            }
        }

        // Build event context
        var ctx = new EventContextImpl();
        var props = ctx.getProperties();
        props.put("repositoryName", repositoryName);
        props.put("scope", scope);
        props.put("requestedUsers", new ArrayList<>(requestedUsers));
        props.put("processedUsers", new ArrayList<>(effectiveProcessed));
        props.put("skippedUsers", new ArrayList<>(skippedUsers));
        props.put("userTotals", userTotals);
        props.put("commandId", commandId);

        Framework.getService(EventService.class).fireEvent(
                UserQuotaService.EVENT_USER_QUOTA_RECOMPUTE_DONE, ctx);
        log.info("Fired {} event for command {}", UserQuotaService.EVENT_USER_QUOTA_RECOMPUTE_DONE, commandId);
    }
}
