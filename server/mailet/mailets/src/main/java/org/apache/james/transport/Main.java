package org.apache.james.transport;

import static com.codahale.metrics.Slf4jReporter.LoggingLevel.INFO;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.mail.Flags;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.james.metrics.api.Metric;
import org.apache.james.metrics.dropwizard.DropWizardMetricFactory;
import org.apache.james.util.ClassLoaderUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Slf4jReporter;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Bytes;
import com.yahoo.imapnio.async.client.ImapAsyncClient;
import com.yahoo.imapnio.async.client.ImapAsyncCreateSessionResponse;
import com.yahoo.imapnio.async.client.ImapAsyncSession;
import com.yahoo.imapnio.async.client.ImapAsyncSessionConfig;
import com.yahoo.imapnio.async.client.ImapFuture;
import com.yahoo.imapnio.async.exception.ImapAsyncClientException;
import com.yahoo.imapnio.async.request.AppendCommand;
import com.yahoo.imapnio.async.request.CreateFolderCommand;
import com.yahoo.imapnio.async.request.ImapRequest;
import com.yahoo.imapnio.async.request.LoginCommand;
import com.yahoo.imapnio.async.response.ImapAsyncResponse;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

public class Main {
    private static final Properties PROPERTIES = retrieveProperties();
    private static final String URL = PROPERTIES.getProperty("url", "imaps://172.25.0.2:993");
    private static final int NUM_MBX = Integer.parseInt(PROPERTIES.getProperty("mailbox.count", "4"));
    private static final int NUM_MSG_REGULAR_FOLDER = Integer.parseInt(PROPERTIES.getProperty("message.per.folder.count", "5"));
    private static final int NUM_MSG_INBOX = Integer.parseInt(PROPERTIES.getProperty("message.inbox.count", "10"));
    private static final int NUM_OF_THREADS = Integer.parseInt(PROPERTIES.getProperty("thread.count", "8"));
    private static final int CONCURRENT_USERS = Integer.parseInt(PROPERTIES.getProperty("concurrent.user.count", "5"));
    private static final int NUM_CONNECTIONS_PER_USER = Integer.parseInt(PROPERTIES.getProperty("connection.per.user.count", "2"));

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    private static final String PREFIX = "folder-";
    private static final Random RANDOM = new Random();
    private static final byte[] HEADER_BYTES = ClassLoaderUtils.getSystemResourceAsByteArray("eml/headers");
    private static final byte[] HEADER_BYTES_SIMPLE = ClassLoaderUtils.getSystemResourceAsByteArray("eml/headersSimple");
    private static final byte[] BODY_START_BYTES = ClassLoaderUtils.getSystemResourceAsByteArray("eml/bodyStart");
    private static final byte[] BODY_END_BYTES = ClassLoaderUtils.getSystemResourceAsByteArray("eml/bodyEnd");

    private static final TrustManager[] TRUST_ALL_CERTS = new TrustManager[]{
        new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }

