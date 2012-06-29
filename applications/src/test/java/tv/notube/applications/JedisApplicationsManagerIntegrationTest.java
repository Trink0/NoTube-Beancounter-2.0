package tv.notube.applications;

import com.google.inject.Guice;
import com.google.inject.Injector;
import junit.framework.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tv.notube.applications.model.Application;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;

/**
 * Reference test case for {@link JedisApplicationsManagerImpl}.
 *
 * @author Davide Palmisano ( dpalmisano@gmail.com )
 */
public class JedisApplicationsManagerIntegrationTest {

    private ApplicationsManager applicationsManager;

    @BeforeClass
    public void setUp() {
        Injector injector = Guice.createInjector(new ApplicationsModule());
        applicationsManager = injector.getInstance(ApplicationsManager.class);
    }

    @Test
    public void testRegisterAndDeregisterApplication()
            throws MalformedURLException, ApplicationsManagerException {
        final String name = "test-app";
        final String description = "a test app";
        final String email = "t@test.com";
        final URL oAuth = new URL("http://fake.com/oauth");
        UUID key = applicationsManager.registerApplication(
                name,
                description,
                email,
                oAuth
        );
        Application actual = applicationsManager.getApplicationByApiKey(key);
        Assert.assertNotNull(actual);
        Assert.assertEquals(actual.getApiKey(), key);
        Assert.assertEquals(actual.getName(), name);
        Assert.assertEquals(actual.getDescription(), description);
        Assert.assertEquals(actual.getCallback(), oAuth);

        Assert.assertTrue(applicationsManager.isAuthorized(
                key,
                ApplicationsManager.Action.CREATE,
                ApplicationsManager.Object.USER)
        );

        applicationsManager.deregisterApplication(key);

        actual = applicationsManager.getApplicationByApiKey(key);
        Assert.assertNull(actual);
    }
}