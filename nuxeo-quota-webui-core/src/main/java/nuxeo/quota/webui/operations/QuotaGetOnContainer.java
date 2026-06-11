/*
 * (C) Copyright 2026 Hyland (http://hyland.com/)  and others.
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

import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

import org.json.JSONObject;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.quota.size.QuotaAware;

import nuxeo.quota.webui.QuotaConfigInfo;

/**
 * Returns the quota currently set on the input container, plus the max possible size.
 * <p>
 * Returns {@code -1} as the {@code quotaValue} for {@code UserWorkspacesRoot}, mirroring the
 * deprecated JSF action {@code QuotaStatsAction#isQuotaSetOnCurrentDocument}.
 *
 * @since 2025.1
 */
@Operation(id = QuotaGetOnContainer.ID, category = "Quotas", label = "Quota: Get Container Quota", description = "Return the quota info on the input container. quotaValue <= 0 means no quota is set. Also return info on the max possible size.")
public class QuotaGetOnContainer {

    /** @since 2025.1 */
    public static final String ID = "Quota.GetContainerQuota";

    @Context
    protected CoreSession session;

    @OperationMethod
    public Blob run(DocumentModel input) {

        if (input == null) {
            throw new NuxeoException("An input document is required", SC_BAD_REQUEST);
        }

        var jsonObj = new JSONObject();

        long maxSize = -1;
        // This is what original JSF action is doing (QuotaStatsAction#isQuotaSetOnCurrentDocument)
        // the quota info set on the userworkspaces root should be ignored
        if ("UserWorkspacesRoot".equals(input.getType())) {
            maxSize = -1;
        } else {
            var qa = input.getAdapter(QuotaAware.class);
            if (qa != null) {
                maxSize = qa.getMaxQuota();
            }
        }
        jsonObj.put("quotaValue", maxSize);

        var maxSizeJson = QuotaConfigInfo.getMaxQuotaSize();
        jsonObj.put("maxQuotaSize", maxSizeJson.get("maxQuotaSize"));
        jsonObj.put("maxQuotaSizeStr", maxSizeJson.get("maxQuotaSizeStr"));

        return Blobs.createJSONBlob(jsonObj.toString());
    }
}
