package org.apache.james.backends.es.v7;

import static org.apache.james.backends.es.v7.IndexCreationFactory.FORMAT;
import static org.apache.james.backends.es.v7.IndexCreationFactory.PROPERTIES;
import static org.apache.james.backends.es.v7.IndexCreationFactory.REQUIRED;
import static org.apache.james.backends.es.v7.IndexCreationFactory.ROUTING;
import static org.apache.james.backends.es.v7.IndexCreationFactory.TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.james.backends.es.v7.search.ScrolledSearch;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.awaitility.core.ConditionFactory;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

public class DateExampleTest {
    private static final IndexName INDEX_NAME = new IndexName("index_name");
    private static final WriteAliasName ALIAS_NAME = new WriteAliasName("alias_name");
    private static final ReadAliasName READ_ALIAS_NAME = new ReadAliasName("alias_name");
    private static final TimeValue TIMEOUT = TimeValue.timeValueMinutes(1);

    private static final ZonedDateTime DATE1 = ZonedDateTime.parse("2014-01-02T15:15:15Z");
    private static final ZonedDateTime DATE2 = ZonedDateTime.parse("2014-01-02T16:15:15Z");
    private static final ZonedDateTime DATE3 = ZonedDateTime.parse("2014-01-02T17:15:15Z");

    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();

    public static class IndexableDocument {
        private final ZonedDateTime date;

        public IndexableDocument(ZonedDateTime date) {
            this.date = date;
        }

        public String getMyDate() {
            return DateTimeFormatter.ISO_ZONED_DATE_TIME.format(date);
        }
    }

    public static XContentBuilder getMappingContent() {
        try {
            return jsonBuilder()
                .startObject()
                    .startObject(ROUTING)
                        .field(REQUIRED, true)
                    .endObject()

                    .startObject(PROPERTIES)

                        .startObject("myDate")
                            .field(TYPE, IndexCreationFactory.DATE)
                            .field(FORMAT, "uuuu-MM-dd'T'HH:mm:ssX||uuuu-MM-dd'T'HH:mm:ssXXX||uuuu-MM-dd'T'HH:mm:ssXXXXX")
                        .endObject()

                    .endObject()
                .endObject();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @RegisterExtension
    public DockerElasticSearchExtension elasticSearch = new DockerElasticSearchExtension();
    private ElasticSearchIndexer indexer;
    private ReactorElasticSearchClient client;

    @BeforeEach
    void setup() {
        client = elasticSearch.getDockerElasticSearch().clientProvider().get();
        new IndexCreationFactory(ElasticSearchConfiguration.DEFAULT_CONFIGURATION)
            .useIndex(INDEX_NAME)
            .addAlias(ALIAS_NAME)
            .createIndexAndAliases(client, getMappingContent());
        indexer = new ElasticSearchIndexer(client, ALIAS_NAME);
    }

    @Test
    void test() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        RoutingKey routingKey = RoutingKey.fromString("routingKey");
        indexer.index(DocumentId.fromString("a"),
            objectMapper.writeValueAsString(new IndexableDocument(DATE1)),
            routingKey)
            .block();

        indexer.index(DocumentId.fromString("b"),
            objectMapper.writeValueAsString(new IndexableDocument(DATE2)),
            routingKey)
            .block();

        indexer.index(DocumentId.fromString("c"),
            objectMapper.writeValueAsString(new IndexableDocument(DATE3)),
            routingKey)
            .block();

        awaitForElasticSearch(matchAllQuery(), 3);

        assertThat(
            new ScrolledSearch(client, new SearchRequest()
                .indices(ALIAS_NAME.getValue())
                .routing(routingKey.asString())
                .scroll(TIMEOUT)
                .source(new SearchSourceBuilder()
                    .query(matchAllQuery())))
                .searchHits()
                .map(SearchHit::getId)
                .collectList()
                .block())
            .containsOnly("a", "b", "c"); // Documents are well indexed.

        assertThat(
            new ScrolledSearch(client, new SearchRequest()
                    .indices(ALIAS_NAME.getValue())
                    .routing(routingKey.asString())
                    .scroll(TIMEOUT)
                    .source(new SearchSourceBuilder()
                        .query(boolQuery()
                            .filter(rangeQuery("myDate").lt(DateTimeFormatter.ISO_ZONED_DATE_TIME.format(DATE2))))))
                .searchHits()
                .map(SearchHit::getId)
                .collectList()
                .block())
            .containsOnly("a");

        assertThat(
            new ScrolledSearch(client, new SearchRequest()
                .indices(ALIAS_NAME.getValue())
                .routing(routingKey.asString())
                .scroll(TIMEOUT)
                .source(new SearchSourceBuilder()
                    .query(boolQuery()
                        .filter(rangeQuery("myDate").gt(DateTimeFormatter.ISO_ZONED_DATE_TIME.format(DATE2))))))
                .searchHits()
                .map(SearchHit::getId)
                .collectList()
                .block())
            .containsOnly("c");
    }

    private void awaitForElasticSearch(QueryBuilder query, long totalHits) {
        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> assertThat(client.search(
                new SearchRequest(INDEX_NAME.getValue())
                    .source(new SearchSourceBuilder().query(query)),
                RequestOptions.DEFAULT)
                .block()
                .getHits().getTotalHits().value).isEqualTo(totalHits));
    }
}
