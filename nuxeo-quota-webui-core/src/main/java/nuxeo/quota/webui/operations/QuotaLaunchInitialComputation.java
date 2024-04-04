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

import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.quota.QuotaStatsService;

/**
 *
 */
@Operation(id = QuotaLaunchInitialComputation.ID, category = "Quotas", label = "Quota: Launch Initial Computation", description = "Launch the (asynchronous) computation for the updater passed as parameter.")
public class QuotaLaunchInitialComputation {

    public static final String ID = "Quota.LaunchInitialComputation";

    @Context
    protected CoreSession session;

    @Context
    QuotaStatsService quotaStatsService;

    @Param(name = "updaterName", required = true)
    protected String updaterName;

    @OperationMethod
    public void run() {

        quotaStatsService.launchInitialStatisticsComputation(updaterName, session.getRepositoryName(), null);

    }
}
