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
package nuxeo.quota.webui.operations.user;

import static nuxeo.quota.webui.QuotaUtils.ensureAdmin;

import org.json.JSONArray;
import org.json.JSONObject;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.runtime.api.Framework;

import nuxeo.quota.webui.user.UserQuotaDescriptor;
import nuxeo.quota.webui.user.UserQuotaOverrideStore;
import nuxeo.quota.webui.user.UserQuotaService;

/**
 * Returns all user-quota configuration: XML defaults, group overrides, user overrides.
 *
 * @since 2025.1
 */
@Operation(id = QuotaUserGetConfiguration.ID, category = "Quotas", label = "Quota.User.GetConfiguration", description = ""
        + "Return the per-user quota configuration (XML defaults, group overrides, user overrides).")
public class QuotaUserGetConfiguration {

    public static final String ID = "Quota.User.GetConfiguration";

    @Context
    protected CoreSession session;

    @OperationMethod
    public Blob run() {
        ensureAdmin(session);

        var service = Framework.getService(UserQuotaService.class);
        var store = service.getOverrideStore();
        var repo = session.getRepositoryName();

        var json = new JSONObject();

        // XML defaults
        var xmlDefaults = new JSONArray();
        for (var d : service.getXmlDefaults()) {
            var obj = new JSONObject();
            obj.put("group", d.getGroup());
            obj.put("maxUploadSize", d.getMaxUploadSize() != null ? d.getMaxUploadSize() : JSONObject.NULL);
            obj.put("maxTotalQuota", d.getMaxTotalQuota() != null ? d.getMaxTotalQuota() : JSONObject.NULL);
            xmlDefaults.put(obj);
        }
        json.put("xmlDefaults", xmlDefaults);

        // Group overrides
        var groupOverrides = new JSONArray();
        var gos = store.listGroupOverrides(repo);
        for (var entry : gos.entrySet()) {
            var obj = new JSONObject();
            obj.put("group", entry.getKey());
            for (var kv : entry.getValue().entrySet()) {
                obj.put(kv.getKey(), kv.getValue());
            }
            groupOverrides.put(obj);
        }
        json.put("groupOverrides", groupOverrides);

        // User overrides
        var userOverrides = new JSONArray();
        var uos = store.listUserOverrides(repo);
        for (var entry : uos.entrySet()) {
            var obj = new JSONObject();
            obj.put("username", entry.getKey());
            for (var kv : entry.getValue().entrySet()) {
                obj.put(kv.getKey(), kv.getValue());
            }
            userOverrides.put(obj);
        }
        json.put("userOverrides", userOverrides);

        return Blobs.createJSONBlob(json.toString());
    }
}
