package tv.notube.platform.validation;

import javax.ws.rs.core.Response;
import java.util.Map;

import static tv.notube.platform.Service.IS_VISIBLE;
import static tv.notube.platform.Service.VISIBILITY_OBJ;
import static tv.notube.platform.validation.RequestValidator.error;

public class VisibilityValidation implements Validation {

    @Override
    public Response validate(Map<String, Object> params) {
        boolean vObj;
        String visibility = (String) params.get(IS_VISIBLE);

        try {
            vObj = Boolean.valueOf(visibility);
        } catch (Exception e) {
            return error(e, "visibility parameter must be {true, false} and not [" + visibility + "]");
        }

        params.put(VISIBILITY_OBJ, vObj);

        return null;
    }
}
