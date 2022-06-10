package org.apache.james.examples;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.apache.james.utils.UserDefinedStartable;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;

public class GraphiteExporter implements UserDefinedStartable {
    private final MetricRegistry metricRegistry;

    @Inject
    public GraphiteExporter(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
    }

    public void start() {
        Graphite graphite = new Graphite(new InetSocketAddress("graphite", 2003));
        GraphiteReporter reporter = GraphiteReporter.forRegistry(metricRegistry)
            .convertRatesTo(TimeUnit.SECONDS)
            .convertDurationsTo(TimeUnit.MILLISECONDS)
            .filter(MetricFilter.ALL)
            .build(graphite);
        reporter.start(1, TimeUnit.SECONDS);
        System.out.println();
        System.out.println();
        System.out.println();
        System.out.println();
        System.out.println();
        System.out.println();
        System.out.println("<<<<<<<<<<<<<<<<<<<<<<started>>>>>>>>>>>>>>");
        System.out.println();
        System.out.println();
        System.out.println();
        System.out.println();
        System.out.println();
        System.out.println();
    }
}
