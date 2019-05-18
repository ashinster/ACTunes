package animal.crossing.tunes;

import animal.crossing.tunes.data.DeviceAddress;
import animal.crossing.tunes.service.AlexaDeviceAddressClient;
import animal.crossing.tunes.service.DbOffsetClient;
import animal.crossing.tunes.service.GoogleMapsClient;
import com.amazon.speech.json.SpeechletRequestEnvelope;
import com.amazon.speech.slu.Intent;
import com.amazon.speech.speechlet.*;
import com.amazon.speech.speechlet.interfaces.audioplayer.AudioPlayer;
import com.amazon.speech.speechlet.interfaces.audioplayer.ClearBehavior;
import com.amazon.speech.speechlet.interfaces.audioplayer.directive.ClearQueueDirective;
import com.amazon.speech.speechlet.interfaces.audioplayer.request.*;
import com.amazon.speech.speechlet.interfaces.system.SystemInterface;
import com.amazon.speech.speechlet.interfaces.system.SystemState;
import com.amazon.speech.ui.*;
import com.amazonaws.services.dynamodbv2.document.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ACTunesPlayer implements AudioPlayer, SpeechletV2 {
    private static final Logger log = LoggerFactory.getLogger(ACTunesPlayer.class);

    private String currentSong;
    private String deviceTimeOffset;

    @Override
    public SpeechletResponse onPlaybackFailed(SpeechletRequestEnvelope<PlaybackFailedRequest> speechletRequestEnvelope) {
        log.error("Inside onPlaybackFailed() | {} - {} ",
                speechletRequestEnvelope.getRequest().getError().getMessage(),
                speechletRequestEnvelope.getRequest().getError().getType().toString());

        PlaybackFailedRequest intentRequest = speechletRequestEnvelope.getRequest();
        SystemState systemState = getSystemState(speechletRequestEnvelope.getContext());
        this.currentSong = intentRequest.getToken();

        return null;
    }

    @Override
    public SpeechletResponse onPlaybackFinished(SpeechletRequestEnvelope<PlaybackFinishedRequest> speechletRequestEnvelope) {
        log.info("Inside onPlaybackFinished()");

        PlaybackFinishedRequest intentRequest = speechletRequestEnvelope.getRequest();
        SystemState systemState = getSystemState(speechletRequestEnvelope.getContext());
        this.currentSong = intentRequest.getToken();
        Date date = intentRequest.getTimestamp();

        return null;
    }

    @Override
    public SpeechletResponse onPlaybackNearlyFinished(SpeechletRequestEnvelope<PlaybackNearlyFinishedRequest> speechletRequestEnvelope) {
        log.info("Inside onPlaybackNearlyFinished()");

        PlaybackNearlyFinishedRequest intentRequest = speechletRequestEnvelope.getRequest();
        SystemState systemState = getSystemState(speechletRequestEnvelope.getContext());
        this.currentSong = intentRequest.getToken();
        Date date = intentRequest.getTimestamp();

        return getPlayAudioResponse(systemState, date);
    }

    @Override
    public SpeechletResponse onPlaybackStarted(SpeechletRequestEnvelope<PlaybackStartedRequest> speechletRequestEnvelope) {
        log.info("Inside onPlaybackStarted()");

        PlaybackStartedRequest intentRequest = speechletRequestEnvelope.getRequest();
        SystemState systemState = getSystemState(speechletRequestEnvelope.getContext());
        this.currentSong = intentRequest.getToken();
        Date date = intentRequest.getTimestamp();

        log.info("Now playing: {}, offset from beginning of stream: {}", this.currentSong, intentRequest.getOffsetInMilliseconds());

        return null;
    }

    @Override
    public SpeechletResponse onPlaybackStopped(SpeechletRequestEnvelope<PlaybackStoppedRequest> speechletRequestEnvelope) {
        log.info("Inside onPlaybackStopped()");

        PlaybackStoppedRequest intentRequest = speechletRequestEnvelope.getRequest();
        this.currentSong = intentRequest.getToken();

        log.info("Stopped: {}, offset from beginning of stream: {}", this.currentSong, intentRequest.getOffsetInMilliseconds());

        return null;
    }

    @Override
    public void onSessionStarted(SpeechletRequestEnvelope<SessionStartedRequest> speechletRequestEnvelope) {
        log.info("onSessionStarted requestId={}, sessionId={}",
                speechletRequestEnvelope.getRequest().getRequestId(),
                speechletRequestEnvelope.getSession().getSessionId());
        // any initialization logic goes here
    }

    @Override
    public void onSessionEnded(SpeechletRequestEnvelope<SessionEndedRequest> speechletRequestEnvelope) {
        log.info("onSessionEnded requestId={}, sessionId={}",
                speechletRequestEnvelope.getRequest().getRequestId(),
                speechletRequestEnvelope.getSession().getSessionId());
        // any cleanup logic goes here
    }

    @Override
    public SpeechletResponse onLaunch(SpeechletRequestEnvelope<LaunchRequest> speechletRequestEnvelope) {
        log.info("onLaunch requestId={}, sessionId={}", speechletRequestEnvelope.getRequest().getRequestId(),
                speechletRequestEnvelope.getSession().getSessionId());

        Date date = speechletRequestEnvelope.getRequest().getTimestamp();

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(date.getTime());
        log.info("LaunchRequest timestamp: {}:{}:{} ({} ms)",
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                calendar.get(Calendar.SECOND),
                calendar.getTimeInMillis());

        SystemState systemState = getSystemState(speechletRequestEnvelope.getContext());

        if(systemState.getUser().getPermissions() == null){
            // Ask the user for permissions in the Alexa app
            log.info("The user hasn't authorized the skill. Sending a permissions card.");
            return createAskPermissionsResponse();
        }

        SpeechletResponse returnResponse = getPlayAudioResponse(systemState, date);
        log.info("Exiting onLaunch()");
        //return TunesUtil.getSilence();
        return returnResponse;
    }

    @Override
    public SpeechletResponse onIntent(SpeechletRequestEnvelope<IntentRequest> speechletRequestEnvelope) {
        IntentRequest request = speechletRequestEnvelope.getRequest();
        log.info("onIntent requestId={}, sessionId={}", request.getRequestId(),
                speechletRequestEnvelope.getSession().getSessionId());

        Intent intent = request.getIntent();
        String intentName = null;
        if(intent != null){
            intentName = intent.getName();
            log.info("Intent name: {}", intentName);
        }

        Date date = request.getTimestamp();

        SpeechletResponse returnResponse;
        if("AMAZON.HelpIntent".equals(intentName)){
            returnResponse = createHelpResponse();
        } else if("AMAZON.StopIntent".equals(intentName) || "AMAZON.PauseIntent".equals(intentName) || "AMAZON.CancelIntent".equals(intentName)){
            log.info("Returning Stop Directive");
            returnResponse = createStopResponse();
        } else if("AMAZON.ResumeIntent".equals(intentName)){
            returnResponse = getPlayAudioResponse(getSystemState(speechletRequestEnvelope.getContext()), date);
        }else {
            returnResponse = getPlayAudioResponse(getSystemState(speechletRequestEnvelope.getContext()), date);
        }

        log.info("Exiting onIntent() - {}", intentName);
        return returnResponse;
    }

    private SpeechletResponse createStopResponse(){
        SpeechletResponse stopResponse = new SpeechletResponse();
        List<Directive> stopDirective = new ArrayList<>();
        ClearQueueDirective clearQueueDirective = new ClearQueueDirective();
        clearQueueDirective.setClearBehavior(ClearBehavior.CLEAR_ALL);
        stopDirective.add(clearQueueDirective);
        //stopDirective.add(new StopDirective());
        stopResponse.setDirectives(stopDirective);
        stopResponse.setNullableShouldEndSession(true);
        return stopResponse;
    }

    private SpeechletResponse getPlayAudioResponse(SystemState systemState, Date requestDate){

        // Get the timestamp of the request in milliseconds
        long requestTimestamp = requestDate.getTime();

        // Debug values
        String debugTime = System.getenv("DEBUG_TIME");
        if(debugTime != null && !debugTime.isEmpty()){
            requestTimestamp = Long.parseLong(debugTime);
        }

        // The default tunes builder which uses the request's UTC date
        TunesUtil.TunesBuilder tunesBuilder = new TunesUtil.TunesBuilder(requestTimestamp);

        // If we have the offset cached then use it. Also check the db for offset
        if(this.deviceTimeOffset != null){
            log.info("Using cached local device time offset.");
            long localOffset = Long.valueOf(this.deviceTimeOffset);
            return tunesBuilder.date(requestTimestamp, localOffset).build().getTune();
        } else{
            log.info("Checking database for the device's offset.");

            // Check the db to see if the device ID is associated with an offset
            DbOffsetClient dbClient = new DbOffsetClient();
            Item item = dbClient.retrieveItem(systemState.getDevice().getDeviceId());

            if(item != null){
                log.info("Device ID found in database");
                long localOffset = item.getLong(DbOffsetClient.OFFSET_KEY);
                return tunesBuilder.date(requestTimestamp, localOffset).build().getTune();
            }else{
                log.info("Device ID not found in database.");
            }
        }

        // We know that the offset isn't cached at this point so we need to figure out what it is
        long localOffset = getDeviceTimeOffset(systemState, requestTimestamp);
        this.deviceTimeOffset = String.valueOf(localOffset);

        // Add the association between device ID and local offset since we now have it
        log.info("Associating device ID with its local offset.");
        DbOffsetClient dbClient = new DbOffsetClient();
        dbClient.associateOffset(systemState.getDevice().getDeviceId(), localOffset);

        // The device's local time is the sum of the time zone offset and the UTC time
        return tunesBuilder.date(requestTimestamp, localOffset).build().getTune();
    }

    /**
     * Method which will call various services to determine the local time offset of the device's location.
     * @param systemState Used to retrieve the necessary data to call Amazon's webservice to get device's address
     * @param requestDate The UTC timestamp in milliseconds of the request
     * @return Returns the local offset of the device's location used to calculate the device's local time.
     */
    private long getDeviceTimeOffset(SystemState systemState, long requestDate) {
        long localOffset = 0;

        // Data needed to call Amazon address webservice
        String deviceId = systemState.getDevice().getDeviceId();
        String accessToken = systemState.getApiAccessToken();
        String apiEndpoint = systemState.getApiEndpoint();

        // Call Amazon's address API for the device
        String addressString = getDeviceAddressAsString(deviceId, accessToken, apiEndpoint);

        if(addressString != null) {
            GoogleMapsClient googleClient = new GoogleMapsClient();
            localOffset = googleClient.getDeviceLocalOffset(addressString, requestDate);
        }

        return localOffset;
    }

    /**
     * Method to call the Alexa API to get the location of the Alexa device.
     * All parameters are from the request's SystemState
     * @param deviceId The device ID
     * @param accessToken The API access token
     * @param apiEndpoint The API endpoint value
     * @return A concatenated String of the device's address to use for the Google Maps API.
     * Or null if there was a permissions issue or an unexpected error.
     */
    private String getDeviceAddressAsString(String deviceId, String accessToken, String apiEndpoint){
        String addressString = null;

        AlexaDeviceAddressClient addressClient
                = new AlexaDeviceAddressClient(deviceId, accessToken, apiEndpoint);

        DeviceAddress deviceAddress = addressClient.getAddress();

        if(deviceAddress != null){
            // Note that we are URL encoding a comma here
            addressString = deviceAddress.countryCode + "%2C" + deviceAddress.postalCode;
        }

        return addressString;
    }

    private SystemState getSystemState(Context context) {
        return context.getState(SystemInterface.class, SystemState.class);
    }

    /**
     * Creates a SpeechletResponse with a card asking the user to set permissions, and an output speech.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private SpeechletResponse createAskPermissionsResponse(){
        String speechText = "Please configure permissions in the Alexa app so A.C. Tunes can play music based on your local timezone.";

        PlainTextOutputSpeech speech =  new PlainTextOutputSpeech();
        speech.setText(speechText);

        Set<String> permissions = new HashSet<>();
        permissions.add("read::alexa:device:all:address:country_and_postal_code");

        AskForPermissionsConsentCard card = new AskForPermissionsConsentCard();
        card.setPermissions(permissions);
        card.setTitle("A.C. Tunes Configure Permissions");

        return SpeechletResponse.newTellResponse(speech, card);
    }

    /**
     * Helper method for retrieving an OutputSpeech object when given a string of TTS.
     * @param speechText the text that should be spoken out to the user.
     * @return an instance of SpeechOutput.
     */
    private PlainTextOutputSpeech getPlainTextOutputSpeech(String speechText) {
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(speechText);

        return speech;
    }

    /**
     * Creates a {@code SpeechletResponse} for the help intent.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private SpeechletResponse createHelpResponse() {
        String speechText = "Unable to get song title";
        if(this.currentSong != null){
            speechText = this.currentSong.split("\\|",2)[0];
        }

        // Create the plain text output
        PlainTextOutputSpeech speech = getPlainTextOutputSpeech(speechText);

        return SpeechletResponse.newTellResponse(speech);
    }
}
