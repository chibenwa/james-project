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

package org.apache.james.jmap.draft.methods;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.jmap.draft.json.ObjectMapperFactory;
import org.apache.james.jmap.draft.model.InvocationResponse;
import org.apache.james.jmap.draft.model.Property;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.PropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import reactor.core.publisher.Flux;

public class JmapResponseWriterImpl implements JmapResponseWriter {
    public static final String PROPERTIES_FILTER = "propertiesFilter";
    
    private final ObjectMapperFactory objectMapperFactory;
    private final Cache<Long, ObjectMapper> writerCache;

    @Inject
    public JmapResponseWriterImpl(ObjectMapperFactory objectMapperFactory) {
        this.objectMapperFactory = objectMapperFactory;

        writerCache = CacheBuilder.newBuilder()
            .maximumSize(128)
            .expireAfterAccess(Duration.ofMinutes(15))
            .build();
    }

    @Override
    public Flux<InvocationResponse> formatMethodResponse(Flux<JmapResponse> jmapResponses) {
        return jmapResponses.map(Throwing.function(jmapResponse -> {
            ObjectMapper objectMapper = configuredObjectMapper(jmapResponse);

            return new InvocationResponse(
                    jmapResponse.getResponseName(),
                    objectMapper.valueToTree(jmapResponse.getResponse()),
                    jmapResponse.getMethodCallId());
        }));
    }
    
    private ObjectMapper configuredObjectMapper(JmapResponse jmapResponse) throws ExecutionException {
        return writerCache.get(computeCachingKey(jmapResponse), () -> {
            FilterProvider filterProvider = jmapResponse
                .getFilterProvider()
                .map(Pair::getValue)
                .orElseGet(SimpleFilterProvider::new)
                .setDefaultFilter(SimpleBeanPropertyFilter.serializeAll())
                .addFilter(PROPERTIES_FILTER, getPropertiesFilter(jmapResponse.getProperties()));

            return objectMapperFactory.forWriting().setFilterProvider(filterProvider);
        });
    }

    private long computeCachingKey(JmapResponse jmapResponse) {
        long lowBits = jmapResponse.getProperties().hashCode();
        long highBits = jmapResponse.getFilterProvider()
            .map(Pair::getKey)
            .map(i -> (long) i)
            .orElse((long) jmapResponse.getResponseName().hashCode());

        return lowBits + (highBits >> 32);
    }
    
    private PropertyFilter getPropertiesFilter(Optional<? extends Set<? extends Property>> properties) {
        return properties
                .map(this::toFieldNames)
                .map(SimpleBeanPropertyFilter::filterOutAllExcept)
                .orElse(SimpleBeanPropertyFilter.serializeAll());
    }
    
    private Set<String> toFieldNames(Set<? extends Property> properties) {
        return properties.stream()
            .map(Property::asFieldName)
            .collect(Guavate.toImmutableSet());
    }
}
