package org.apache.james.modules.protocols;

import java.util.Collection;

import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.decode.ImapCommandParser;
import org.apache.james.imap.decode.parser.AppendCommandParser;
import org.apache.james.imap.decode.parser.AuthenticateCommandParser;
import org.apache.james.imap.decode.parser.CapabilityCommandParser;
import org.apache.james.imap.decode.parser.CheckCommandParser;
import org.apache.james.imap.decode.parser.CloseCommandParser;
import org.apache.james.imap.decode.parser.CompressCommandParser;
import org.apache.james.imap.decode.parser.CopyCommandParser;
import org.apache.james.imap.decode.parser.CreateCommandParser;
import org.apache.james.imap.decode.parser.DeleteACLCommandParser;
import org.apache.james.imap.decode.parser.DeleteCommandParser;
import org.apache.james.imap.decode.parser.EnableCommandParser;
import org.apache.james.imap.decode.parser.ExamineCommandParser;
import org.apache.james.imap.decode.parser.ExpungeCommandParser;
import org.apache.james.imap.decode.parser.FetchCommandParser;
import org.apache.james.imap.decode.parser.GetACLCommandParser;
import org.apache.james.imap.decode.parser.GetMetadataCommandParser;
import org.apache.james.imap.decode.parser.GetQuotaCommandParser;
import org.apache.james.imap.decode.parser.GetQuotaRootCommandParser;
import org.apache.james.imap.decode.parser.IdleCommandParser;
import org.apache.james.imap.decode.parser.ListCommandParser;
import org.apache.james.imap.decode.parser.ListRightsCommandParser;
import org.apache.james.imap.decode.parser.LoginCommandParser;
import org.apache.james.imap.decode.parser.LsubCommandParser;
import org.apache.james.imap.decode.parser.MoveCommandParser;
import org.apache.james.imap.decode.parser.MyRightsCommandParser;
import org.apache.james.imap.decode.parser.NamespaceCommandParser;
import org.apache.james.imap.decode.parser.NoopCommandParser;
import org.apache.james.imap.decode.parser.RenameCommandParser;
import org.apache.james.imap.decode.parser.SearchCommandParser;
import org.apache.james.imap.decode.parser.SelectCommandParser;
import org.apache.james.imap.decode.parser.SetACLCommandParser;
import org.apache.james.imap.decode.parser.SetAnnotationCommandParser;
import org.apache.james.imap.decode.parser.SetQuotaCommandParser;
import org.apache.james.imap.decode.parser.StartTLSCommandParser;
import org.apache.james.imap.decode.parser.StatusCommandParser;
import org.apache.james.imap.decode.parser.StoreCommandParser;
import org.apache.james.imap.decode.parser.SubscribeCommandParser;
import org.apache.james.imap.decode.parser.UidCommandParser;
import org.apache.james.imap.decode.parser.UnselectCommandParser;
import org.apache.james.imap.decode.parser.UnsubscribeCommandParser;
import org.apache.james.imap.decode.parser.XListCommandParser;
import org.apache.james.imap.encode.AuthenticateResponseEncoder;
import org.apache.james.imap.encode.CapabilityResponseEncoder;
import org.apache.james.imap.encode.ContinuationResponseEncoder;
import org.apache.james.imap.encode.ESearchResponseEncoder;
import org.apache.james.imap.encode.EnableResponseEncoder;
import org.apache.james.imap.encode.ExistsResponseEncoder;
import org.apache.james.imap.encode.ExpungeResponseEncoder;
import org.apache.james.imap.encode.FetchResponseEncoder;
import org.apache.james.imap.encode.FlagsResponseEncoder;
import org.apache.james.imap.encode.ImapResponseEncoder;
import org.apache.james.imap.encode.LSubResponseEncoder;
import org.apache.james.imap.encode.ListResponseEncoder;
import org.apache.james.imap.encode.ListRightsResponseEncoder;
import org.apache.james.imap.encode.MailboxStatusResponseEncoder;
import org.apache.james.imap.encode.MetadataResponseEncoder;
import org.apache.james.imap.encode.MyRightsResponseEncoder;
import org.apache.james.imap.encode.NamespaceResponseEncoder;
import org.apache.james.imap.encode.QuotaResponseEncoder;
import org.apache.james.imap.encode.QuotaRootResponseEncoder;
import org.apache.james.imap.encode.RecentResponseEncoder;
import org.apache.james.imap.encode.SearchResponseEncoder;
import org.apache.james.imap.encode.StatusResponseEncoder;
import org.apache.james.imap.encode.VanishedResponseEncoder;
import org.apache.james.imap.encode.XListResponseEncoder;
import org.apache.james.imap.processor.AppendProcessor;
import org.apache.james.imap.processor.AuthenticateProcessor;
import org.apache.james.imap.processor.CapabilityProcessor;
import org.apache.james.imap.processor.CheckProcessor;
import org.apache.james.imap.processor.CloseProcessor;
import org.apache.james.imap.processor.CompressProcessor;
import org.apache.james.imap.processor.CopyProcessor;
import org.apache.james.imap.processor.CreateProcessor;
import org.apache.james.imap.processor.DeleteACLProcessor;
import org.apache.james.imap.processor.DeleteProcessor;
import org.apache.james.imap.processor.EnableProcessor;
import org.apache.james.imap.processor.ExamineProcessor;
import org.apache.james.imap.processor.ExpungeProcessor;
import org.apache.james.imap.processor.GetACLProcessor;
import org.apache.james.imap.processor.GetMetadataProcessor;
import org.apache.james.imap.processor.GetQuotaProcessor;
import org.apache.james.imap.processor.GetQuotaRootProcessor;
import org.apache.james.imap.processor.IdleProcessor;
import org.apache.james.imap.processor.LSubProcessor;
import org.apache.james.imap.processor.ListProcessor;
import org.apache.james.imap.processor.ListRightsProcessor;
import org.apache.james.imap.processor.LoginProcessor;
import org.apache.james.imap.processor.LogoutProcessor;
import org.apache.james.imap.processor.MoveProcessor;
import org.apache.james.imap.processor.MyRightsProcessor;
import org.apache.james.imap.processor.NamespaceProcessor;
import org.apache.james.imap.processor.NoopProcessor;
import org.apache.james.imap.processor.RenameProcessor;
import org.apache.james.imap.processor.SearchProcessor;
import org.apache.james.imap.processor.SelectProcessor;
import org.apache.james.imap.processor.SetACLProcessor;
import org.apache.james.imap.processor.SetMetadataProcessor;
import org.apache.james.imap.processor.SetQuotaProcessor;
import org.apache.james.imap.processor.StartTLSProcessor;
import org.apache.james.imap.processor.StatusProcessor;
import org.apache.james.imap.processor.StoreProcessor;
import org.apache.james.imap.processor.SubscribeProcessor;
import org.apache.james.imap.processor.UnselectProcessor;
import org.apache.james.imap.processor.UnsubscribeProcessor;
import org.apache.james.imap.processor.XListProcessor;
import org.apache.james.imap.processor.base.AbstractProcessor;
import org.apache.james.imap.processor.base.ImapResponseMessageProcessor;
import org.apache.james.imap.processor.fetch.FetchProcessor;