            public void checkClientTrusted(
                X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(
                X509Certificate[] certs, String authType) {
            }
        }
    };
    private static final SSLContext SSL_CONTEXT = createSslContext();
    private static final Map<Integer, List<String>> PARTITIONED_MAILBOXES = computePartitionnedMailboxes();
    private static final ImapAsyncClient IMAP_CLIENT = createImapClient();

    private static DropWizardMetricFactory dropWizardMetricFactory;
    private static Metric failedAppend;
    private static Metric failedConnect;
    private static Metric failedCreate;
    private static Metric failedAuth;
    private static Metric provisionnedUsers;

    public static void main(String... args) throws Exception {
        startMetrics();

        Iterable<CSVRecord> records = parseCSV();

        Stopwatch started = Stopwatch.createStarted();

        Flux.fromIterable(records)
            .flatMap(Main::provisionUser, CONCURRENT_USERS)
            .then()
            .block();

        LOGGER.info("Elapsed {} ms", started.elapsed(TimeUnit.MILLISECONDS));
        System.exit(0);
    }

    private static Properties retrieveProperties() {
        try {
            final Properties properties = new Properties();
            final File file = new File("provisioning.properties");
            if (file.exists()) {
                try (FileInputStream fileInputStream = new FileInputStream(file)) {
                    properties.load(fileInputStream);
                }
            }
            return properties;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Iterable<CSVRecord> parseCSV() throws IOException {
        try (Reader in = new InputStreamReader(new ByteArrayInputStream(loadCSVBytes()))) {
            return ImmutableList.copyOf(CSVFormat.DEFAULT.parse(in));
        }
    }

    private static byte[] loadCSVBytes() {
        try {
            final File file = new File("users.csv");
            if (file.exists()) {
                try (FileInputStream fileInputStream = new FileInputStream(file)) {
                    return fileInputStream.readAllBytes();
                }
            }
            return ClassLoaderUtils.getSystemResourceAsByteArray("users.csv");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void startMetrics() {
        MetricRegistry metricRegistry = new MetricRegistry();
        dropWizardMetricFactory = new DropWizardMetricFactory(metricRegistry);
        dropWizardMetricFactory.start();
        Slf4jReporter slf4jReporter = Slf4jReporter.forRegistry(metricRegistry)
            .outputTo(LoggerFactory.getLogger("METRICS"))
            .withLoggingLevel(INFO)
            .convertRatesTo(TimeUnit.SECONDS)
            .convertDurationsTo(TimeUnit.MILLISECONDS)
            .build();
        slf4jReporter.start(10, TimeUnit.SECONDS);

        failedAppend = dropWizardMetricFactory.generate("failed-append");
        failedAuth = dropWizardMetricFactory.generate("failed-auth");
        failedConnect = dropWizardMetricFactory.generate("failed-connect");
        failedCreate = dropWizardMetricFactory.generate("failed-create");
        provisionnedUsers = dropWizardMetricFactory.generate("provisionnedUsers");
    }

    private static Mono<Void> provisionUser(CSVRecord csvRecord)  {
        try {
            URI serverUri = new URI(URL);

            return Flux.range(0, NUM_CONNECTIONS_PER_USER)
                .flatMap(i -> connect(serverUri)
                    // LOGIN
                    .flatMap(session -> login(csvRecord, session)
                        .thenReturn(session.getSession())))
                
                .collectList()
                
                // Create mailboxes (uses first session)
                .flatMap(sessions -> Flux.range(0, NUM_MBX)
                    .concatMap(i -> createFolder(sessions.get(0), PREFIX + i))
                    .then()
                    .thenReturn(sessions))
                
                // Append messages in parallel on all connections
                .flatMap(sessions -> Flux.range(0, NUM_CONNECTIONS_PER_USER)
                    .flatMap(i -> Flux.fromIterable(PARTITIONED_MAILBOXES.get(i))
                        .concatMap(mailbox -> append(sessions.get(i), mailbox)))
                    .then()
                    .thenReturn(sessions))

                // Close session
                .flatMap(sessions -> Flux.fromIterable(sessions)
                    .flatMap(Main::close)
                    .then())

                .then(Mono.fromRunnable(() -> provisionnedUsers.increment()))

                .onErrorResume(e -> {
                    LOGGER.error("Fatal error provisioning {}", csvRecord.get(0), e);
                    return Mono.empty();
                })

                .then();
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    private static Mono<Object> close(ImapAsyncSession session) {
        return Mono.from(dropWizardMetricFactory.decoratePublisherWithTimerMetric("CLOSE", Mono.create(sink -> {
            ImapFuture<Boolean> future = session.close();
            future.setDoneCallback(sink::success);
            future.setExceptionCallback(sink::error);
            future.setCanceledCallback(sink::success);
        })));
    }

    private static ImapAsyncClient createImapClient() {
        try {
            return new ImapAsyncClient(NUM_OF_THREADS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<Integer, List<String>> computePartitionnedMailboxes() {
        ImmutableList<String> mailboxNames = computeAppendOrders();
        AtomicInteger counter = new AtomicInteger(0);
        return mailboxNames.stream()
            .collect(Collectors.groupingBy(s -> counter.incrementAndGet() % NUM_CONNECTIONS_PER_USER));
    }

    private static ImmutableList<String> computeAppendOrders() {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        IntStream.range(0, NUM_MSG_INBOX).forEach(i -> builder.add("INBOX"));
        IntStream.range(0, NUM_MBX).forEach(i ->
            IntStream.range(0, NUM_MSG_REGULAR_FOLDER)
                .forEach(j -> builder.add(PREFIX + i)));
        return builder.build();
    }

    private static Mono<ImapAsyncResponse> login(CSVRecord csvRecord, ImapAsyncCreateSessionResponse session) {
        return Mono.from(dropWizardMetricFactory.decoratePublisherWithTimerMetric("LOGIN",
            execute(session.getSession(), new LoginCommand(csvRecord.get(0), csvRecord.get(1)))))
            .retryWhen(Retry.backoff(5, Duration.ofMillis(200)).scheduler(Schedulers.parallel()))
            .doOnError(e -> failedAuth.increment());
    }

    private static Mono<ImapAsyncResponse> append(ImapAsyncSession session, String folderName) {
        return Mono.from(dropWizardMetricFactory.decoratePublisherWithTimerMetric("APPEND",
            execute(session, new AppendCommand(folderName, new Flags(), new Date(), messageBytes()))))
            .onErrorResume(e -> {
                failedAppend.increment();
                LOGGER.error("Failed appending a message", e);
                return Mono.empty();
            });
    }

    private static Mono<ImapAsyncResponse> createFolder(ImapAsyncSession session, String folderName) {
        return Mono.from(dropWizardMetricFactory.decoratePublisherWithTimerMetric("CREATE",
            execute(session, new CreateFolderCommand(folderName))
                .onErrorResume(e -> e.getMessage().contains("NO CREATE failed. Mailbox already exists."), e -> Mono.empty())))
            .retryWhen(Retry.backoff(5, Duration.ofMillis(200)).scheduler(Schedulers.parallel()))
            .doOnError(e -> failedCreate.increment());
    }

    private static byte[] messageBytes() {
        int tirage = Math.abs(RANDOM.nextInt());
        byte[] customHeader = ("\r\nX-CUSTOM: " + UUID.randomUUID().toString() + "\r\n\r\n").getBytes();
        byte[] customBodyValue = UUID.randomUUID().toString().getBytes();

        // 1% -> 10MB   (with attachments)
        if (tirage % 100 == 0) {
            return Bytes.concat(
                HEADER_BYTES,
                customHeader,
                customBodyValue,
                BODY_START_BYTES,
                // 10MB Attachment 66
                Strings.repeat("UmV0dXJuLVBhdGg6IDx0cHJ1ZGVudG92YUBsaW5hZ29yYS5jb20+DQpvcmcuYXBhY2hlLmph\r\n", 151515).getBytes(),
                BODY_END_BYTES);
        }
        // 4% -> 5MB   (with attachments)
        if (tirage % 100 < 4) {
            return Bytes.concat(
                HEADER_BYTES,
                customHeader,
                customBodyValue,
                BODY_START_BYTES,
                // 10MB Attachment 66
                Strings.repeat("UmV0dXJuLVBhdGg6IDx0cHJ1ZGVudG92YUBsaW5hZ29yYS5jb20+DQpvcmcuYXBhY2hlLmph\r\n", 75757).getBytes(),
                BODY_END_BYTES);
        }
        // 10% -> 1 MB  (with attachments)
        if (tirage % 100 < 10) {
            return Bytes.concat(
                HEADER_BYTES,
                customHeader,
                customBodyValue,
                BODY_START_BYTES,
                // 10MB Attachment 66
                Strings.repeat("UmV0dXJuLVBhdGg6IDx0cHJ1ZGVudG92YUBsaW5hZ29yYS5jb20+DQpvcmcuYXBhY2hlLmph\r\n", 15151).getBytes(),
                BODY_END_BYTES);
        }
        // 20% -> 100KB (with attachments)
        if (tirage % 100 < 20) {
            return Bytes.concat(
                HEADER_BYTES,
                customHeader,
                customBodyValue,
                BODY_START_BYTES,
                // 10MB Attachment 66
                Strings.repeat("UmV0dXJuLVBhdGg6IDx0cHJ1ZGVudG92YUBsaW5hZ29yYS5jb20+DQpvcmcuYXBhY2hlLmph\r\n", 1515).getBytes(),
                BODY_END_BYTES);
        }
        return Bytes.concat(
            HEADER_BYTES_SIMPLE,
            customHeader,
            customBodyValue);
    }

    static Mono<ImapAsyncCreateSessionResponse> connect(URI serverUri) {
        ImapAsyncSessionConfig config = new ImapAsyncSessionConfig();
        config.setConnectionTimeoutMillis(5000);
        config.setReadTimeoutMillis(6000);
        List<String> sniNames = null;
        InetSocketAddress localAddress = null;

        Mono<ImapAsyncCreateSessionResponse> result = Mono.from(dropWizardMetricFactory.decoratePublisherWithTimerMetric("CONNECT",
            Mono.create(sink -> {
                ImapFuture<ImapAsyncCreateSessionResponse> future = (ImapFuture) IMAP_CLIENT.createSession(serverUri, config, localAddress, sniNames, ImapAsyncSession.DebugMode.DEBUG_OFF, "NA", SSL_CONTEXT);
                future.setDoneCallback(sink::success);
                future.setExceptionCallback(sink::error);
                future.setCanceledCallback(sink::success);
            })));
        return result
            .retryWhen(Retry.backoff(5, Duration.ofMillis(200)).scheduler(Schedulers.parallel()))
            .doOnError(e -> failedConnect.increment());
    }

    private static SSLContext createSslContext() {
        try {
            // Create a trust manager that does not validate certificate chains
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, TRUST_ALL_CERTS, new java.security.SecureRandom());
            return sc;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static Mono<ImapAsyncResponse> execute(ImapAsyncSession session, ImapRequest cmd) {
        return Mono.<ImapAsyncResponse>create(sink -> {
            try {
                ImapFuture<ImapAsyncResponse> future = session.execute(cmd);
                future.setDoneCallback(sink::success);
                future.setExceptionCallback(sink::error);
                future.setCanceledCallback(sink::success);
            } catch (ImapAsyncClientException e) {
                sink.error(e);
            }
        }).handle((next, sink) -> next.getResponseLines().stream()
            .filter(response -> response.isBAD() || response.isNO())
            .findAny()
            .ifPresentOrElse(
                bad -> sink.error(new Exception("IMAP error " + bad.toString())),
                () -> sink.next(next)));
    }
}
