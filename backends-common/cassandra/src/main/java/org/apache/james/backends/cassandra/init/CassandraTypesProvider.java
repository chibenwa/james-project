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

package org.apache.james.backends.cassandra.init;

import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.components.CassandraType;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata;
import com.datastax.oss.driver.api.core.type.UserDefinedType;
import com.google.common.collect.ImmutableMap;

public class CassandraTypesProvider {
    private final ImmutableMap<String, UserDefinedType> userTypes;

    @Inject
    public CassandraTypesProvider(CassandraModule module, CqlSession session) {
        Map<CqlIdentifier, UserDefinedType> userDefinedTypes = session.getKeyspace().map(keyspace -> userDefinedTypes(session, keyspace))
            .orElse(ImmutableMap.of());

        userTypes = module.moduleTypes()
            .stream()
            .collect(ImmutableMap.toImmutableMap(
                    CassandraType::getName,
                    type -> userDefinedTypes.get(CqlIdentifier.fromInternal(type.getName()))));
    }

    public Map<CqlIdentifier, UserDefinedType> userDefinedTypes(CqlSession session, CqlIdentifier keyspace) {
        KeyspaceMetadata keyspaceMetadata = session.getMetadata().getKeyspaces().get(keyspace);
        return keyspaceMetadata.getUserDefinedTypes();
    }

    public UserDefinedType getDefinedUserType(String typeName) {
        return Optional.ofNullable(userTypes.get(typeName))
            .orElseThrow(() -> new RuntimeException("Cassandra UDT " + typeName + " can not be retrieved"));
    }

}
