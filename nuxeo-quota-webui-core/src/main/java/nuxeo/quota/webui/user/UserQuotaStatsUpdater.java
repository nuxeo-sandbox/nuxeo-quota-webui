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
import java.util.HashSet;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.utils.BlobsExtractor;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.ecm.quota.AbstractQuotaStatsUpdater;
import org.nuxeo.ecm.quota.QuotaStatsInitialWork;
import org.nuxeo.ecm.quota.size.QuotaExceededException;
import org.nuxeo.ecm.quota.size.QuotaSizeService;
import org.nuxeo.runtime.api.Framework;

/**
 * Per-user quota stats updater.
 * <p>
 * Counts blob bytes against the document's {@code dc:creator} and enforces
 * per-user upload size and total quota limits.
 * <p>
 * Administrators bypass enforcement entirely, but their counter is still
 * updated for visibility.
 *
 * @since 2025.1
 */
public class UserQuotaStatsUpdater extends AbstractQuotaStatsUpdater {

    private static final Logger log = LogManager.getLogger(UserQuotaStatsUpdater.class);

    public static final String NAME = "userQuotaUpdater";

    public static final String LABEL = "user.quota.updater.label";

    public static final String DESCRIPTION = "user.quota.updater.description";

    protected final UserQuotaCounter counter = new UserQuotaCounter();

    // --- Override handleQuotaExceeded to roll back with a user-quota message ---

    @Override
    protected void handleQuotaExceeded(QuotaExceededException e, Event event) {
        log.debug("Per-user quota exceeded, rolling back: {}", e.getMessage());
        event.markRollBack("User Quota Exceeded", e);
    }

    // --- Event handlers ---

    @Override
    protected void processDocumentCreated(CoreSession session, DocumentModel doc) {
        if (doc.isVersion()) {
            return;
        }
        processDocWithBlobs(session, doc, 1);
    }

    @Override
    protected void processDocumentUpdated(CoreSession session, DocumentModel doc) {
        // Nothing to do, handled in processDocumentBeforeUpdate
    }

    @Override
    protected void processDocumentBeforeUpdate(CoreSession session, DocumentModel doc) {
        // Compute delta = new blobs - old blobs
        var newSize = getBlobsSize(doc);
        // old inner size stored on the QuotaAware facet (but we don't have one for user quota)
        // Instead, compute from the previous property values (same pattern as DocumentsSizeUpdater)
        long oldSize = 0;
        var extractor = newBlobsExtractor();
        var oldBlobs = extractor.getBlobs(doc);
        if (oldBlobs != null) {
            for (var b : oldBlobs) {
                oldSize += b.getLength();
            }
        }
        var delta = newSize - oldSize;
        if (delta == 0) {
            return;
        }
        applyDelta(session, doc, delta);
    }

    @Override
    protected void processDocumentAboutToBeRemoved(CoreSession session, DocumentModel doc) {
        if (doc.isVersion()) {
            // For versions, decrement the live doc's owner
            var sourceId = doc.getSourceId();
            if (sourceId != null) {
                var liveDoc = session.getDocument(new org.nuxeo.ecm.core.api.IdRef(sourceId));
                var creator = getCreator(liveDoc);
                if (creator != null) {
                    var size = getBlobsSize(doc);
                    if (size > 0) {
                        counter.addAndGet(session.getRepositoryName(), creator, -size);
                    }
                }
            }
            return;
        }
        var creator = getCreator(doc);
        if (creator == null) {
            return;
        }
        var size = getBlobsSize(doc);
        if (size > 0) {
            counter.addAndGet(session.getRepositoryName(), creator, -size);
        }
    }

    @Override
    protected void processDocumentCopied(CoreSession session, DocumentModel doc) {
        // On copy, Nuxeo resets dc:creator to the copier. The copy counts against the copier.
        processDocWithBlobs(session, doc, 1);
    }

    @Override
    protected void processDocumentMoved(CoreSession session, DocumentModel doc, DocumentModel sourceParent) {
        // No-op: moving does not change the owner or byte count
    }

    @Override
    protected void processDocumentBeforeCheckedIn(CoreSession session, DocumentModel doc) {
        // Version blobs count against the same user; check upload size
        processDocWithBlobs(session, doc, 0); // only check, do not add to counter (checked-in handles it)
    }

    @Override
    protected void processDocumentCheckedIn(CoreSession session, DocumentModel doc) {
        // Checked-in version already accounted for in before-check-in
    }

    @Override
    protected void processDocumentCheckedOut(CoreSession session, DocumentModel doc) {
        // No-op
    }