import com.google.common.collect.ImmutableList;

public interface ImapPackage {
    Collection<Class<? extends AbstractProcessor>> processors();

    Collection<Class<? extends ImapCommandParser>> decoders();

    Collection<Class<? extends ImapResponseEncoder>> encoders();

    class Impl implements ImapPackage {
        private final ImmutableList<Class<? extends AbstractProcessor>> processors;
        private final ImmutableList<Class<? extends ImapCommandParser>> decoders;
        private final ImmutableList<Class<? extends ImapResponseEncoder>> encoders;

        public Impl(ImmutableList<Class<? extends AbstractProcessor>> processors,
                    ImmutableList<Class<? extends ImapCommandParser>> decoders,
                    ImmutableList<Class<? extends ImapResponseEncoder>> encoders) {
            this.processors = processors;
            this.decoders = decoders;
            this.encoders = encoders;
        }

        @Override
        public Collection<Class<? extends AbstractProcessor>> processors() {
            return processors;
        }

        @Override
        public Collection<Class<? extends ImapCommandParser>> decoders() {
            return decoders;
        }

        @Override
        public Collection<Class<? extends ImapResponseEncoder>> encoders() {
            return encoders;
        }
    }

    static ImapPackage and(Collection<ImapPackage> packages) {
        return new ImapPackage() {
            @Override
            public Collection<Class<? extends AbstractProcessor>> processors() {
                return packages.stream()
                    .flatMap(p -> p.processors().stream())
                    .collect(ImmutableList.toImmutableList());
            }

            @Override
            public Collection<Class<? extends ImapCommandParser>> decoders() {
                return packages.stream()
                    .flatMap(p -> p.decoders().stream())
                    .collect(ImmutableList.toImmutableList());
            }

            @Override
            public Collection<Class<? extends ImapResponseEncoder>> encoders() {
                return packages.stream()
                    .flatMap(p -> p.encoders().stream())
                    .collect(ImmutableList.toImmutableList());
            }
        };
    }

