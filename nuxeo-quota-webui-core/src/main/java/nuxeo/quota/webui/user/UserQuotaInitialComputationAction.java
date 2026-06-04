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
package nuxeo.quota.webui.user;

import static org.nuxeo.lib.stream.computation.AbstractComputation.INPUT_1;
import static org.nuxeo.lib.stream.computation.AbstractComputation.OUTPUT_1;

import java.util.Arrays;
import java.util.Map;

import org.nuxeo.lib.stream.computation.Topology;
import org.nuxeo.runtime.stream.StreamProcessorTopology;

/**
 * Bulk Action Framework action for per-user quota initial computation and recompute.
 * <p>
 * Supports optional scoping to specific users via the {@code users} command parameter.
 *
 * @since 2025.1
 */
public class UserQuotaInitialComputationAction implements StreamProcessorTopology {

    public static final String ACTION_NAME = "userQuotaInitialComputation";

    public static final String ACTION_FULL_NAME = "bulk/" + ACTION_NAME;

    public static final String PARAM_USERS = "users";

    public static final String PARAM_TRIGGERED_BY = "triggeredBy";

    @Override
    public Topology getTopology(Map<String, String> options) {
        return Topology.builder()
                .addComputation(UserQuotaInitialComputationComputation::new,
                        Arrays.asList(INPUT_1 + ":" + ACTION_FULL_NAME, OUTPUT_1 + ":status"))
                .build();
    }
}
