package animal.crossing.tunes.service;

import animal.crossing.tunes.data.DeviceGeocodingResponse;
import animal.crossing.tunes.data.DeviceTimezone;
import animal.crossing.tunes.data.GoogleData;
import animal.crossing.tunes.exception.UnexpectedResultException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;

public class GoogleMapsClient {
    private static final Logger log = LoggerFactory.getLogger(GoogleMapsClient.class);

    private final String TIMEZONE_SERVICE = "Time Zone API";
    private final String GEOCODE_SERVICE = "Geocode API";

    interface ResponseParser{
        GoogleData parseJsonString(String responseBody);
    }

    /**
     *
     * @param deviceAddress The address of the device.
     * @param timestamp The device's timestamp in milliseconds. Needed to calculate the time zone.
     * @return The calculated local offset that is used to determine the device's local time.
     */
    public long getDeviceLocalOffset(String deviceAddress, long timestamp) {
        long deviceLocalTimeOffset = 0;

        try{
            DeviceGeocodingResponse deviceGeocode = getGeocode(deviceAddress);

            String coordinates = createCoordinatesString(deviceGeocode);

            // Google's Time Zone API only takes seconds
            long timestampSeconds = TimeUnit.MILLISECONDS.toSeconds(timestamp);

            // Use the timestamp in seconds and the coordinates of device to calculate the local time zone offset.
            DeviceTimezone timezone = getTimezone(coordinates, timestampSeconds);

            log.info("Timezone JSON: {}", timezone.toString());

            // Calculate the final offset and return it
            deviceLocalTimeOffset = getFinalOffset(timezone);

        }catch (UnexpectedResultException e){
            log.error("Could not determine the device's local offset.");
        }

        return deviceLocalTimeOffset;
    }

    /**
     * Returns the final offset in milliseconds.
     * @param timezone The POJO representing the response from Google's Time Zone API
     * @return The sum of the local daylight savings time offset and the local raw offset.
     */
    private long getFinalOffset(DeviceTimezone timezone){
        // Get the daylight savings time offset and the raw local offset from the POJO
        long dst = Long.valueOf(timezone.dstOffset);
        long offset = Long.valueOf(timezone.rawOffset);

        // Google Time Zone API returns to us in seconds, so convert to milliseconds
        long msDst = TimeUnit.SECONDS.toMillis(dst);
        long msOffset = TimeUnit.SECONDS.toMillis(offset);

        log.info("msDst: {}, msOffset: {}", msDst, msOffset);

        // The final calculated offset = (daylight savings offset + local raw offset)
        return msDst + msOffset;
    }

    private String createCoordinatesString(DeviceGeocodingResponse geocode){
        DeviceGeocodingResponse.Location location = geocode.results.get(0).geometry.location;

        // Get the latitude and longitude of the device
        String latitude = location.lat;
        String longitude = location.lng;

        // concat the coordinates to easily use in the time zone API call
        // Note we are URL encoding a comma here
        String coordinatesString = latitude + "%2C" + longitude;
        log.info("Coordinates (lat,lng): {}", coordinatesString);

        return coordinatesString;
    }

    private DeviceGeocodingResponse getGeocode(String deviceAddress) throws UnexpectedResultException {
        final String googleService = "https://maps.googleapis.com/maps/api/geocode/json";
        final String address = "?address=" + deviceAddress;
        final String apiKey = "&key=" + System.getenv("GEOCODE_KEY");

        String url = googleService + address + apiKey;

        DeviceGeocodingResponse geocodingResponse = null;
        try{
            geocodingResponse = makeGoogleRequest(GEOCODE_SERVICE, url, responseBody -> {
                try{
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    return gson.fromJson(responseBody, DeviceGeocodingResponse.class);
                } catch(JsonSyntaxException e){
                    log.error("JsonSyntaxException when trying to convert Google Geocode API response to a POJO.", e);
                    return null;
                }
            }, DeviceGeocodingResponse.class);
        }catch (IOException e){
            log.error("IOException when trying to get the coordinates of the device.", e);
            throw new UnexpectedResultException();
        }

        return geocodingResponse;
    }

    private DeviceTimezone getTimezone(String coordinates, long deviceTimestampSeconds) throws UnexpectedResultException {
        final String googleService = "https://maps.googleapis.com/maps/api/timezone/json";
        final String location = "?location=" + coordinates;
        final String timestamp = "&timestamp=" + deviceTimestampSeconds;
        final String apiKey = "&key=" + System.getenv("TIME_ZONE_KEY");

        String url = googleService + location + timestamp + apiKey;

        DeviceTimezone deviceTimezone = null;
        try{
            deviceTimezone = makeGoogleRequest(TIMEZONE_SERVICE, url, responseBody -> {
                try{
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    return gson.fromJson(responseBody, DeviceTimezone.class);
                } catch(JsonSyntaxException e){
                    log.error("JsonSyntaxException when trying to convert Google Time Zone API response to a POJO.", e);
                    return null;
                }
            }, DeviceTimezone.class);
        } catch(IOException e){
            log.error("IOException when trying to get the local timezone offset of the device.", e);
            throw new UnexpectedResultException();
        }

        return deviceTimezone;
    }

    private <T extends GoogleData> T makeGoogleRequest(String serviceName, String url, ResponseParser responseParser, Class<T> type)
            throws IOException{

        CloseableHttpClient httpClient = HttpClients.createDefault();

        log.info("{} request will be made to the following URL: {}", serviceName, url);

        HttpGet httpGet = new HttpGet(url);

        log.info("Executing request " + httpGet.getRequestLine());

        String responseBody = httpClient.execute(httpGet, response -> {
            int status = response.getStatusLine().getStatusCode();
            if (status >= 200) {
                HttpEntity entity = response.getEntity();
                return (entity != null) ? EntityUtils.toString(entity) : null;
            } else {
                throw new ClientProtocolException("Unexpected response status: " + status);
            }
        });

        GoogleData googleData = null;
        if(responseBody != null){
            googleData = responseParser.parseJsonString(responseBody);
            return type.cast(googleData);
        }else{
            log.error("Entity from {} was null.", serviceName);
        }

        return type.cast(googleData);
    }
}