    ImapPackage DEFAULT = and(ImmutableList.of(new DefaultNoAuth(), new DefaultAuth()));

    class DefaultNoAuth extends Impl {
        public DefaultNoAuth() {
            super(
                ImmutableList.of(
                    CapabilityProcessor.class,
                    CheckProcessor.class,
                    RenameProcessor.class,
                    DeleteProcessor.class,
                    CreateProcessor.class,
                    CloseProcessor.class,
                    UnsubscribeProcessor.class,
                    SubscribeProcessor.class,
                    CopyProcessor.class,
                    ExpungeProcessor.class,
                    ExamineProcessor.class,
                    AppendProcessor.class,
                    StoreProcessor.class,
                    NoopProcessor.class,
                    IdleProcessor.class,
                    StatusProcessor.class,
                    LSubProcessor.class,
                    XListProcessor.class,
                    ListProcessor.class,
                    SearchProcessor.class,
                    SelectProcessor.class,
                    NamespaceProcessor.class,
                    FetchProcessor.class,
                    StartTLSProcessor.class,
                    UnselectProcessor.class,
                    CompressProcessor.class,
                    GetACLProcessor.class,
                    SetACLProcessor.class,
                    DeleteACLProcessor.class,
                    ListRightsProcessor.class,
                    MyRightsProcessor.class,
                    EnableProcessor.class,
                    GetQuotaProcessor.class,
                    SetQuotaProcessor.class,
                    GetQuotaRootProcessor.class,
                    ImapResponseMessageProcessor.class,
                    MoveProcessor.class,
                    SetMetadataProcessor.class,
                    GetMetadataProcessor.class,
                    LogoutProcessor.class),
                ImmutableList.of(
                    CapabilityCommandParser.class,
                    NoopCommandParser.class,
                    SelectCommandParser.class,
                    ExamineCommandParser.class,
                    CreateCommandParser.class,
                    DeleteCommandParser.class,
                    RenameCommandParser.class,
                    SubscribeCommandParser.class,
                    UnsubscribeCommandParser.class,
                    ListCommandParser.class,
                    XListCommandParser.class,
                    LsubCommandParser.class,
                    StatusCommandParser.class,
                    AppendCommandParser.class,
                    NamespaceCommandParser.class,
                    GetACLCommandParser.class,
                    SetACLCommandParser.class,
                    DeleteACLCommandParser.class,
                    ListRightsCommandParser.class,
                    MyRightsCommandParser.class,
                    CheckCommandParser.class,
                    CloseCommandParser.class,
                    ExpungeCommandParser.class,
                    CopyCommandParser.class,
                    MoveCommandParser.class,
                    SearchCommandParser.class,
                    FetchCommandParser.class,
                    StoreCommandParser.class,
                    UidCommandParser.class,
                    IdleCommandParser.class,
                    StartTLSCommandParser.class,
                    UnselectCommandParser.class,
                    CompressCommandParser.class,
                    EnableCommandParser.class,
                    GetQuotaRootCommandParser.class,
                    GetQuotaCommandParser.class,
                    SetQuotaCommandParser.class,
                    SetAnnotationCommandParser.class,
                    GetMetadataCommandParser.class),
                ImmutableList.of(MetadataResponseEncoder.class,
                    MyRightsResponseEncoder.class,
                    ListRightsResponseEncoder.class,
                    ListResponseEncoder.class,
                    NamespaceResponseEncoder.class,
                    StatusResponseEncoder.class,
                    RecentResponseEncoder.class,
                    FetchResponseEncoder.class,
                    ExpungeResponseEncoder.class,
                    ExistsResponseEncoder.class,
                    MailboxStatusResponseEncoder.class,
                    SearchResponseEncoder.class,
                    LSubResponseEncoder.class,
                    XListResponseEncoder.class,
                    FlagsResponseEncoder.class,
                    CapabilityResponseEncoder.class,
                    EnableResponseEncoder.class,
                    ContinuationResponseEncoder.class,
                    AuthenticateResponseEncoder.class,
                    ESearchResponseEncoder.class,
                    VanishedResponseEncoder.class,
                    QuotaResponseEncoder.class,
                    QuotaRootResponseEncoder.class));
        }
    }

    class DefaultAuth extends Impl {
        public DefaultAuth() {
            super(ImmutableList.of(AuthenticateProcessor.class, LoginProcessor.class),
                ImmutableList.of(AuthenticateCommandParser.class, LoginCommandParser.class),
                ImmutableList.of());
        }
    }
}
