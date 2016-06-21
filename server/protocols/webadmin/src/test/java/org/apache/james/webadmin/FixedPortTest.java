/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.webadmin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;

public class FixedPortTest {

    @Test
    public void getShouldThrowOnNegativePort() {
        assertThatThrownBy(() -> new FixedPort(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void getShouldThrowOnNullPort() {
        assertThatThrownBy(() -> new FixedPort(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void getShouldThrowOnTooBigNumbers() {
        assertThatThrownBy(() -> new FixedPort(65536)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void getShouldReturnedDesiredPort() {
        int expectedPort = 452;
        assertThat(new FixedPort(expectedPort).get()).isEqualTo(expectedPort);
    }

}
