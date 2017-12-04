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

package org.apache.james.mailets.configuration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Optional;

import org.apache.commons.io.IOUtils;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.google.common.base.Preconditions;

public class SmtpConfiguration implements SerializableAsXml {
    public static final boolean AUTH_REQUIRED = true;
    public static final SmtpConfiguration DEFAULT = SmtpConfiguration.builder().build();

    public static class Builder {
        private Optional<Boolean> authRequired;
        private Optional<Boolean> verifyIndentity;
        private Optional<String> authorizedAddresses;

        public Builder() {
            authorizedAddresses = Optional.empty();
            authRequired = Optional.empty();
            verifyIndentity = Optional.empty();
        }

        public Builder withAutorizedAddresses(String authorizedAddresses) {
            Preconditions.checkNotNull(authorizedAddresses);
            this.authorizedAddresses = Optional.of(authorizedAddresses);
            return this;
        }

        public Builder requireAuthentication() {
            this.authRequired = Optional.of(AUTH_REQUIRED);
            return this;
        }

        public Builder verifyIdentity() {
            this.verifyIndentity = Optional.of(true);
            return this;
        }

        public Builder doNotVerifyIdentity() {
            this.verifyIndentity = Optional.of(false);
            return this;
        }

        public SmtpConfiguration build() {
            return new SmtpConfiguration(authorizedAddresses, authRequired.orElse(!AUTH_REQUIRED), verifyIndentity.orElse(true));
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final Optional<String> authorizedAddresses;
    private final boolean authRequired;
    private final boolean verifyIndentity;

    public SmtpConfiguration(Optional<String> authorizedAddresses, boolean authRequired, boolean verifyIndentity) {
        this.authorizedAddresses = authorizedAddresses;
        this.authRequired = authRequired;
        this.verifyIndentity = verifyIndentity;
    }

    public String serializeAsXml() throws IOException {
        HashMap<String, Object> scopes = new HashMap<>();
        scopes.put("hasAuthorizedAddresses", authorizedAddresses.isPresent());
        authorizedAddresses.ifPresent(value -> scopes.put("authorizedAddresses", value));
        scopes.put("authRequired", authRequired);
        scopes.put("verifyIdentity", verifyIndentity);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        Writer writer = new OutputStreamWriter(byteArrayOutputStream);
        MustacheFactory mf = new DefaultMustacheFactory();
        Mustache mustache = mf.compile(getPatternReader(), "example");
        mustache.execute(writer, scopes);
        writer.flush();
        return new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8);
    }

    private StringReader getPatternReader() throws IOException {
        InputStream patternStream = ClassLoader.getSystemResourceAsStream("smtpserver.xml");
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        IOUtils.copy(patternStream, byteArrayOutputStream);
        String pattern = new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8);
        return new StringReader(pattern);
    }
}
