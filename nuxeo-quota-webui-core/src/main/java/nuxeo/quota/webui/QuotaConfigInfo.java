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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.nuxeo.common.utils.SizeUtils;
import org.nuxeo.runtime.api.Framework;

public class QuotaConfigInfo {

    private static final Logger log = LogManager.getLogger(QuotaConfigInfo.class);

    public static final String QUOTA_MAX_SIZE_PROP = "nuxeo.quota.maxsize";

    public static final String QUOTA_MAX_SIZE_DEFAULT = "999 GB";

    public static JSONObject getMaxQuotaSize() {

        JSONObject maxSizeJson = new JSONObject();

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

        maxSizeJson.put("maxQuotaSize", configuredMaxQuotaSize);
        maxSizeJson.put("maxQuotaSizeStr", maxSizeStr);

        return maxSizeJson;
    }

}
