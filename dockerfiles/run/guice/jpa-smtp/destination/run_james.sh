#!/usr/bin/env bash
if [ "$GLOWROOT_ACTIVATED" == "true" ]; then
    GLOWROOT_OPTIONS=-javaagent:/root/glowroot/glowroot.jar
fi

java -classpath '/root/james-server-jpa-smtp-guice.jar:/root/james-server-jpa-smtp-guice.lib/*' \
  -javaagent:/root/james-server-jpa-smtp-guice.lib/openjpa-3.1.0.jar \
  $GLOWROOT_OPTIONS \
  -Dlogback.configurationFile=/root/conf/logback.xml \
  -Dworking.directory=/root/ \
  -Djdk.tls.client.protocols="TLSv1,TLSv1.1,TLSv1.2" \
  $JVM_OPTIONS \
   org.apache.james.JPAJamesServerMain