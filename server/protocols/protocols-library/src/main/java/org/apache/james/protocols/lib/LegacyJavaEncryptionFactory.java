package org.apache.james.protocols.lib;

import java.util.Optional;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509ExtendedKeyManager;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.protocols.netty.Encryption;

import nl.altindag.ssl.SSLFactory;
import nl.altindag.ssl.util.PemUtils;

public class LegacyJavaEncryptionFactory implements Encryption.Factory {
    private final FileSystem fileSystem;
    private final SslConfig sslConfig;

    public LegacyJavaEncryptionFactory(FileSystem fileSystem, SslConfig sslConfig) {
        this.fileSystem = fileSystem;
        this.sslConfig = sslConfig;
    }

    @Override
    public Encryption create() throws Exception {
        SSLFactory.Builder sslFactoryBuilder = SSLFactory.builder()
            .withSslContextAlgorithm("TLS");
        if (sslConfig.getKeystore() != null) {
            char[] passwordAsCharArray = Optional.ofNullable(sslConfig.getSecret())
                .orElse("")
                .toCharArray();
            sslFactoryBuilder.withIdentityMaterial(
                fileSystem.getFile(sslConfig.getKeystore()).toPath(),
                passwordAsCharArray,
                passwordAsCharArray,
                sslConfig.getKeystoreType());
        } else {
            X509ExtendedKeyManager keyManager = PemUtils.loadIdentityMaterial(
                fileSystem.getResource(sslConfig.getCertificates()),
                fileSystem.getResource(sslConfig.getPrivateKey()),
                Optional.ofNullable(sslConfig.getSecret())
                    .map(String::toCharArray)
                    .orElse(null));

            sslFactoryBuilder.withIdentityMaterial(keyManager);
        }

        if (sslConfig.getClientAuth() != null && sslConfig.getTruststore() != null) {
            sslFactoryBuilder.withTrustMaterial(
                fileSystem.getFile(sslConfig.getTruststore()).toPath(),
                sslConfig.getTruststoreSecret(),
                sslConfig.getKeystoreType());
        }

        SSLContext context = sslFactoryBuilder.build().getSslContext();

        if (sslConfig.useStartTLS()) {
            return Encryption.createStartTls(context, sslConfig.getEnabledCipherSuites(), sslConfig.getClientAuth());
        } else {
           return Encryption.createTls(context, sslConfig.getEnabledCipherSuites(), sslConfig.getClientAuth());
        }
    }
}
