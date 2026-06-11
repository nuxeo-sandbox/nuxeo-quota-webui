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
package nuxeo.quota.webui;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.nuxeo.common.utils.SizeUtils;
import org.nuxeo.runtime.api.Framework;

/**
 * Reads the configured max quota size from {@code nuxeo.conf} and exposes it as a JSON payload
 * for the admin and per-container UI.
 *
 * @since 2025.1
 */
public class QuotaConfigInfo {

    private static final Logger log = LogManager.getLogger(QuotaConfigInfo.class);

    /** @since 2025.1 */
    public static final String QUOTA_MAX_SIZE_PROP = "nuxeo.quota.maxsize";

    /** @since 2025.1 */
    public static final String QUOTA_MAX_SIZE_DEFAULT = "999 GB";

    /** @since 2025.1 */
    public static JSONObject getMaxQuotaSize() {

        var maxSizeJson = new JSONObject();

        long configuredMaxQuotaSize;
        // Max size and possible other config. parameters
        var maxSizeStr = Framework.getProperty(QUOTA_MAX_SIZE_PROP, QUOTA_MAX_SIZE_DEFAULT);
        try {
            configuredMaxQuotaSize = SizeUtils.parseSizeInBytes(maxSizeStr);
        } catch (NumberFormatException e) {
            log.error("Invalid value for configuration property {}: {}; using default: {}", QUOTA_MAX_SIZE_PROP,
                    maxSizeStr, QUOTA_MAX_SIZE_DEFAULT, e);
            configuredMaxQuotaSize = SizeUtils.parseSizeInBytes(QUOTA_MAX_SIZE_DEFAULT);
        }

        maxSizeJson.put("maxQuotaSize", configuredMaxQuotaSize);
        maxSizeJson.put("maxQuotaSizeStr", maxSizeStr);

        return maxSizeJson;
    }

}
