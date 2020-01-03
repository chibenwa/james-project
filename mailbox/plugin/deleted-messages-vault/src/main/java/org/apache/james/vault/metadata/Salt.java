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

package org.apache.james.vault.metadata;

import java.util.Objects;

import org.apache.commons.text.RandomStringGenerator;

import com.google.common.base.Preconditions;

public class Salt {
    public interface Factory {
        RandomStringGenerator RANDOM_STRING_GENERATOR = new RandomStringGenerator.Builder().withinRange('a', 'z').build();
        int LENGTH = 32;

        Factory RANDOM = () -> new Salt(RANDOM_STRING_GENERATOR.generate(LENGTH));

        Salt generate();
    }

    private final String value;

    public Salt(String value) {
        Preconditions.checkNotNull(value);

        this.value = value;
    }

    public String asString() {
        return value;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof Salt) {
            Salt salt = (Salt) o;

            return Objects.equals(this.value, salt.value);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(value);
    }
}
