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
 */package nuxeo.quota.webui.operations;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.quota.QuotaStatsService;
import org.nuxeo.ecm.quota.QuotaStatsUpdater;

/**
 *
 */
@Operation(id=QuotaGetStatsUpdaters.ID, category="Quotas", label="Quota: Get Stats Updaters", description="Get the updaters (to display in the UI)")
public class QuotaGetStatsUpdaters {

    public static final String ID = "Quota.GetStatsUpdaters";
    
    @Context
    QuotaStatsService quotaStatsService;

    @OperationMethod
    public Blob run() {
        List<QuotaStatsUpdater> updaters = quotaStatsService.getQuotaStatsUpdaters();
        JSONArray jsonArr = new JSONArray();
        for(QuotaStatsUpdater updater : updaters) {
            JSONObject jsonObj = new JSONObject();
            jsonObj.put("name", updater.getName());
            jsonObj.put("label", updater.getLabel());
            jsonObj.put("description",  updater.getDescriptionLabel());
            jsonArr.put(jsonObj);
        }
        
        return Blobs.createJSONBlob(jsonArr.toString());
    }
}
