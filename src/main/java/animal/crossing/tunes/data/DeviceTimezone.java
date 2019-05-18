package animal.crossing.tunes.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * The json information retrieved from the Google Time Zone API represented in a Java POJO
 */
public class DeviceTimezone extends GoogleData{
    public String dstOffset;
    public String rawOffset;
    public String status;
    public String timeZoneId;
    public String timeZoneName;
}
