package org.apache.james.mailbox.store;

import java.time.Duration;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxPathLocker;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class JVMMailboxPathLockerTest {
    JVMMailboxPathLocker testee = new JVMMailboxPathLocker();

    @Test
    void test() throws Exception {
        ConcurrentTestRunner.builder()
            .operation((a, b) -> Mono.from(testee.executeReactiveWithLockReactive(MailboxPath.inbox(Username.of("bob")),
                Mono.fromCallable(() -> {
                        System.out.println(a + " " + b);
                    Thread.sleep(10);
                    return null;
                }).subscribeOn(Schedulers.boundedElastic()), MailboxPathLocker.LockType.Write)

                ).subscribeOn(Schedulers.boundedElastic()).block())
            .threadCount(100)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(10));
    }

}