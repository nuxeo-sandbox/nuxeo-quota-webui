/*
 * (C) Copyright 2023 Hyland (http://hyland.com/)  and others.
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
package nuxeo.quota.webui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ecm.quota.count.Constants.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.quota.QuotaStatsInitialWork;
import org.nuxeo.ecm.quota.QuotaStatsService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;
import org.nuxeo.runtime.transaction.TransactionHelper;

import nuxeo.quota.webui.operations.QuotaLaunchInitialComputation;

@RunWith(FeaturesRunner.class)
@Features(AutomationFeature.class)
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.ecm.quota")
@Deploy("nuxeo.quota.webui.nuxeo-quota-webui-core")
public class TestQuotaLaunchInitialComputation {

    @Inject
    protected CoreSession session;

    @Inject
    protected AutomationService automationService;

    @Inject
    protected TransactionalFeature txFeature;

    @Inject
    protected QuotaStatsService quotaStatsService;

    // All the following variables are copy/paste the nuxeo-quota test source code
    DocumentRef wsRef;

    DocumentRef firstFolderRef;

    DocumentRef secondFolderRef;

    DocumentRef firstSubFolderRef;

    DocumentRef secondSubFolderRef;

    DocumentRef firstFileRef;

    DocumentRef secondFileRef;

    // This is copy/paste the nuxeo-quota test source code
    @Before
    public void addFiles() {
        session.save();
        DocumentModel ws = session.createDocumentModel("/", "ws", "Workspace");
        ws = session.createDocument(ws);
        wsRef = ws.getRef();

        DocumentModel firstFolder = session.createDocumentModel(ws.getPathAsString(), "folder1", "Folder");
        firstFolder = session.createDocument(firstFolder);
        firstFolderRef = firstFolder.getRef();

        DocumentModel firstSubFolder = session.createDocumentModel(firstFolder.getPathAsString(), "subfolder1",
                "Folder");
        firstSubFolder = session.createDocument(firstSubFolder);
        firstSubFolderRef = firstSubFolder.getRef();

        DocumentModel firstFile = session.createDocumentModel(firstSubFolder.getPathAsString(), "file1", "File");
        firstFile = session.createDocument(firstFile);
        firstFileRef = firstFile.getRef();

        DocumentModel secondFile = session.createDocumentModel(firstSubFolder.getPathAsString(), "file2", "File");
        secondFile = session.createDocument(secondFile);
        secondFileRef = secondFile.getRef();

        DocumentModel secondSubFolder = session.createDocumentModel(firstFolder.getPathAsString(), "subfolder2",
                "Folder");
        secondSubFolder = session.createDocument(secondSubFolder);
        secondSubFolderRef = secondSubFolder.getRef();

        DocumentModel secondFolder = session.createDocumentModel(ws.getPathAsString(), "folder2", "Folder");
        secondFolder = session.createDocument(secondFolder);
        session.save();
        secondFolderRef = secondFolder.getRef();
    }

    // This is copy/paste the nuxeo-quota test source code
    protected static void assertHasCountFacet(DocumentModel doc) {
        assertTrue(doc.hasFacet(DOCUMENTS_COUNT_STATISTICS_FACET));
    }

    // This is copy/paste the nuxeo-quota test source code
    protected static void assertHasNoCountFacet(DocumentModel doc) {
        assertFalse(doc.hasFacet(DOCUMENTS_COUNT_STATISTICS_FACET));
    }

    // This is copy/paste the nuxeo-quota test source code
    protected static void assertDescendantsCount(long expected, DocumentModel doc) {
        assertHasCountFacet(doc);
        assertEquals(expected,
                ((Number) doc.getPropertyValue(DOCUMENTS_COUNT_STATISTICS_DESCENDANTS_COUNT_PROPERTY)).longValue());
    }

    // This is copy/paste the nuxeo-quota test source code
    // We just removed @Test and made it protected
    protected void testDocumentsCount() throws Exception {
        DocumentModel firstSubFolder = session.getDocument(firstSubFolderRef);
        assertDescendantsCount(2, firstSubFolder);

        DocumentModel secondSubFolder = session.getDocument(secondSubFolderRef);
        assertHasNoCountFacet(secondSubFolder);

        DocumentModel ws = session.getDocument(wsRef);
        assertDescendantsCount(2, ws);
    }

    @Test
    public void testServiceRegistration() {
        assertNotNull(quotaStatsService);
    }

    @Test
    public void shouldLaunchInitialComputation() throws Exception {
        List<DocumentModel> folders = session.query("SELECT * FROM Document where ecm:mixinType = 'Folderish'");
        for (DocumentModel folder : folders) {
            if (folder.hasFacet("DocumentsCountStatistics")) {
                folder.removeFacet("DocumentsCountStatistics");
                session.saveDocument(folder);
            }
        }

        OperationContext ctx = new OperationContext(session);
        Map<String, Object> params = new HashMap<>();
        params.put("updaterName", "documentsCountUpdater");
        automationService.run(ctx, QuotaLaunchInitialComputation.ID, params);

        // This also is copy/paste from unit test of nuxeo-quota :-)
        TransactionHelper.commitOrRollbackTransaction();
        WorkManager workManager = Framework.getService(WorkManager.class);
        String queueId = workManager.getCategoryQueueId(QuotaStatsInitialWork.CATEGORY_QUOTA_INITIAL);
        assertEquals("quota", queueId);
        
        workManager.awaitCompletion(queueId, 10, TimeUnit.SECONDS);

        TransactionHelper.startTransaction();

        testDocumentsCount();

    }
}
