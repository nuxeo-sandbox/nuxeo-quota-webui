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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.Serializable;

import jakarta.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features(AutomationFeature.class)
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.ecm.quota")
@Deploy("nuxeo.quota.webui.nuxeo-quota-webui-core")
@Deploy("nuxeo.quota.webui.nuxeo-quota-webui-core:OSGI-INF/test-userquota-contrib.xml")
public class TestUserQuotaUpdateDelta {

    protected static final String REPO = "test";

    @Inject
    protected CoreSession session;

    protected String getSessionUser() {
        return ((NuxeoPrincipal) session.getPrincipal()).getName();
    }

    @Test
    public void blobReplacementUpdatesCounter() throws IOException {
        var counter = new UserQuotaCounter();
        var user = getSessionUser();
        counter.set(REPO, user, 0L);

        // Create a document with a 100-byte blob
        var doc = session.createDocumentModel("/", "doc1", "File");
        doc.setPropertyValue("file:content", (Serializable) Blobs.createBlob("a".repeat(100)));
        doc = session.createDocument(doc);
        session.save();
        long afterCreate = counter.get(REPO, user);
        assertEquals("Counter should be 100 after creating doc with 100-byte blob",
                100L, afterCreate);

        // Replace the blob with a 300-byte one
        doc.setPropertyValue("file:content", (Serializable) Blobs.createBlob("b".repeat(300)));
        session.saveDocument(doc);
        session.save();
        long afterReplace = counter.get(REPO, user);
        // Expected: counter = 300 (delta of +200 was applied)
        // BUG: counter still = 100 (delta computed as 0 from old=old)
        assertEquals("Counter should be 300 after replacing blob with 300-byte one",
                300L, afterReplace);
    }

    @Test
    public void blobRemovalDecrementsCounter() throws IOException {
        var counter = new UserQuotaCounter();
        var user = getSessionUser();
        counter.set(REPO, user, 0L);

        // Create a document with a 200-byte blob
        var doc = session.createDocumentModel("/", "doc2", "File");
        doc.setPropertyValue("file:content", (Serializable) Blobs.createBlob("c".repeat(200)));
        doc = session.createDocument(doc);
        session.save();
        long afterCreate = counter.get(REPO, user);
        assertEquals("Counter should be 200 after creating doc with 200-byte blob",
                200L, afterCreate);

        // Remove the blob (set to null)
        doc.setPropertyValue("file:content", null);
        session.saveDocument(doc);
        session.save();
        long afterRemove = counter.get(REPO, user);
        // Expected: counter should be 0 (delta of -200), but the old code reads
        // the already-updated doc and sees oldSize = 0, so delta = 0
        assertEquals("Counter should be 0 after removing the blob",
                0L, afterRemove);
    }
}