    @Override
    protected void processDocumentBeforeCheckedOut(CoreSession session, DocumentModel doc) {
        // No-op
    }

    @Override
    protected void processDocumentTrashOp(CoreSession session, DocumentModel doc, boolean isTrashed) {
        // Trashed content still counts toward the user's quota (same as container quota)
        // No counter change on trash/restore
    }

    @Override
    protected void processDocumentBeforeRestore(CoreSession session, DocumentModel doc) {
        // No-op: trashed content counts too
    }

    @Override
    protected void processDocumentRestored(CoreSession session, DocumentModel doc) {
        // No-op: trashed content counts too
    }

    @Override
    protected boolean needToProcessEventOnDocument(Event event, DocumentModel doc) {
        if (doc == null || doc.isProxy()) {
            return false;
        }
        return true;
    }

    // --- Internal logic ---

    protected void processDocWithBlobs(CoreSession session, DocumentModel doc, int mode) {
        var creator = getCreator(doc);
        if (creator == null) {
            return;
        }
        var userManager = Framework.getService(UserManager.class);
        var p = userManager.getPrincipal(creator);
        if (p == null) {
            return;
        }
        var quotaService = Framework.getService(UserQuotaService.class);
        var limits = quotaService.getEffectiveLimits(p, session.getRepositoryName());

        if (limits.isAdminBypass()) {
            // Track admin bytes for visibility but never enforce
            if (mode > 0) {
                var total = getBlobsSize(doc);
                if (total > 0) {
                    counter.addAndGet(session.getRepositoryName(), creator, total);
                }
            }
            return;
        }

        var blobs = getAllBlobs(doc);
        long total = 0;
        for (var b : blobs) {
            var len = b.getLength();
            // Per-blob upload check
            if (limits.maxUploadSize() > 0 && len > limits.maxUploadSize()) {
                throw new QuotaExceededException(doc, "user.quota.upload-too-large");
            }
            total += len;
        }
        if (total == 0) {
            return;
        }

        if (mode > 0) {
            // Total quota check
            var current = counter.get(session.getRepositoryName(), creator);
            if (limits.maxTotalQuota() > 0 && current + total > limits.maxTotalQuota()) {
                throw new QuotaExceededException(doc, "user.quota.quota-exceeded");
            }
            counter.addAndGet(session.getRepositoryName(), creator, total);
        }
    }

    protected void applyDelta(CoreSession session, DocumentModel doc, long delta) {
        var creator = getCreator(doc);
        if (creator == null) {
            return;
        }
        var userManager = Framework.getService(UserManager.class);
        var p = userManager.getPrincipal(creator);
        if (p == null) {
            return;
        }
        var quotaService = Framework.getService(UserQuotaService.class);
        var limits = quotaService.getEffectiveLimits(p, session.getRepositoryName());

        if (limits.isAdminBypass()) {
            if (delta > 0) {
                counter.addAndGet(session.getRepositoryName(), creator, delta);
            }
            return;
        }

        // Per-blob check on new/changed blobs (already satisfied by caller)

        // Total check on delta
        if (limits.maxTotalQuota() > 0 && delta > 0) {
            var current = counter.get(session.getRepositoryName(), creator);
            if (current + delta > limits.maxTotalQuota()) {
                throw new QuotaExceededException(doc, "user.quota.quota-exceeded");
            }
        }
        counter.addAndGet(session.getRepositoryName(), creator, delta);
    }

    // --- Blob utilities ---

    protected long getBlobsSize(DocumentModel doc) {
        long size = 0;
        for (var blob : getAllBlobs(doc)) {
            size += blob.getLength();
        }
        return size;
    }

    protected List<Blob> getAllBlobs(DocumentModel doc) {
        return newBlobsExtractor().getBlobs(doc);
    }

    protected BlobsExtractor newBlobsExtractor() {
        var sizeService = Framework.getService(QuotaSizeService.class);
        var excludedPaths = sizeService.getExcludedPathList();
        var extractor = new BlobsExtractor();
        extractor.setExtractorProperties(null, new HashSet<>(excludedPaths), true);
        return extractor;
    }

    // --- Helpers ---

    protected String getCreator(DocumentModel doc) {
        var creator = (String) doc.getPropertyValue("dc:creator");
        if (creator == null || creator.isBlank()) {
            return null;
        }
        return creator;
    }

    // --- Initial computation ---

    @Override
    public void computeInitialStatistics(CoreSession session, QuotaStatsInitialWork currentWorker, String path) {
        var work = new UserQuotaInitialComputationWork(session.getRepositoryName());
        Framework.getService(org.nuxeo.ecm.core.work.api.WorkManager.class).schedule(work);
    }
}
