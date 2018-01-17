package animal.crossing.tunes.service;

import animal.crossing.tunes.data.DeviceGeocodingResponse;
import animal.crossing.tunes.data.DeviceTimezone;
import animal.crossing.tunes.data.GoogleData;
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

import java.io.IOException;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;

public class GoogleMapsClient {
    private static final Logger log = LoggerFactory.getLogger(GoogleMapsClient.class);

    interface ResponseParser{
        public GoogleData parseJsonString(String responseBody) throws IOException, JsonSyntaxException;
    }

    /**
     *
     * @param deviceAddress the address of the device
     * @param timestamp the request's timestamp in milliseconds
     * @return the calculated local time in milliseconds
     * @throws IOException
     */
    public long getDeviceTime(String deviceAddress, long timestamp) throws IOException, JsonSyntaxException {
        DeviceGeocodingResponse deviceGeocode = getGeocode(deviceAddress);

        String coordinates = getCoordinates(deviceGeocode);

        long timestampSeconds = TimeUnit.MILLISECONDS.toSeconds(timestamp);
        DeviceTimezone timezone = getTimezone(coordinates, timestampSeconds);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String prettyJson = gson.toJson(timezone);
        log.info("Timezone JSON: {}", prettyJson);

        return getCalculatedLocalTime(timezone, timestamp);
    }

    private long getCalculatedLocalTime(DeviceTimezone timezone, long timestamp){
        long dst = Long.valueOf(timezone.dstOffset);
        long offset = Long.valueOf(timezone.rawOffset);

        long msDst = TimeUnit.SECONDS.toMillis(dst);
        long msOffset = TimeUnit.SECONDS.toMillis(offset);

        log.info("msDst: {}, msOffset: {}", msDst, msOffset);

        return msDst + msOffset;
    }

    private String getCoordinates(DeviceGeocodingResponse geocode){
        DeviceGeocodingResponse.Location location = geocode.results.get(0).geometry.location;
        String latitude = location.lat;
        String longitude = location.lng;

        String coordinatesString = latitude + "," + longitude;

        log.info("Coordinates (lat,lng): {}", coordinatesString);
        return coordinatesString;
    }

    private DeviceGeocodingResponse getGeocode(String deviceAddress) throws IOException, JsonSyntaxException {
        final String googleService = "https://maps.googleapis.com/maps/api/geocode/json";
        final String address = "?address=" + URLEncoder.encode(deviceAddress, "UTF-8");
        final String apiKey = "&key=" + System.getenv("GEOCODE_KEY");

        return makeGoogleRequest(googleService, address, apiKey,
                responseBody -> {
//                    JsonParser parser = new JsonParser();
//                    JsonObject obj = parser.parse(responseBody).getAsJsonObject();
//
//                    JsonObject tmp = obj.get("results").getAsJsonArray()
//                            .get(0).getAsJsonObject()
//                            .get("geometry").getAsJsonObject()
//                            .get("location").getAsJsonObject();
//
//                    String latitude = String.valueOf(tmp.get("lat").getAsDouble());
//                    String longitude = String.valueOf(tmp.get("lng").getAsDouble());

                    Gson gson = new Gson();
                    DeviceGeocodingResponse deviceGeocode = gson.fromJson(responseBody, DeviceGeocodingResponse.class);
                    return deviceGeocode;
                }, DeviceGeocodingResponse.class);
    }

    private DeviceTimezone getTimezone(String coordinates, long deviceTimestampSeconds) throws IOException, JsonSyntaxException {
        final String googleService = "https://maps.googleapis.com/maps/api/timezone/json";
        final String location = "?location=" + coordinates;
        final String timestamp = "&timestamp=" + deviceTimestampSeconds;
        final String apiKey = "&key=" + System.getenv("TIME_ZONE_KEY");

        return makeGoogleRequest(googleService, location + timestamp, apiKey,
                responseBody -> {
                    Gson gson = new GsonBuilder().setPrettyPrinting().create();
                    DeviceTimezone deviceTimezone = gson.fromJson(responseBody, DeviceTimezone.class);

                    return deviceTimezone;
        }, DeviceTimezone.class);
    }

    private <T extends GoogleData> T makeGoogleRequest(
            final String GOOGLE_SERVICE, final String PARAMETERS,
            final String API_KEY, ResponseParser responseParser, Class<T> type) throws IOException, JsonSyntaxException{

        CloseableHttpClient httpClient = HttpClients.createDefault();

        String requestUrl = GOOGLE_SERVICE + PARAMETERS + API_KEY;

        log.info("User address request will be made to the following URL: {}", requestUrl);

        HttpGet httpGet = new HttpGet(requestUrl);

        log.info("Executing request " + httpGet.getRequestLine());

        String responseBody = null;

        responseBody = httpClient.execute(httpGet, response -> {
            int status = response.getStatusLine().getStatusCode();
            if (status >= 200) {
                HttpEntity entity = response.getEntity();
                return (entity != null) ? EntityUtils.toString(entity) : null;
            } else {
                throw new ClientProtocolException("Unexpected response status: " + status);
            }
        });

        if(responseBody != null){
            GoogleData googleData = responseParser.parseJsonString(responseBody);
            return type.cast(googleData);
        }

        return null;
    }
}
