package org.apache.james.mpt.imapmailbox.lucenesearch;

import org.apache.james.mpt.api.ImapHostSystem;
import org.apache.james.mpt.imapmailbox.suite.PartialFetch;
import org.junit.After;
import org.junit.Before;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class LucenePartialFetch extends PartialFetch {

    private ImapHostSystem system;

    @Before
    public void setUp() throws Exception {
        Injector injector = Guice.createInjector(new LuceneSearchMailboxTestModule());
        system = injector.getInstance(ImapHostSystem.class);
        system.beforeTest();
        super.setUp();
    }
    
    @Override
    protected ImapHostSystem createImapHostSystem() {
        return system;
    }

    @After
    public void tearDown() throws Exception {
        system.afterTest();
    }
    
}
