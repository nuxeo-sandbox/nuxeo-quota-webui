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

/**
 * Misc configuration helpers for the quota plugin.
 * <p>
 * The max-quota size is parsed once and cached; the underlying framework property
 * ({@value #QUOTA_MAX_SIZE_PROP}) only changes across restarts.
 */
public class QuotaConfigInfo {

    private static final Logger log = LogManager.getLogger(QuotaConfigInfo.class);

    public static final String QUOTA_MAX_SIZE_PROP = "nuxeo.quota.maxsize";

    public static final String QUOTA_MAX_SIZE_DEFAULT = "999 GB";

    // Cached parsed values. volatile for safe publication; the values come from a
    // framework property that only changes across restarts.
    private static volatile Long cachedMaxQuotaSize;

    private static volatile String cachedMaxQuotaSizeStr;

    public static JSONObject getMaxQuotaSize() {
        var bytes = cachedMaxQuotaSize;
        var str = cachedMaxQuotaSizeStr;
        if (bytes == null || str == null) {
            var maxSizeStr = Framework.getProperty(QUOTA_MAX_SIZE_PROP, QUOTA_MAX_SIZE_DEFAULT);
            long configuredMaxQuotaSize;
            try {
                configuredMaxQuotaSize = SizeUtils.parseSizeInBytes(maxSizeStr);
            } catch (NumberFormatException e) {
                log.error("Invalid value for configuration property {}: {}; using default: {}",
                        QUOTA_MAX_SIZE_PROP, maxSizeStr, QUOTA_MAX_SIZE_DEFAULT);
                configuredMaxQuotaSize = SizeUtils.parseSizeInBytes(QUOTA_MAX_SIZE_DEFAULT);
                maxSizeStr = QUOTA_MAX_SIZE_DEFAULT;
            }
            cachedMaxQuotaSize = configuredMaxQuotaSize;
            cachedMaxQuotaSizeStr = maxSizeStr;
            bytes = configuredMaxQuotaSize;
            str = maxSizeStr;
        }

        var maxSizeJson = new JSONObject();
        maxSizeJson.put("maxQuotaSize", bytes.longValue());
        maxSizeJson.put("maxQuotaSizeStr", str);
        return maxSizeJson;
    }

    /**
     * Invalidates the cached max-quota-size value. Primarily for tests; the production
     * value comes from a framework property and doesn't change at runtime.
     *
     * @since 2025.1
     */
    public static void invalidateCache() {
        cachedMaxQuotaSize = null;
        cachedMaxQuotaSizeStr = null;
    }

}
