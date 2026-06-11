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
package nuxeo.quota.webui.operations;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.json.JSONObject;
import org.nuxeo.common.utils.SizeUtils;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.core.work.api.WorkQueueMetrics;
import org.nuxeo.ecm.quota.QuotaStatsService;
import org.nuxeo.ecm.quota.size.QuotaAware;
import org.nuxeo.runtime.api.Framework;

import nuxeo.quota.webui.QuotaConfigInfo;

/**
 * We get values as they were used in the deprecated jsf QuotaStatsActions.java
 */
@Operation(id = QuotaGetOnContainer.ID, category = "Quotas", label = "Quota: Get Container Quota", description = ""
        + "Return the quota info on the input container. quotaValue <= 0 means no quota is set."
        + " Also return info on the max possible size.")
public class QuotaGetOnContainer {

    private static final Logger log = LogManager.getLogger(QuotaGetOnContainer.class);

    public static final String ID = "Quota.GetContainerQuota";

    @Context
    protected CoreSession session;

    @OperationMethod
    public Blob run(DocumentModel input) {

        JSONObject jsonObj = new JSONObject();

        if (input == null) {
            throw new IllegalArgumentException("An input document isrequired");
        }

        long maxSize = -1;
        // This is what original JSF action is doing (QuotaStatsAction#isQuotaSetOnCurrentDocument)
        // the quota info set on the userworkspaces root should be ignored
        if ("UserWorkspacesRoot".equals(input.getType())) {
            maxSize = -1;
        } else {
            QuotaAware qa = input.getAdapter(QuotaAware.class);
            if (qa != null) {
                maxSize = qa.getMaxQuota();
            }
        }
        jsonObj.put("quotaValue", maxSize);

        JSONObject maxSizeJson = QuotaConfigInfo.getMaxQuotaSize();
        jsonObj.put("maxQuotaSize", maxSizeJson.get("maxQuotaSize"));
        jsonObj.put("maxQuotaSizeStr", maxSizeJson.get("maxQuotaSizeStr"));

        return Blobs.createJSONBlob(jsonObj.toString());
    }
}
