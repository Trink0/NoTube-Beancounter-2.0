package io.beancounter.commons.model;

import io.beancounter.commons.model.auth.Auth;
import io.beancounter.commons.tests.annotations.Random;

import java.io.Serializable;
import java.util.*;

/**
 * Models the main <a href="http://beancounter.io">User</a>
 * characteristics.
 *
 * @author Davide Palmisano ( dpalmisano@gmail.com )
 */
public class User implements Serializable {

    private static final long serialVersionUID = 324345235L;

    private UUID id;

    private String name;

    private String surname;

    private Map<String, Auth> services = new HashMap<String, Auth>();

    private String password;

    private String username;

    private Map<String, String> metadata = new HashMap<String, String>();

    private UUID userToken;

    public User() {
        id = UUID.randomUUID();
    }

    @Random(names = {"name", "surname", "username", "password"})
    public User(String name, String surname, String username, String password) {
        this();
        this.name = name;
        this.surname = surname;
        this.username = username;
        this.password = password;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    public Map<String, Auth> getServices() {
        return services;
    }

    public void setServices(Map<String, Auth> services) {
        this.services = services;
    }

    public Auth getAuth(String service) {
        return services.get(service);
    }

    public void addService(String service, Auth auth) {
        services.put(service, auth);
    }

    public void removeService(String service) {
        services.remove(service);
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPassword() {
        return password;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public void addMetadata(String key, String value) {
        metadata.put(key, value);
    }

    public String getMetadata(String key) {
        return metadata.get(key);
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public UUID getUserToken() {
        return userToken;
    }

    public void setUserToken(UUID userToken) {
        this.userToken = userToken;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        User user = (User) o;

        if (username != null ? !username.equals(user.username) : user.username != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        return username != null ? username.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", surname='" + surname + '\'' +
                ", services=" + services +
                ", password='" + password + '\'' +
                ", username='" + username + '\'' +
                ", metadata=" + metadata +
                '}';
    }
}
