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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.ScrollResult;
import org.nuxeo.ecm.core.api.UnrestrictedSessionRunner;
import org.nuxeo.ecm.core.utils.BlobsExtractor;
import org.nuxeo.ecm.core.work.AbstractWork;
import org.nuxeo.ecm.quota.size.QuotaSizeService;
import org.nuxeo.runtime.api.Framework;

/**
 * Work that computes the initial per-user byte counters by scanning all documents
 * in a repository and summing blob bytes grouped by {@code dc:creator}.
 *
 * @since 2025.1
 */
public class UserQuotaInitialComputationWork extends AbstractWork {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LogManager.getLogger(UserQuotaInitialComputationWork.class);

    public static final String CATEGORY = "quota";

    protected static final int SCROLL_SIZE = 1000;

    protected static final int SCROLL_KEEP_ALIVE = 60;

    public UserQuotaInitialComputationWork(String repositoryName) {
        super(repositoryName + ":userQuotaInitialComputation");
        setDocument(repositoryName, null);
        this.repositoryName = repositoryName;
    }

    @Override
    public String getCategory() {
        return CATEGORY;
    }

    @Override
    public String getTitle() {
        return "Per-user quota initial computation";
    }

    @Override
    public boolean isIdempotent() {
        return false;
    }

    @Override
    public void work() {
        var currentWorker = this;
        new UnrestrictedSessionRunner(repositoryName) {
            @Override
            public void run() {
                compute(session, currentWorker);
            }
        }.runUnrestricted();
    }

    protected void compute(CoreSession session, UserQuotaInitialComputationWork worker) {
        log.debug("Starting per-user quota initial computation for repository: {}", repositoryName);

        var query = "SELECT * FROM Document WHERE ecm:isProxy = 0 AND ecm:isVersion = 0 AND ecm:isTrashed IN (0,1)";
        var extractor = newBlobsExtractor();
        var counter = new UserQuotaCounter();
        var totals = new HashMap<String, Long>();
        long totalDocs = 0;

        var scroll = session.scroll(query, SCROLL_SIZE, SCROLL_KEEP_ALIVE);
        while (scroll.hasResults()) {
            for (var uuid : scroll.getResults()) {
                totalDocs++;
                var doc = session.getDocument(new org.nuxeo.ecm.core.api.IdRef(uuid));
                var creator = (String) doc.getPropertyValue("dc:creator");
                if (creator == null || creator.isBlank()) {
                    continue;
                }
                long size = 0;
                for (var b : extractor.getBlobs(doc)) {
                    size += b.getLength();
                }
                if (size > 0) {
                    totals.merge(creator, size, Long::sum);
                }
            }
            if (totalDocs % 1000 == 0) {
                worker.setProgress(new Progress(totalDocs, 0));
            }
            scroll = session.scroll(scroll.getScrollId());
        }

        // Write all counters
        for (Map.Entry<String, Long> entry : totals.entrySet()) {
            counter.set(repositoryName, entry.getKey(), entry.getValue());
        }

        log.debug("Completed per-user quota initial computation for {} users, {} documents scanned",
                totals.size(), totalDocs);
        worker.setStatus("completed");
    }

    protected BlobsExtractor newBlobsExtractor() {
        var sizeService = Framework.getService(QuotaSizeService.class);
        var excludedPaths = sizeService.getExcludedPathList();
        var extractor = new BlobsExtractor();
        extractor.setExtractorProperties(null, new HashSet<>(excludedPaths), true);
        return extractor;
    }
}
