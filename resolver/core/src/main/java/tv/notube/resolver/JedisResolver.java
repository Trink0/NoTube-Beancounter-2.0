package tv.notube.resolver;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import tv.notube.commons.helper.jedis.JedisPoolFactory;
import tv.notube.commons.helper.resolver.Services;
import tv.notube.commons.model.activity.Activity;

import java.util.UUID;

/**
 * <i>Redis</i>-based implementation of {@link Resolver}.
 *
 * @author Enrico Candino ( enrico.candino@gmail.com )
 */
@Singleton
public class JedisResolver implements Resolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(JedisResolver.class);

    private JedisPool pool;

    private Services services;

    @Inject
    public JedisResolver(
            JedisPoolFactory factory,
            Services services
    ) {
        pool = factory.build();
        this.services = services;
    }

    @Override
    public UUID resolve(Activity activity) throws ResolverException {
        String service = activity.getContext().getService();
        int database;
        try {
            database = services.get(service);
        } catch (NullPointerException e) {
            final String errmsg = "Service [" + service + "] not supported";
            LOGGER.error(errmsg, e);
            throw new ResolverException(errmsg, e);
        }
        Jedis jedis = pool.getResource();
        jedis.select(database);
        String userIdentifier = activity.getContext().getUsername();
        String userId;
        try {
            userId = jedis.hget(userIdentifier, "uuid");
        } finally {
            pool.returnResource(jedis);
        }
        if (userId == null) {
            final String errmsg = "User [" + userIdentifier + "] not found for [" + service + "]";
            LOGGER.error(errmsg);
            throw new ResolverException(errmsg);
        }
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            final String errmsg = "Illformed beancounter userId [" + userId +
                    "] for userIdentifier [" + userIdentifier + "] in service [" + service + "]";
            LOGGER.error(errmsg, e);
            throw new ResolverException(errmsg, e);
        }
    }

    @Override
    public UUID resolveId(String identifier, String service) throws ResolverException {
        int database;
        try {
            database = services.get(service);
        } catch (NullPointerException e) {
            final String errmsg = "Service [" + service + "] not supported";
            LOGGER.error(errmsg, e);
            throw new ResolverException(errmsg, e);
        }
        Jedis jedis = pool.getResource();
        jedis.select(database);
        String userId;
        try {
            userId = jedis.hget(identifier, "uuid");
        } finally {
            pool.returnResource(jedis);
        }
        if (userId == null) {
            final String errmsg = "User [" + identifier + "] not found for [" + service + "]";
            LOGGER.error(errmsg);
            throw new ResolverException(errmsg);
        }
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            final String errmsg = "Illformed beancounter userId [" + userId +
                    "] for userIdentifier [" + identifier + "] in service [" + service + "]";
            LOGGER.error(errmsg, e);
            throw new ResolverException(errmsg, e);
        }
    }

    @Override
    public String resolveUsername(String identifier, String service) throws ResolverException {
        int database;
        try {
            database = services.get(service);
        } catch (NullPointerException e) {
            final String errmsg = "Service [" + service + "] not supported";
            LOGGER.error(errmsg, e);
            throw new ResolverException(errmsg, e);
        }
        Jedis jedis = pool.getResource();
        jedis.select(database);
        String username;
        try {
            username = jedis.hget(identifier, "username");
        } finally {
            pool.returnResource(jedis);
        }
        if (username == null) {
            final String errmsg = "User [" + identifier + "] not found for [" + service + "]";
            LOGGER.error(errmsg);
            throw new ResolverException(errmsg);
        }
        return username;
    }

    @Override
    public void store(String identifier, String service, UUID userId, String username)
            throws ResolverException {
        if (username == null) {
            throw new IllegalArgumentException("username parameter cannot be null");
        }
        if (service == null) {
            throw new IllegalArgumentException("service parameter cannot be null");
        }
        int database;
        try {
            database = services.get(service);
        } catch (NullPointerException e) {
            final String errMsg = "Service [" + service + "] not supported";
            LOGGER.error(errMsg, e);
            throw new ResolverException(errMsg, e);
        }
        Jedis jedis = pool.getResource();
        jedis.select(database);
        try {
            jedis.hset(identifier, "uuid", userId.toString());
            jedis.hset(identifier, "username", username);
        } finally {
            pool.returnResource(jedis);
        }
    }


}