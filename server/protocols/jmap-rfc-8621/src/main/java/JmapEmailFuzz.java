import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.mail.Flags;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.jmap.api.model.Preview;
import org.apache.james.jmap.mail.EmailFullViewFactory;
import org.apache.james.jmap.mail.EmailGetRequest;
import org.apache.james.jmap.method.SystemZoneIdProvider;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.Header;
import org.apache.james.mailbox.model.Headers;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MimeDescriptor;
import org.apache.james.mailbox.model.MimePath;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.util.html.HtmlTextExtractor;
import org.apache.james.util.mime.MessageContentExtractor;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.google.common.collect.ImmutableList;

public class JmapEmailFuzz {
    private static final HtmlTextExtractor HTML_TEXT_EXTRACTOR = html -> html;
    private static final EmailFullViewFactory EMAIL_FULL_VIEW_FACTORY = new EmailFullViewFactory(new SystemZoneIdProvider(), new Preview.Factory(new MessageContentExtractor(), HTML_TEXT_EXTRACTOR));
    private static final MessageId MESSAGE_ID = new MessageId() {
        @Override
        public String serialize() {
            return "1";
        }

        @Override
        public boolean isSerializable() {
            return true;
        }
    };
    private static final MessageUid UID = MessageUid.of(456);

    public static void fuzzerTestOneInput(FuzzedDataProvider data) {
        final byte[] bytes = data.consumeBytes(4096);
        final EmailGetRequest emailGetRequest = EmailGetRequest.javaStuff("bob@domain.tld", MESSAGE_ID);

        EMAIL_FULL_VIEW_FACTORY.toEmailJava(HTML_TEXT_EXTRACTOR, emailGetRequest, new MessageResult() {
            @Override
            public MessageId getMessageId() {
                return MESSAGE_ID;
            }

            @Override
            public ThreadId getThreadId() {
                return ThreadId.fromBaseMessageId(MESSAGE_ID);
            }

            @Override
            public Date getInternalDate() {
                return new Date();
            }

            @Override
            public Flags getFlags() {
                return new Flags();
            }

            @Override
            public long getSize() {
                return 0;
            }

            @Override
            public MessageMetaData messageMetaData() {
                return null;
            }

            @Override
            public MessageUid getUid() {
                return UID;
            }

            @Override
            public ModSeq getModSeq() {
                return ModSeq.first();
            }

            @Override
            public MimeDescriptor getMimeDescriptor() {
                throw new NotImplementedException();
            }

            @Override
            public MailboxId getMailboxId() {
                return () -> "36";
            }

            @Override
            public Iterator<Header> iterateHeaders(MimePath path) {
                throw new NotImplementedException();
            }

            @Override
            public Iterator<Header> iterateMimeHeaders(MimePath path) {
                throw new NotImplementedException();
            }

            @Override
            public Content getFullContent() {
                return new Content() {
                    @Override
                    public InputStream getInputStream() {
                        return new ByteArrayInputStream(bytes);
                    }

                    @Override
                    public long size() {
                        return bytes.length;
                    }
                };
            }

            @Override
            public Content getFullContent(MimePath path) {
                throw new NotImplementedException();
            }

            @Override
            public Content getBody() {
                throw new NotImplementedException();
            }

            @Override
            public Content getBody(MimePath path) {
                throw new NotImplementedException();
            }

            @Override
            public Content getMimeBody(MimePath path) {
                throw new NotImplementedException();
            }

            @Override
            public Headers getHeaders() {
                throw new NotImplementedException();
            }

            @Override
            public List<MessageAttachmentMetadata> getLoadedAttachments() {
                return ImmutableList.of();
            }

            @Override
            public int compareTo(MessageResult messageResult) {
                return this.getUid().compareTo(messageResult.getUid());
            }
        }).get();
    }
}

