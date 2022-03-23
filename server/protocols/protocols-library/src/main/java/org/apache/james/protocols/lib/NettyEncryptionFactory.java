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

package org.apache.james.protocols.lib;

import java.util.Optional;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.protocols.netty.Encryption;

import com.google.common.collect.ImmutableList;

import io.netty.channel.Channel;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import nl.altindag.ssl.SSLFactory;
import nl.altindag.ssl.util.PemUtils;

public class NettyEncryptionFactory implements Encryption.Factory {
    static class NettyEncryption implements Encryption {
        private final SslConfig sslConfig;
        private final SslContext context;

        NettyEncryption(SslConfig sslConfig, SslContext context) {
            this.sslConfig = sslConfig;
            this.context = context;
        }

        @Override
        public boolean isStartTLS() {
            return sslConfig.useStartTLS();
        }

        @Override
        public boolean supportsEncryption() {
            return true;
        }

        @Override
        public String[] getEnabledCipherSuites() {
            return sslConfig.getEnabledCipherSuites();
        }

        @Override
        public org.apache.james.protocols.api.ClientAuth getClientAuth() {
            return sslConfig.getClientAuth();
        }

        @Override
        public SslHandler sslHandler(Channel channel) {
            SSLEngine engine = context.newEngine(channel.alloc());
            // We need to set clientMode to false.
            // See https://issues.apache.org/jira/browse/JAMES-1025
            engine.setUseClientMode(false);
            return new SslHandler(engine);
        }
    }

    private final FileSystem fileSystem;
    private final SslConfig sslConfig;

    public NettyEncryptionFactory(FileSystem fileSystem, SslConfig sslConfig) {
        this.fileSystem = fileSystem;
        this.sslConfig = sslConfig;
    }

    @Override
    public Encryption create() throws Exception {
       SslContextBuilder contextBuilder = loadKeys();

       switch (sslConfig.getImplementation()) {
           case NETTY_NIO:
               contextBuilder.sslProvider(SslProvider.JDK);
               break;
           case NETTY_NATIVE:
               contextBuilder.sslProvider(SslProvider.OPENSSL);
               break;
       }

        if (sslConfig.getClientAuth() != null && sslConfig.getTruststore() != null) {
            switch (sslConfig.getClientAuth()) {
                case NEED:
                    contextBuilder.clientAuth(ClientAuth.REQUIRE);
                    break;
                case NONE:
                    contextBuilder.clientAuth(ClientAuth.NONE);
                    break;
                case WANT:
                    contextBuilder.clientAuth(ClientAuth.OPTIONAL);
                    break;
            }
            SSLFactory.Builder sslFactoryBuilder = SSLFactory.builder()
                .withSslContextAlgorithm("TLS");
            sslFactoryBuilder.withTrustMaterial(
                fileSystem.getFile(sslConfig.getTruststore()).toPath(),
                sslConfig.getTruststoreSecret(),
                sslConfig.getKeystoreType());
            contextBuilder.trustManager(sslFactoryBuilder.build().getTrustManager().get());
        }

        Optional.ofNullable(sslConfig.getEnabledCipherSuites())
            .ifPresent(ciphers -> contextBuilder.ciphers(ImmutableList.copyOf(ciphers)));

        return new NettyEncryption(sslConfig, contextBuilder.build());
    }

    private SslContextBuilder loadKeys() throws Exception {
        if (sslConfig.getKeystore() != null) {
            char[] passwordAsCharArray = Optional.ofNullable(sslConfig.getSecret())
                .orElse("")
                .toCharArray();
            SSLFactory.Builder sslFactoryBuilder = SSLFactory.builder()
                .withSslContextAlgorithm("TLS");
            sslFactoryBuilder.withIdentityMaterial(
                fileSystem.getFile(sslConfig.getKeystore()).toPath(),
                passwordAsCharArray,
                passwordAsCharArray,
                sslConfig.getKeystoreType());

            return SslContextBuilder.forServer(sslFactoryBuilder.build().getKeyManager().get());
        } else {
            X509ExtendedKeyManager keyManager = PemUtils.loadIdentityMaterial(
                fileSystem.getResource(sslConfig.getCertificates()),
                fileSystem.getResource(sslConfig.getPrivateKey()),
                Optional.ofNullable(sslConfig.getSecret())
                    .map(String::toCharArray)
                    .orElse(null));

            return SslContextBuilder.forServer(keyManager);
        }
    }
}
