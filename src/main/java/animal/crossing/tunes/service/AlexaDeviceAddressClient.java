package animal.crossing.tunes.service;

import animal.crossing.tunes.data.DeviceAddress;
import com.google.gson.Gson;
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

public class AlexaDeviceAddressClient {
    private static final Logger log = LoggerFactory.getLogger(AlexaDeviceAddressClient.class);

    private String deviceId;
    private String apiAccessToken;
    private String apiEndpoint;

    public AlexaDeviceAddressClient(String deviceId, String apiAccessToken, String apiEndpoint){
        this.deviceId = deviceId;
        this.apiAccessToken = apiAccessToken;
        this.apiEndpoint = apiEndpoint;
    }

    public DeviceAddress getAddress() {
        final String BASE_API_PATH = "/v1/devices/";
        final String SETTINGS_PATH = "/settings/";
        final String COUNTRY_AND_POSTAL_CODE_PATH = "address/countryAndPostalCode";

        String requestUrl
                = apiEndpoint + BASE_API_PATH + deviceId + SETTINGS_PATH + COUNTRY_AND_POSTAL_CODE_PATH;
        log.info("User address request will be made to the following URL: {}", requestUrl);

        HttpGet httpGet = new HttpGet(requestUrl);

        httpGet.setHeader("Host", "api.amazonalexa.com");
        httpGet.setHeader("Accept", "application/json");
        httpGet.setHeader("Authorization", "Bearer " + apiAccessToken);

        log.info("Executing request " + httpGet.getRequestLine());

        CloseableHttpClient httpClient = HttpClients.createDefault();

        log.info("Calling Alexa API to get device address.");

        DeviceAddress deviceAddress = null;
        try{
            String responseBody = httpClient.execute(httpGet, (response) -> {
                int status = response.getStatusLine().getStatusCode();
                log.info("Response Status: {}", status);
                if (status == 200) {
                    HttpEntity entity = response.getEntity();
                    return (entity != null) ? EntityUtils.toString(entity) : null;
                } else if(status == 403){
                    log.error("Unauthorized permissions to view address of device ID: {)", deviceId);
                    throw new ClientProtocolException("Failed to get device address.");
                } else {
                    throw new ClientProtocolException("Unexpected response status: " + status);
                }
            });

            if(responseBody != null){
                Gson gson = new Gson();
                deviceAddress = gson.fromJson(responseBody, DeviceAddress.class);
            }else{
                log.error("Entity from Amazon address API was null.");
            }
        } catch (IOException e){
            log.info("Exception when trying to get device address.", e);
        } catch (JsonSyntaxException e){
            log.error("JsonSyntaxException when trying to convert Amazon address API response to a POJO.", e);
        }

        return deviceAddress;
    }

}
