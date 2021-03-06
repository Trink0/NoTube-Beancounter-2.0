package io.beancounter.platform.rai;

import com.google.inject.Inject;
import io.beancounter.commons.model.User;
import io.beancounter.commons.model.auth.SimpleAuth;
import io.beancounter.platform.JsonService;
import io.beancounter.platform.PlatformResponse;
import io.beancounter.platform.responses.MyRaiTVSignUpResponse;
import io.beancounter.platform.validation.Validations;
import io.beancounter.usermanager.UserManager;
import io.beancounter.usermanager.UserManagerException;
import io.beancounter.usermanager.UserTokenManager;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.UUID;

/**
 * This class is exclusively responsible to handle signup and login procedures
 * for <a href="http://rai.tv/">myRAI</a> logins. This class will be not part of the
 * official <a href="http://api.beancounter.io>beancounter API</a>.
 *
 * @author Davide Palmisano ( dpalmisano@gmail.com )
 */
@Path("rest/rai")
@Produces(MediaType.APPLICATION_JSON)
public class MyRaiTVService extends JsonService {

    private UserManager userManager;

    private UserTokenManager tokenManager;

    private MyRaiTVAuthHandler authHandler;

    private static final String SERVICE_NAME = "myRai";

    @Inject
    public MyRaiTVService(
            UserManager userManager,
            UserTokenManager tokenManager,
            MyRaiTVAuthHandler authHandler
    ) {
        this.userManager = userManager;
        this.tokenManager = tokenManager;
        this.authHandler = authHandler;
    }

    @POST
    @Path("/login")
    public Response login(
            @FormParam("username") String username,
            @FormParam("password") String password
    ) {
        try {
            Validations.checkNotEmpty(username, "Missing username parameter");
            Validations.checkNotEmpty(password, "Missing password parameter");
        } catch (Exception ex) {
            return error(ex.getMessage());
        }

        MyRaiTVAuthResponse response;
        try {
            response = authHandler.authOnRai(username, password);
        } catch (MyRaiTVAuthException e) {
            return error(e.getMessage());
        } catch (Exception e) {
            return error(e, "Error while authenticating [" + username + "] on myRai auth service");
        }

        return loginWithAuth(response.getUsername(), response.getToken());
    }

    @POST
    @Path("/login/auth")
    public Response loginWithAuth(
            @FormParam("username") String username,
            @FormParam("token") String token

    ) {
        try {
            Validations.checkNotEmpty(username, "Missing username parameter");
            Validations.checkNotEmpty(token, "Missing MyRaiTV token parameter");
        } catch (Exception ex) {
            return error(ex.getMessage());
        }

        User user;
        try {
            user = userManager.getUser(username);
        } catch (UserManagerException e) {
            return error(e, "Error while getting beancounter.io user with name [" + username + "]");
        }
        // user is not there, hence must be registered
        if (user == null) {
            try {
                user = getNewUser(username, token);
                userManager.storeUser(user);
            } catch (UserManagerException e) {
                return error(e, "error while storing user [" + username + "] on beancounter.io");
            }
            MyRaiTVSignUp signUp = new MyRaiTVSignUp();
            signUp.setReturning(false);
            signUp.setService(SERVICE_NAME);
            signUp.setUserId(user.getId());
            signUp.setUsername(username);
            signUp.setIdentifier(username);
            signUp.setRaiToken(token);
            signUp.setUserToken(user.getUserToken());
            Response.ResponseBuilder rb = Response.ok();
            rb.entity(
                    new MyRaiTVSignUpResponse(
                            PlatformResponse.Status.OK,
                            "user with user name [" + signUp.getUsername() + "] logged in with service [" + signUp.getService() + "]",
                            signUp
                    )
            );
            return rb.build();
        } else {
            try {
                user.addService(SERVICE_NAME, new SimpleAuth(token, username));
                if (user.getUserToken() != null) {
                    tokenManager.deleteUserToken(user.getUserToken());
                }
                UUID userToken = tokenManager.createUserToken(username);
                user.setUserToken(userToken);
                userManager.storeUser(user);
            } catch (UserManagerException ume) {
                return error(ume, "error while storing user [" + username + "] on beancounter.io");
            }

            MyRaiTVSignUp signUp = new MyRaiTVSignUp();
            signUp.setReturning(true);
            signUp.setService(SERVICE_NAME);
            signUp.setUserId(user.getId());
            signUp.setUsername(username);
            signUp.setIdentifier(username);
            signUp.setRaiToken(token);
            signUp.setUserToken(user.getUserToken());
            Response.ResponseBuilder rb = Response.ok();
            rb.entity(
                    new MyRaiTVSignUpResponse(
                            PlatformResponse.Status.OK,
                            "user with user name [" + signUp.getUsername() + "] logged in with service [" + signUp.getService() + "]",
                            signUp
                    )
            );
            return rb.build();
        }
    }

    private User getNewUser(String username, String token) throws UserManagerException {
        User user = new User();
        user.setUsername(username);
        user.addService(SERVICE_NAME, new SimpleAuth(token, username));
        UUID userToken = tokenManager.createUserToken(username);
        user.setUserToken(userToken);
        return user;
    }
}