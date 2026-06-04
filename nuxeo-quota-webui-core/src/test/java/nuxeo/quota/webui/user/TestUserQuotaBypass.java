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
import static org.junit.Assert.assertTrue;

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
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features(AutomationFeature.class)
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy("org.nuxeo.ecm.quota")
@Deploy("nuxeo.quota.webui.nuxeo-quota-webui-core")
@Deploy("nuxeo.quota.webui.nuxeo-quota-webui-core:OSGI-INF/test-userquota-contrib.xml")
public class TestUserQuotaBypass {

    protected static final String REPO = "test";

    @Inject
    protected CoreSession session;

    protected UserQuotaService getService() {
        return Framework.getService(UserQuotaService.class);
    }

    protected String getSessionUser() {
        return ((NuxeoPrincipal) session.getPrincipal()).getName();
    }

    @Test
    public void serviceStartsEnabledByDefault() {
        assertTrue(getService().isEnabled());
    }

    @Test
    public void programmaticDisableToggle() {
        var svc = getService();
        svc.setEnabled(false);
        assertEquals(false, svc.isEnabled());
        svc.setEnabled(true);
        assertTrue(svc.isEnabled());
    }

    @Test
    public void disabledListenerSkipsCounterUpdate() {
        var counter = new UserQuotaCounter();
        var svc = getService();
        var user = getSessionUser();

        // Reset counter for this user before test
        counter.set(REPO, user, 0L);

        // Create doc while enabled (dc:creator is set to session user by Nuxeo)
        svc.setEnabled(true);
        var doc1 = session.createDocumentModel("/", "doc1", "File");
        doc1.setPropertyValue("file:content", (Serializable) Blobs.createBlob("x".repeat(100)));
        doc1 = session.createDocument(doc1);
        session.save();
        long afterEnabled = counter.get(REPO, user);
        assertTrue("Counter should be > 0 with listener enabled", afterEnabled > 0);

        // Create doc while disabled
        svc.setEnabled(false);
        var doc2 = session.createDocumentModel("/", "doc2", "File");
        doc2.setPropertyValue("file:content", (Serializable) Blobs.createBlob("y".repeat(200)));
        session.createDocument(doc2);
        session.save();
        long afterDisabled = counter.get(REPO, user);
        assertEquals("Counter should not change when listener is disabled",
                afterEnabled, afterDisabled);

        svc.setEnabled(true);
    }

    @Test
    public void contextDataFlagSkipsCounterUpdate() {
        var counter = new UserQuotaCounter();
        var user = getSessionUser();

        // Reset counter for this user before test
        counter.set(REPO, user, 0L);

        // Create doc with context-data bypass flag
        var doc = session.createDocumentModel("/", "docFlagged", "File");
        doc.setPropertyValue("file:content", (Serializable) Blobs.createBlob("z".repeat(150)));
        doc.putContextData("disableQuotaListener", Boolean.TRUE);
        doc = session.createDocument(doc);
        session.save();
        long afterFlagged = counter.get(REPO, user);
        assertEquals("Counter should be 0 when context-data bypass flag is set",
                0L, afterFlagged);

        // Verify flag absence allows counting
        var docOk = session.createDocumentModel("/", "docOk", "File");
        docOk.setPropertyValue("file:content", (Serializable) Blobs.createBlob("w".repeat(75)));
        session.createDocument(docOk);
        session.save();
        long afterOk = counter.get(REPO, user);
        assertTrue("Counter should be > 0 without the bypass flag", afterOk > 0);
    }
}
