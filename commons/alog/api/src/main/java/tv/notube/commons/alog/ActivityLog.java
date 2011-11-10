package tv.notube.commons.alog;

import org.joda.time.DateTime;

import java.util.UUID;

/**
 * @author Davide Palmisano ( dpalmisano@gmail.com )
 */
public interface ActivityLog {

    public void log(String owner, String description, Field... fields)
            throws ActivityLogException;

    public Field[] getFields(UUID activityId) throws ActivityLogException;

    public Activity[] filter(DateTime from, DateTime to) throws
            ActivityLogException;

    public Activity[] filter(
            DateTime from,
            DateTime to,
            String owner
    ) throws ActivityLogException;

    public Activity[] filter(
            DateTime from,
            DateTime to,
            String owner,
            Query query
    ) throws ActivityLogException;

    public void delete(DateTime from, DateTime to)
            throws ActivityLogException;

    public void delete(DateTime from, DateTime to, String owner)
            throws ActivityLogException;

    public void delete(String owner) throws ActivityLogException;

}