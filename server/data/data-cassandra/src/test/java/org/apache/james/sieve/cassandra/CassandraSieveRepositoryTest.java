package org.apache.james.sieve.cassandra;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.lib.AbstractSieveRepositoryTest;

public class CassandraSieveRepositoryTest extends AbstractSieveRepositoryTest {

    private CassandraCluster cassandra;

    public CassandraSieveRepositoryTest() {
        cassandra = CassandraCluster.create(new CassandraSieveRepositoryModule());
    }

    @Override
    protected SieveRepository createSieveRepository() throws Exception {
        CassandraSieveRepository cassandraSieveRepository = new CassandraSieveRepository();
        cassandraSieveRepository.setSession(cassandra.getConf());
        return cassandraSieveRepository;
    }

    @Override
    protected void cleanUp() throws Exception {
        cassandra.clearAllTables();
    }
}