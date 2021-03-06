package io.beancounter.commons.model.activity;

import org.joda.time.DateTime;
import io.beancounter.commons.model.activity.adapters.DateTimeAdapterJAXB;
import io.beancounter.commons.tests.annotations.Random;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.io.Serializable;
import java.lang.*;
import java.lang.Object;

/**
 * @author Davide Palmisano ( dpalmisano@gmail.com )
 */
@XmlRootElement
public class Context implements Serializable {

    private static final long serialVersionUID = 325277757335L;

    /**
     * Date of when the activity occurred.
     */
    private DateTime date;

    /**
     * On which service, es: Facebook, Twitter ...
     */
    private String service;

    private String mood;

    /**
     * username or identifier of the user on that given service.
     * Es: if the activity has been performed on facebook, here you should put
     * its facebook identifier.
     */
    private String username;

    public Context() {}

    @Random( names = { "d" } )
    public Context(DateTime d) {
        date = d;
    }

    @XmlJavaTypeAdapter(DateTimeAdapterJAXB.class)
    public DateTime getDate() {
        return date;
    }

    public void setDate(DateTime date) {
        this.date = date;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getMood() {
        return mood;
    }

    public void setMood(String mood) {
        this.mood = mood;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public String toString() {
        return "Context{" +
                "date=" + date +
                ", service=" + service +
                ", mood='" + mood + '\'' +
                ", username='" + username + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Context)) return false;

        Context context = (Context) o;

        if (date != null) {
            if (context.date == null) return false;
            if (date.getMillis() != context.date.getMillis()) return false;
        } else {
            if (context.date != null) return false;
        }

        if (service != null ? !service.equals(context.service) : context.service != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = date != null ? date.hashCode() : 0;
        result = 31 * result + (service != null ? service.hashCode() : 0);
        return result;
    }
}
