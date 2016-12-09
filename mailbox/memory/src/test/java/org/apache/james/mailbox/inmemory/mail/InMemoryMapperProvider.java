package org.apache.james.mailbox.inmemory.mail;

import java.util.List;
import java.util.Random;

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageIdMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.mail.model.MapperProvider;

import com.google.common.collect.ImmutableList;

public class InMemoryMapperProvider implements MapperProvider {

    private final Random random;
    private final MessageId.Factory messageIdFactory;
    private long nextUid;

    public InMemoryMapperProvider() {
        random = new Random();
        messageIdFactory = new DefaultMessageId.Factory();
        nextUid = 1;
    }

    @Override
    public MailboxMapper createMailboxMapper() throws MailboxException {
        return new InMemoryMailboxSessionMapperFactory().createMailboxMapper(new MockMailboxSession("user"));
    }

    @Override
    public MessageMapper createMessageMapper() throws MailboxException {
        return new InMemoryMailboxSessionMapperFactory().createMessageMapper(new MockMailboxSession("user"));
    }

    @Override
    public MessageIdMapper createMessageIdMapper() throws MailboxException {
        throw new NotImplementedException();
    }

    @Override
    public AttachmentMapper createAttachmentMapper() throws MailboxException {
        return new InMemoryMailboxSessionMapperFactory().createAttachmentMapper(new MockMailboxSession("user"));
    }

    @Override
    public InMemoryId generateId() {
        return InMemoryId.of(random.nextInt());
    }

    @Override
    public MessageUid generateMessageUid() {
        long currentUid = nextUid;
        nextUid++;
        return MessageUid.of(currentUid);
    }

    @Override
    public void clearMapper() throws MailboxException {

    }

    @Override
    public void ensureMapperPrepared() throws MailboxException {

    }

    @Override
    public boolean supportPartialAttachmentFetch() {
        return false;
    }

    @Override
    public AnnotationMapper createAnnotationMapper() throws MailboxException {
        return new InMemoryMailboxSessionMapperFactory().createAnnotationMapper(new MockMailboxSession("user"));
    }
    
    @Override
    public MessageId generateMessageId() {
        return messageIdFactory.generate();
    }

    @Override
    public List<Capabilities> getNotImplemented() {
        return ImmutableList.of();
    }
}
