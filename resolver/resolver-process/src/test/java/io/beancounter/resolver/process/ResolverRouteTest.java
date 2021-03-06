package io.beancounter.resolver.process;

import java.util.UUID;

import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.testng.CamelTestSupport;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import io.beancounter.commons.model.User;
import io.beancounter.commons.model.activity.Activity;
import io.beancounter.commons.model.auth.OAuthAuth;
import io.beancounter.resolver.Resolver;
import io.beancounter.usermanager.UserManager;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ResolverRouteTest extends CamelTestSupport {

    private Injector injector;

    private Resolver resolver;

    private UserManager userManager;

    @BeforeMethod
    public void setUp() throws Exception {
        injector = Guice.createInjector(new Module() {
            @Override
            public void configure(Binder binder) {
                resolver = mock(Resolver.class);
                userManager = mock(UserManager.class);
                binder.bind(UserManager.class).toInstance(userManager);
                binder.bind(Resolver.class).toInstance(resolver);
                binder.bind(ResolverRoute.class).toInstance(new ResolverRoute() {
                    @Override
                    protected String fromEndpoint() {
                        return "direct:start";
                    }

                    @Override
                    public String toInternalQueue() {
                        return "mock:internal";
                    }

                    @Override
                    public String errorEndpoint() {
                        return "mock:error";
                    }
                });
            }
        });
        super.setUp();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return injector.getInstance(ResolverRoute.class);
    }

    @Test
    public void unresolvedActivitiesAreDropped() throws Exception {
        MockEndpoint internal = getMockEndpoint("mock:internal");
        internal.expectedMessageCount(0);

        MockEndpoint error = getMockEndpoint("mock:error");
        error.expectedMessageCount(0);

        template.sendBody("direct:start", anActivity());
        internal.assertIsSatisfied();
        error.assertIsSatisfied();
    }


    @Test
    public void resolvedActivitiesAreSentToTarget() throws Exception {
        MockEndpoint internal = getMockEndpoint("mock:internal");
        internal.expectedMessageCount(1);

        MockEndpoint error = getMockEndpoint("mock:error");
        error.expectedMessageCount(0);

        when(resolver.resolve(any(Activity.class))).thenReturn(UUID.randomUUID());
        when(resolver.resolveUsername(anyString(), anyString())).thenReturn("test-username");
        when(userManager.getUser(any(String.class))).thenReturn(getRandomUser());

        template.sendBody("direct:start", anActivity());
        internal.assertIsSatisfied();
        error.assertIsSatisfied();
    }

    private String anActivity() {
        return "{\"id\":\"891aed77-7f8b-4a28-991f-34683a281ead\",\"verb\":\"TWEET\",\"object\":{\"type\":\"TWEET\",\"url\":\"http://twitter.com/ElliottWilson/status/220164023340118017\",\"name\":\"ElliottWilson\",\"description\":null,\"text\":\"RT @RapRadar3: RAPRADAR: New Mixtape: Theophilus London Rose Island Vol. 1 http://t.co/BynRjPJm\",\"hashTags\":[],\"urls\":[\"http://bit.ly/P5Tzc1\"]},\"context\":{\"date\":1341326168000,\"service\":\"http://sally.beancounter.io\",\"mood\":null}}";
    }

    private User getRandomUser() {
        User user = new User();
        user.setName("test-name");
        user.setSurname("test-surname");
        user.setPassword("test-pwd");
        user.setUsername("test-username");
        user.addService("facebook", new OAuthAuth("s", "c"));
        return user;
    }
}

