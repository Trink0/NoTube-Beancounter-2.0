package tv.notube.indexer;

import java.util.HashSet;
import java.util.Set;

import com.google.inject.Inject;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tv.notube.activities.ActivityStore;
import tv.notube.activities.ActivityStoreException;
import tv.notube.commons.model.UserProfile;
import tv.notube.commons.model.activity.ResolvedActivity;
import tv.notube.filter.FilterService;
import tv.notube.profiler.Profiler;

public class IndexerRoute extends RouteBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(IndexerRoute.class);
    public static final String ENDPOINTS_HEADER = "endpoints";

    @Inject
    private ActivityStore activityStore;

    @Inject
    private Profiler profiler;

    @Inject
    private FilterService filterService;

    public void configure() {
        errorHandler(deadLetterChannel(errorEndpoint()));

        from(fromKestrel())
                .unmarshal().json(JsonLibrary.Jackson, ResolvedActivity.class)
                .multicast().parallelProcessing().to("direct:es", "direct:profiler", "direct:filter");

        from("direct:es")
                .process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        ResolvedActivity resolvedActivity = exchange.getIn().getBody(ResolvedActivity.class);
                        try {
                            activityStore.store(resolvedActivity.getUserId(), resolvedActivity.getActivity());
                        } catch (Exception e) {
                            final String errMsg = "Error while storing " + "resolved activity for user ["
                                    + resolvedActivity.getUserId() + "]";
                            LOGGER.error(errMsg, e);
                            throw new ActivityStoreException(errMsg, e);
                        }
                    }
                });

        from("direct:profiler")
                .process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        ResolvedActivity resolvedActivity = exchange.getIn().getBody(ResolvedActivity.class);
                        LOGGER.debug("Profiling activity {}.", resolvedActivity);
                        try {
                            UserProfile profile = profiler.profile(
                                    resolvedActivity.getUserId(),
                                    resolvedActivity.getActivity()
                            );
                        } catch (Exception e) {
                            // log the error but do not raise an exception
                            final String errMsg = "Error while profiling user [" + resolvedActivity
                                    .getUserId() + "]";
                            LOGGER.error(errMsg, e);
                        }
                        // (TODO) (low) profile will be sent in a down stream queue
                        // meant to persist all the profiles of every user
                        // and yes, even to other real-time processes
                        // exchange.getIn().setBody(profile);
                    }
                }
                );
        // TODO (out of release 1.0) turn on profiling analytics
        /**
         .marshal().json(JsonLibrary.Jackson)
         .convertBodyTo(String.class)
         .to("kestrel://{{kestrel.queue.analytics}}");
         **/


        from("direct:filter")
                .process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        ResolvedActivity resolvedActivity = exchange.getIn().getBody(ResolvedActivity.class);
                        Set<String> targets = filterService.processActivity(resolvedActivity);
                        targets = appendTargetPrefix(targets);
                        exchange.getIn().setHeader(ENDPOINTS_HEADER, targets);
                    }
                })

                .filter(header(ENDPOINTS_HEADER).isNotNull())

                .recipientList(header(ENDPOINTS_HEADER)).parallelProcessing().ignoreInvalidEndpoints();
    }

    protected Set<String> appendTargetPrefix(Set<String> targets) {
        if (targets != null) {
            Set<String> prefixedTargets = new HashSet<String>();
            for (String target : targets) {
                prefixedTargets.add("kestrel://{{kestrel.queue.filter.prefix.url}}" + target);
            }
            return prefixedTargets;
        }
        return null;
    }

    protected String fromKestrel() {
        return "kestrel://{{kestrel.queue.internal.url}}?concurrentConsumers=10&waitTimeMs=500";
    }

    protected String errorEndpoint() {
        return "log:indexerRoute?level=ERROR";
    }

}
