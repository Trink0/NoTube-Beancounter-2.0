package tv.notube.resolver.process;

import java.util.UUID;

import com.google.inject.Inject;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tv.notube.commons.model.activity.Activity;
import tv.notube.commons.model.activity.ResolvedActivity;
import tv.notube.resolver.Resolver;

/**
 * @author Enrico Candino ( enrico.candino@gmail.com )
 */
public class ResolverRoute extends RouteBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResolverRoute.class);

    @Inject
    private Resolver resolver;

    public void configure() {
        errorHandler(deadLetterChannel(errorEndpoint()));

        from(fromKestrelEndpoint())
                // ?concurrentConsumers=10&waitTimeMs=500
                .convertBodyTo(String.class)
                .unmarshal().json(JsonLibrary.Jackson, Activity.class)
                .process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        Activity activity = exchange.getIn().getBody(Activity.class);
                        LOGGER.debug("Resolving username {}.", activity);
                        UUID userId = resolver.resolve(activity);
                        if (userId == null) {
                            exchange.getIn().setBody(null);
                        } else {
                            exchange.getIn().setBody(new ResolvedActivity(userId, activity)
                            );
                        }
                        LOGGER.debug("resolved username [{}-{}].", activity.getContext().getUsername(), userId);
                    }
                })
                .filter(body().isNotNull())
                .marshal().json(JsonLibrary.Jackson)
                .convertBodyTo(String.class)

                .multicast().parallelProcessing().to(toInternalQueue(), toFilterQueue());
    }

    protected String toFilterQueue() {
        return "kestrel://{{kestrel.queue.filter.url}}";
    }

    protected String toInternalQueue() {
        return "kestrel://{{kestrel.queue.internal.url}}";
    }

    protected String fromKestrelEndpoint() {
        return "kestrel://{{kestrel.queue.social.url}}";
    }

    protected String errorEndpoint() {
        return "log:resolverRoute?level=ERROR";
    }
}