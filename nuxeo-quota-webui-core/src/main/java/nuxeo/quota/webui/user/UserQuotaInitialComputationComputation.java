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

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.bulk.action.computation.AbstractBulkComputation;
import org.nuxeo.ecm.core.utils.BlobsExtractor;
import org.nuxeo.ecm.quota.size.QuotaSizeService;
import org.nuxeo.runtime.api.Framework;

/**
 * BAF computation that scans documents and updates per-user byte counters.
 * <p>
 * Counter reset is performed by the operation entry point before submitting the BAF command.
 *
 * @since 2025.1
 */
public class UserQuotaInitialComputationComputation extends AbstractBulkComputation {

    private static final Logger log = LogManager.getLogger(UserQuotaInitialComputationComputation.class);

    protected final UserQuotaCounter counter = new UserQuotaCounter();

    protected Set<String> scopedUsers;

    public UserQuotaInitialComputationComputation() {
        super(UserQuotaInitialComputationAction.ACTION_FULL_NAME);
    }

    @Override
    public void startBucket(String bucketKey) {
        // Read scoped users from command params once per bucket
        var usersParam = getCurrentCommand().getParam(UserQuotaInitialComputationAction.PARAM_USERS);
        if (usersParam instanceof List<?> list && !list.isEmpty()) {
            scopedUsers = new HashSet<>();
            for (var u : list) {
                if (u instanceof String s) {
                    scopedUsers.add(s);
                }
            }
        } else {
            scopedUsers = null; // means "all users"
        }
    }

    @Override
    protected void compute(CoreSession session, List<String> ids, Map<String, Serializable> properties) {
        var extractor = newBlobsExtractor();
        for (var doc : loadDocuments(session, ids)) {
            var creator = (String) doc.getPropertyValue("dc:creator");
            if (creator == null || creator.isBlank()) {
                continue;
            }
            // When scoped to specific users, skip users not in the set
            if (scopedUsers != null && !scopedUsers.contains(creator)) {
                continue;
            }
            long size = 0;
            for (var blob : extractor.getBlobs(doc)) {
                size += blob.getLength();
            }
            if (size > 0) {
                counter.addAndGet(session.getRepositoryName(), creator, size);
            }
        }
    }

    protected BlobsExtractor newBlobsExtractor() {
        var sizeService = Framework.getService(QuotaSizeService.class);
        var excludedPaths = sizeService.getExcludedPathList();
        var extractor = new BlobsExtractor();
        extractor.setExtractorProperties(null, new HashSet<>(excludedPaths), true);
        return extractor;
    }
}
