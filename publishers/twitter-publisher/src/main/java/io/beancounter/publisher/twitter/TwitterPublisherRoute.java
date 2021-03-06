package io.beancounter.publisher.twitter;

import com.google.inject.Inject;
import io.beancounter.commons.model.activity.ResolvedActivity;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

/**
 *
 * @author Enrico Candino ( enrico.candino@gmail.com )
 */
public class TwitterPublisherRoute extends RouteBuilder {

    @Inject
    private TwitterPublisher twitterPublisher;

    @Override
    public void configure() throws Exception {
        errorHandler(deadLetterChannel(errorEndpoint()));

        from(fromEndpoint())
                .unmarshal().json(JsonLibrary.Jackson, ResolvedActivity.class)
                .process(twitterPublisher);
    }

    protected String fromEndpoint() {
        return "kestrel://{{kestrel.queue.twitter.url}}";
    }

    protected String errorEndpoint() {
        return "log:" + getClass().getSimpleName() + "?{{camel.log.options.error}}";
    }

}
