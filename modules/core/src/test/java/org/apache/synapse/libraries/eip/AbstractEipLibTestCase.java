/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.synapse.libraries.eip;

import junit.framework.TestCase;
import org.apache.synapse.libraries.imports.SynapseImport;
import org.apache.synapse.libraries.model.Library;
import org.apache.synapse.libraries.util.LibDeployerUtils;
import org.apache.synapse.mediators.eip.AbstractSplitMediatorTestCase;

import java.io.File;
import java.net.URISyntaxException;

public abstract class AbstractEipLibTestCase extends TestCase {
    public String path = null;

    protected String getResourcePath() {
        try {
            if (path == null) {
                path = new File("./target/test_repos/synapse/synapse-libraries/synapse-eiptest-lib.zip").getAbsolutePath();
            }
        } catch (Exception e) {
            return null;
        }
        return path;

    }
}
