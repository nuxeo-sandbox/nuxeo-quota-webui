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
import org.nuxeo.runtime.api.Framework;

/**
 * We get values as they were used in the deprecated jsf QuotaStatsActions.java
 */
@Operation(id=QuotaGetConfigurationAndInfo.ID, category="Quotas", label="Quota: Get Configuration", description="Return the misc configuration settings")
public class QuotaGetConfigurationAndInfo {
    
    private static final Logger log = LogManager.getLogger(QuotaGetConfigurationAndInfo.class);
        
    public static final String ID = "Quota.GetConfigurationAndInfo";
    
    public static final String QUOTA_MAX_SIZE_PROP = "nuxeo.quota.maxsize";

    public static final String QUOTA_MAX_SIZE_DEFAULT = "999 GB";

    @Context
    protected CoreSession session;
    
    @Context
    WorkManager workManager;
    
    @Context
    QuotaStatsService quotaStatsService;

    @OperationMethod
    public Blob run() {
        return run(null);
    }

    @OperationMethod
    public Blob run(DocumentModel input) {
        
        long configuredMaxQuotaSize;
        
        // Max size and possible other config. parameters
        String maxSizeStr = Framework.getProperty(QUOTA_MAX_SIZE_PROP, QUOTA_MAX_SIZE_DEFAULT);
        try {
            configuredMaxQuotaSize = SizeUtils.parseSizeInBytes(maxSizeStr);
        } catch (NumberFormatException e) {
            log.error("Invalid value for configuration property " + QUOTA_MAX_SIZE_PROP + ": " + maxSizeStr
                    + "; using default: " + QUOTA_MAX_SIZE_DEFAULT);
            configuredMaxQuotaSize = SizeUtils.parseSizeInBytes(QUOTA_MAX_SIZE_DEFAULT);
        }
        
        JSONObject jsonObj = new JSONObject();
        jsonObj.put("maxQuotaSize", configuredMaxQuotaSize);
        jsonObj.put("maxQuotaSizeStr", maxSizeStr);
        
        // Is something running?
        boolean running = false;
        WorkQueueMetrics metrics = workManager.getMetrics("quota");
        if(metrics != null) {
            running = metrics.getRunning().longValue() + metrics.getScheduled().longValue() > 0;
        }
        jsonObj.put("hasWorkInProgress", running);
        
        // Quota on UserWS (if any)
        // (-1 if not set)
        jsonObj.put("quotaSetOnUserWS", quotaStatsService.getQuotaSetOnUserWorkspaces(session));
        
        return Blobs.createJSONBlob(jsonObj.toString());
    }
}
