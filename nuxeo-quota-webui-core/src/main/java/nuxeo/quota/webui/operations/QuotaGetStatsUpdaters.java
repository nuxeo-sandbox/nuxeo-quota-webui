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

import org.json.JSONArray;
import org.json.JSONObject;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.quota.QuotaStatsService;

/**
 * Returns the list of registered {@code QuotaStatsUpdater}s as a JSON array (name, label,
 * description) for the admin UI dropdown.
 *
 * @since 2025.1
 */
@Operation(id = QuotaGetStatsUpdaters.ID, category = "Quotas", label = "Quota: Get Stats Updaters", description = "Get the updaters (to display in the UI)")
public class QuotaGetStatsUpdaters {

    /** @since 2025.1 */
    public static final String ID = "Quota.GetStatsUpdaters";

    @Context
    QuotaStatsService quotaStatsService;

    @OperationMethod
    public Blob run() {
        var updaters = quotaStatsService.getQuotaStatsUpdaters();
        var jsonArr = new JSONArray();
        for (var updater : updaters) {
            var jsonObj = new JSONObject();
            jsonObj.put("name", updater.getName());
            jsonObj.put("label", updater.getLabel());
            jsonObj.put("description", updater.getDescriptionLabel());
            jsonArr.put(jsonObj);
        }

        return Blobs.createJSONBlob(jsonArr.toString());
    }
}
