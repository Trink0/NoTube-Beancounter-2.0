package tv.notube.commons.alog.mybatis.mapper;

import org.apache.ibatis.annotations.Param;
import org.joda.time.DateTime;
import tv.notube.commons.alog.Activity;
import tv.notube.commons.alog.Field;

import java.util.List;
import java.util.UUID;

/**
 * put class description here
 *
 * @author Davide Palmisano ( dpalmisano@gmail.com )
 */
public interface ActivityLogMapper {

    public void insertActivity(
            @Param("activity") Activity activity
    );

    public void insertStringField(
            @Param("field") Field field
    );

    public void insertIntegerField(
            @Param("field") Field field
    );

    public List<Activity> selectActivityByOwner(
            @Param("owner") String owner
    );

    public List<Activity> selectActivityByDateRange(
            @Param("from") DateTime from,
            @Param("to") DateTime to
    );

    public List<Activity> selectActivityByDateRangeAndOwner(
            @Param("from") DateTime from,
            @Param("to") DateTime to,
            @Param("owner") String owner
    );

    public List<Field> selectActivityFields(UUID id);

    public void deleteActivitiesByDateRange(
            @Param("from") DateTime from,
            @Param("to") DateTime to
    );

    public void deleteActivitiesByDateRangeAndOwner(
            @Param("from") DateTime from,
            @Param("to") DateTime to,
            @Param("owner") String owner
    );

    public void deleteActivitiesByOwner(
            @Param("owner") String owner
    );

    public void deleteActivityFields(
            @Param("id") UUID activityId
    );
}