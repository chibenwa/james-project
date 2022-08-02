import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.apache.james.jmap.mail.EmailBodyPart;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.message.DefaultMessageBuilder;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;

public class BPFuzz {
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

    public static void fuzzerTestOneInput(FuzzedDataProvider data) {
        final byte[] bytes = data.consumeBytes(4096);
        try {
            final Message message = new DefaultMessageBuilder().parseMessage(new ByteArrayInputStream(bytes));
            EmailBodyPart.of(MESSAGE_ID, message);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

