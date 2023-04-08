package org.apache.james.transport;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.mail.Flags;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.james.util.ClassLoaderUtils;

import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
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

public class Main {
    private static final String PREFIX = "folder-";
    private static final Random RANDOM = new Random();
    private static final byte[] HEADER_BYTES = ClassLoaderUtils.getSystemResourceAsByteArray("eml/headers");
    private static final byte[] HEADER_BYTES_SIMPLE = ClassLoaderUtils.getSystemResourceAsByteArray("eml/headersSimple");
    private static final byte[] BODY_START_BYTES = ClassLoaderUtils.getSystemResourceAsByteArray("eml/bodyStart");
    private static final byte[] BODY_END_BYTES = ClassLoaderUtils.getSystemResourceAsByteArray("eml/bodyEnd");
    private static final byte[] CSV = ClassLoaderUtils.getSystemResourceAsByteArray("users.csv");

    private static final String URL = "imaps://172.25.0.2:993";
    private static final int NUM_MBX = 4;
    private static final int NUM_MSG_REGULAR_FOLDER = 5;
    private static final int NUM_MSG_INBOX = 10;
    private static final int NUM_OF_THREADS = 10;

    public static void main(String... args) throws Exception {
        Reader in = new InputStreamReader(new ByteArrayInputStream(CSV));

        Iterable<CSVRecord> records = CSVFormat.DEFAULT.parse(in);

        final Stopwatch started = Stopwatch.createStarted();

        Flux.fromIterable(records)
            .flatMap(Main::provisionUser, 10)
            .then()
            .block();

        System.out.println("Elapsed " + started.elapsed(TimeUnit.MILLISECONDS) + " ms");
    }

    private static Mono<Void> provisionUser(CSVRecord record)  {
        try {
            ImapAsyncClient imapClient = new ImapAsyncClient(NUM_OF_THREADS);
            URI serverUri = new URI(URL);


            // TODO pooling: for each user do 4 connections and mutualize requests to it
            return connect(imapClient, serverUri)

                // LOGIN
                .flatMap(session -> execute(session.getSession(), new LoginCommand(record.get(0), record.get(1)))
                    .thenReturn(session.getSession()))

                // Provision massages into INBOX
                .flatMap(session -> Flux.range(0, NUM_MSG_INBOX)
                    .concatMap(j -> execute(session, new AppendCommand("INBOX", new Flags(), new Date(), messageBytes()))).then().thenReturn(session))

                // Create mailboxes with sub messages
                .flatMap(session -> Flux.range(0, NUM_MBX)
                    .concatMap(i -> createFolder(session, PREFIX + i)
                        .then(Flux.range(0, NUM_MSG_REGULAR_FOLDER)
                            .concatMap(j -> execute(session, new AppendCommand(PREFIX + i, new Flags(), new Date(), messageBytes()))).then()))
                    .then())

                .then();
        } catch(Exception e) {
            return Mono.error(e);
        }
    }

    private static Mono<ImapAsyncResponse> createFolder(ImapAsyncSession session, String folderName) {
        return execute(session, new CreateFolderCommand(folderName))
            .onErrorResume(e -> e.getMessage().contains("NO CREATE failed. Mailbox already exists."), e -> Mono.empty());
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

    static Mono<ImapAsyncCreateSessionResponse> connect(ImapAsyncClient imapClient, URI serverUri) throws NoSuchAlgorithmException, KeyManagementException {
        ImapAsyncSessionConfig config = new ImapAsyncSessionConfig();
        config.setConnectionTimeoutMillis(5000);
        config.setReadTimeoutMillis(6000);
        List<String> sniNames = null;
        InetSocketAddress localAddress = null;

        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[] {
            new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
                public void checkClientTrusted(
                    java.security.cert.X509Certificate[] certs, String authType) {
                }
                public void checkServerTrusted(
                    java.security.cert.X509Certificate[] certs, String authType) {
                }
            }
        };
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());

        return Mono.create(sink -> {
            final ImapFuture<ImapAsyncCreateSessionResponse> future = (ImapFuture) imapClient.createSession(serverUri, config, localAddress, sniNames, ImapAsyncSession.DebugMode.DEBUG_ON,
                "NA", sc);
            future.setDoneCallback(sink::success);
            future.setExceptionCallback(sink::error);
            future.setCanceledCallback(sink::success);
        });
    }

    static Mono<ImapAsyncResponse> execute(ImapAsyncSession session, ImapRequest cmd) {
        System.out.println("Execute " + cmd.getClass());
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
