package io.beancounter.activities;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.codehaus.jackson.map.ObjectMapper;
import org.elasticsearch.ElasticSearchException;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.collect.ImmutableList;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.query.AndFilterBuilder;
import org.elasticsearch.index.query.FilteredQueryBuilder;
import org.elasticsearch.index.query.OrFilterBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortOrder;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.beancounter.commons.helper.es.ElasticSearchConfiguration;
import io.beancounter.commons.helper.es.NodeInfo;
import io.beancounter.commons.model.activity.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.elasticsearch.index.query.FilterBuilders.*;
import static org.elasticsearch.index.query.QueryBuilders.*;

/**
 * <a href="http://www.elasticsearch.org">ElasticSearch</a>
 * based implementation of {@link ActivityStore}.
 *
 * @author Davide Palmisano ( dpalmisano@gmail.com )
 * @author Alex Cowell ( alxcwll@gmail.com )
 */
public class ElasticSearchActivityStore implements ActivityStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticSearchActivityStore.class);

    public static final String INDEX_NAME = "beancounter";

    public static final String INDEX_TYPE = "activity";

    private static final String DATE_PATH = INDEX_TYPE + ".activity.context.date";

    private static final String VISIBLE = "visible";

    private ObjectMapper mapper;

    private ElasticSearchConfiguration configuration;

    private Client client;

    @Inject
    public ElasticSearchActivityStore(
            @Named("esConfiguration") ElasticSearchConfiguration configuration
    ) {
        this.configuration = configuration;
        client = getClient();
        mapper = new ObjectMapper();
    }

    @Override
    public void store(UUID userId, ResolvedActivity activity) throws ActivityStoreException {
        indexActivity(userId, activity, client);
    }

    @Override
    public void store(UUID userId, Collection<ResolvedActivity> activities) throws ActivityStoreException {
        // TODO (low): Use the Bulk API for this.
        for (ResolvedActivity activity : activities) {
            indexActivity(userId, activity, client);
        }
    }

    @Override
    public Collection<ResolvedActivity> getByUser(UUID userId, int max) throws ActivityStoreException {
        AndFilterBuilder visibilityFilter = andFilter()
                .add(termFilter(VISIBLE, true));

        QueryBuilder query = filteredQuery(
                queryString("userId:" + userId.toString()),
                visibilityFilter
        );

        SearchResponse searchResponse = client.prepareSearch(INDEX_NAME)
                .setQuery(query)
                .addSort(DATE_PATH, SortOrder.DESC)
                .setSize(max)
                .execute().actionGet();

        return retrieveActivitiesFromSearchResponse(searchResponse);
    }

    @Override
    public Collection<ResolvedActivity> getByUserAndDateRange(UUID userId, DateTime from, DateTime to)
            throws ActivityStoreException {
        AndFilterBuilder visibilityFilter = andFilter()
                .add(termFilter(VISIBLE, true));

        QueryBuilder query = filteredQuery(
                queryString("userId:" + userId.toString()),
                visibilityFilter
        );

        SearchResponse searchResponse = client.prepareSearch(INDEX_NAME)
                .setQuery(query)
                .addSort(DATE_PATH, SortOrder.DESC)
                .setFilter(numericRangeFilter(DATE_PATH)
                        .from(from.getMillis())
                        .to(to.getMillis())
                ).execute().actionGet();

        return retrieveActivitiesFromSearchResponse(searchResponse);
    }

    @Override
    public Map<UUID, Collection<ResolvedActivity>> getByDateRange(DateTime from, DateTime to)
            throws ActivityStoreException {
        AndFilterBuilder visibilityFilter = andFilter()
                .add(termFilter(VISIBLE, true));

        QueryBuilder query = filteredQuery(
                matchAllQuery(),
                visibilityFilter
        );

        SearchResponse searchResponse = client.prepareSearch(INDEX_NAME)
                .setQuery(query)
                .addSort(DATE_PATH, SortOrder.DESC)
                .setFilter(numericRangeFilter(DATE_PATH)
                        .from(from.getMillis())
                        .to(to.getMillis())
                ).execute().actionGet();

        Map<UUID, Collection<ResolvedActivity>> activitiesMap =
                new HashMap<UUID, Collection<ResolvedActivity>>();

        // TODO (low): Use facets or some type of grouping to avoid populating the map
        // manually.
        for (SearchHit hit : searchResponse.getHits()) {
            UUID userId = UUID.fromString((String) hit.getSource().get("userId"));

            if (activitiesMap.get(userId) == null) {
                activitiesMap.put(userId, new ArrayList<ResolvedActivity>());
            }
            List<ResolvedActivity> activities = (List<ResolvedActivity>) activitiesMap.get(userId);

            ResolvedActivity activity;
            try {
                activity = mapper.readValue(hit.source(), ResolvedActivity.class);
            } catch (IOException ioe) {
                final String errMsg = "Error while deserializing [" + hit.getSource() + "]";
                throw new ActivityStoreException(errMsg, ioe);
            }

            activities.add(activity);
        }

        return activitiesMap;
    }

    @Override
    public Collection<ResolvedActivity> getByUser(UUID userId, Collection<UUID> activityIds)
            throws ActivityStoreException {
        AndFilterBuilder visibilityFilter = andFilter()
                .add(termFilter(VISIBLE, true));

        OrFilterBuilder idFilter = orFilter();
        for (UUID id : activityIds) {
            idFilter.add(queryFilter(queryString("activity.id:" + id.toString())));
        }

        AndFilterBuilder combinedFilters = andFilter(visibilityFilter, idFilter);

        QueryBuilder query = filteredQuery(
                queryString("userId:" + userId.toString()),
                combinedFilters
        );

        SearchResponse searchResponse = client.prepareSearch(INDEX_NAME)
                .setQuery(query)
                .addSort(DATE_PATH, SortOrder.DESC)
                .execute().actionGet();

        return retrieveActivitiesFromSearchResponse(searchResponse);
    }

    @Override
    public Collection<ResolvedActivity> getByUserPaginated(
            UUID userId, int pageNumber, int size, String order
    ) throws ActivityStoreException, InvalidOrderException {
        return searchAndPaginateResults("userId:" + userId.toString(), pageNumber, size, order);
    }

    @Override
    public Collection<ResolvedActivity> search(
            String path, String value, int pageNumber, int size, String order
    ) throws ActivityStoreException, WildcardSearchException, InvalidOrderException {
        if (path.contains("*") || value.contains("*")) {
            throw new WildcardSearchException("Wildcard searches are not allowed.");
        }

        return searchAndPaginateResults(path + ":" + value, pageNumber, size, order);
    }

    @Override
    public ResolvedActivity getActivity(UUID activityId) throws ActivityStoreException {
        GetResponse response = client
                .prepareGet(INDEX_NAME, INDEX_TYPE, activityId.toString())
                .execute().actionGet();

        ResolvedActivity activity;

        try {
            activity = mapper.readValue(response.source(), ResolvedActivity.class);
        } catch (IOException ioe) {
            final String errMsg = "Error while deserializing from json [" + response.getSource() + "]";
            throw new ActivityStoreException(errMsg, ioe);
        }

        return (activity != null && activity.isVisible()) ? activity : null;
    }

    @Override
    public void setVisible(UUID activityId, boolean visible) throws ActivityStoreException {
        try {
            client.prepareUpdate(INDEX_NAME, INDEX_TYPE, activityId.toString())
                    .addScriptParam(VISIBLE, visible)
                    .setScript("ctx._source.visible = visible")
                    .execute().actionGet();

            LOGGER.debug("activity {} visibility set to {}", activityId, visible);
        } catch (ElasticSearchException ese) {
            String message = "Error setting the visibility of activity "
                    + activityId.toString();
            throw new ActivityStoreException(message, ese);
        }
    }

    @Override
    public void shutDown() throws ActivityStoreException {
        try {
            client.close();
        } catch (Exception e) {
            final String errMsg = "Error while closing the client";
            throw new ActivityStoreException(errMsg, e);
        }
    }

    private void indexActivity(UUID userId, ResolvedActivity activity, Client client)
            throws ActivityStoreException {
        // TODO (high): is this needed or does the ResolvedActivity already
        // have the correct userId?
        activity.setUserId(userId);
        byte[] jsonActivity;
        try {
            jsonActivity = createActivityJson(activity);
        } catch (IOException e) {
            final String errMsg = "Error while serializing to json [" + activity + "]";
            throw new ActivityStoreException(errMsg, e);
        }
        client.prepareIndex(INDEX_NAME, INDEX_TYPE)
                .setSource(jsonActivity)
                .setId(activity.getActivity().getId().toString())
                .execute().actionGet();
    }

    private byte[] createActivityJson(ResolvedActivity activity) throws IOException {
        return mapper.writeValueAsBytes(activity);
    }

    private Collection<ResolvedActivity> searchAndPaginateResults(
            String query, int pageNumber, int size, String order
    ) throws ActivityStoreException, InvalidOrderException {
        SortOrder sortOrder;
        try {
            sortOrder = SortOrder.valueOf(order.toUpperCase());
        } catch (Exception ex) {
            throw new InvalidOrderException(order + " is not a valid sort order.");
        }

        AndFilterBuilder visibilityFilter = andFilter()
                .add(termFilter(VISIBLE, true));

        FilteredQueryBuilder fq = filteredQuery(
                queryString(query),
                visibilityFilter
        );

        SearchResponse searchResponse = client.prepareSearch(INDEX_NAME)
                .setSearchType(SearchType.QUERY_THEN_FETCH)
                .setQuery(fq)
                .addSort(DATE_PATH, sortOrder)
                .setFrom(pageNumber * size)
                .setSize(size)
                .execute().actionGet();

        return retrieveActivitiesFromSearchResponse(searchResponse);
    }

    private Collection<ResolvedActivity> retrieveActivitiesFromSearchResponse(
            SearchResponse searchResponse
    ) throws ActivityStoreException {
        Collection<ResolvedActivity> activities = new ArrayList<ResolvedActivity>();

        for (SearchHit hit : searchResponse.getHits()) {
            ResolvedActivity activity;
            try {
                activity = mapper.readValue(
                        hit.source(),
                        ResolvedActivity.class
                );
            } catch (IOException e) {
                final String errMsg = "Error while deserializing from json [" + hit.getSource() + "]";
                throw new ActivityStoreException(errMsg, e);
            }

            activities.add(activity);
        }

        return activities;
    }

    private Client getClient() {
        Settings settings = ImmutableSettings.settingsBuilder()
                .put("client.transport.sniff", true)
                .build();
        TransportClient transportClient = new TransportClient(settings);
        for (NodeInfo node : configuration.getNodes()) {
            transportClient.addTransportAddress(
                    new InetSocketTransportAddress(node.getHost(), node.getPort())
            );
        }
        ImmutableList<DiscoveryNode> nodes = transportClient.connectedNodes();
        if (nodes.isEmpty()) {
            transportClient.close();
            throw new RuntimeException("Could not connect to elasticsearch cluster."
                    + " Please check the elasticsearch-configuration.xml file.");
        }
        return transportClient;
    }
}