package animal.crossing.tunes;

import animal.crossing.tunes.data.DeviceAddress;
import animal.crossing.tunes.service.AlexaDeviceAddressClient;
import animal.crossing.tunes.service.GoogleMapsClient;
import com.amazon.speech.json.SpeechletRequestEnvelope;
import com.amazon.speech.slu.Intent;
import com.amazon.speech.speechlet.*;
import com.amazon.speech.speechlet.interfaces.audioplayer.AudioPlayer;
import com.amazon.speech.speechlet.interfaces.audioplayer.directive.StopDirective;
import com.amazon.speech.speechlet.interfaces.audioplayer.request.*;
import com.amazon.speech.speechlet.interfaces.system.SystemInterface;
import com.amazon.speech.speechlet.interfaces.system.SystemState;
import com.amazon.speech.ui.*;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

public class ACTunesPlayer implements AudioPlayer, SpeechletV2 {
    private static final Logger log = LoggerFactory.getLogger(ACTunesPlayer.class);

    private String currentSong;
    private String deviceTimeOffset;

    private boolean isLaunch = false;

    @Override
    public SpeechletResponse onPlaybackFailed(SpeechletRequestEnvelope<PlaybackFailedRequest> speechletRequestEnvelope) {
        log.error("Inside onPlaybackFailed() | {} - {} ",
                speechletRequestEnvelope.getRequest().getError().getMessage(),
                speechletRequestEnvelope.getRequest().getError().getType().toString());

        PlaybackFailedRequest intentRequest = speechletRequestEnvelope.getRequest();

        return null;
    }

    @Override
    public SpeechletResponse onPlaybackFinished(SpeechletRequestEnvelope<PlaybackFinishedRequest> speechletRequestEnvelope) {
        log.info("Inside onPlaybackFinished()");

        PlaybackFinishedRequest intentRequest = speechletRequestEnvelope.getRequest();

        return null;
    }

    @Override
    public SpeechletResponse onPlaybackNearlyFinished(SpeechletRequestEnvelope<PlaybackNearlyFinishedRequest> speechletRequestEnvelope) {
        log.info("Inside onPlaybackNearlyFinished()");
        PlaybackNearlyFinishedRequest intentRequest = speechletRequestEnvelope.getRequest();

        SystemState systemState = getSystemState(speechletRequestEnvelope.getContext());

        TunesUtil.token = intentRequest.getToken();

        Date date = intentRequest.getTimestamp();
        return getPlayAudioResponse(systemState, date, true);
    }

    @Override
    public SpeechletResponse onPlaybackStarted(SpeechletRequestEnvelope<PlaybackStartedRequest> speechletRequestEnvelope) {
        log.info("Inside onPlaybackStarted()");

        PlaybackStartedRequest intentRequest = speechletRequestEnvelope.getRequest();

        SystemState systemState = getSystemState(speechletRequestEnvelope.getContext());

        TunesUtil.token = intentRequest.getToken();

        Date date = intentRequest.getTimestamp();
        return getPlayAudioResponse(systemState, date, false);
    }

    @Override
    public SpeechletResponse onPlaybackStopped(SpeechletRequestEnvelope<PlaybackStoppedRequest> speechletRequestEnvelope) {
        log.info("Inside onPlaybackStopped()");

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
            return getAskPermissionsResponse();
        }

        this.isLaunch = true;
        return getPlayAudioResponse(systemState, date, false);
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

        if("AMAZON.HelpIntent".equals(intentName)){
            return getHelpResponse();
        } else if("AMAZON.StopIntent".equals(intentName) || "AMAZON.PauseIntent".equals(intentName) || "AMAZON.CancelIntent".equals(intentName)){
            return getStopResponse();
        } else if("AMAZON.ResumeIntent".equals(intentName)){
            return getPlayAudioResponse(getSystemState(speechletRequestEnvelope.getContext()), date, false);
        }else {
            return getPlayAudioResponse(getSystemState(speechletRequestEnvelope.getContext()), date, false);
        }
    }

    private SpeechletResponse getStopResponse(){
        SpeechletResponse stopResponse = new SpeechletResponse();
        List<Directive> stopDirective = new ArrayList<>();
        stopDirective.add(new StopDirective());
        stopResponse.setDirectives(stopDirective);
        stopResponse.setNullableShouldEndSession(true);
        return stopResponse;
    }

    private SpeechletResponse getPlayAudioResponse(SystemState systemState, Date requestDate, boolean playFromBeginning){

        SpeechletResponse speechletResponse;
        if(this.deviceTimeOffset != null){
            log.info("using cached local device time offset.");
            long localOffset = Long.valueOf(this.deviceTimeOffset);
            speechletResponse = TunesUtil.getTune(new Date(requestDate.getTime() + localOffset), playFromBeginning);
        }else{
            try {
                long localOffset = getDeviceTimeOffset(systemState, requestDate.getTime());
                log.info("getPlayAudioResponse received date: {} ms, calculated local time offset as: {} ms", requestDate.getTime(), localOffset);
                speechletResponse = TunesUtil.getTune(new Date(requestDate.getTime() + localOffset), playFromBeginning);
            } catch (Exception e) {
                log.error("Error trying to determine device's local time. Defaulting to UTC time.", e);
                speechletResponse = TunesUtil.getTune(requestDate, false);
                speechletResponse.setOutputSpeech(getPlainTextOutputSpeech("Error getting device local time. Using default time."));
            }
        }
        this.isLaunch = false;
        return speechletResponse;
    }

    private long getDeviceTimeOffset(SystemState systemState, long requestDate) throws IOException, JsonSyntaxException {
        long local = 0;

        String deviceId = systemState.getDevice().getDeviceId();
        String accessToken = systemState.getApiAccessToken();
        String apiEndpoint = systemState.getApiEndpoint();

        String addressString = getDeviceLocation(deviceId, accessToken, apiEndpoint);
        if(addressString != null) {
            GoogleMapsClient googleClient = new GoogleMapsClient();
            try {
                local = googleClient.getDeviceTime(addressString, requestDate);
                this.deviceTimeOffset = String.valueOf(local);
            } catch (IOException e) {
                log.error("SOmethings a BROKE!!!1", e);
                throw e;
            } catch (JsonSyntaxException e) {
                log.error("Error when deserialzing JSON string to POJO", e);
                throw e;
            }
        }
        return local;
    }

    /**
     * Method to call the Alexa API to get the location of the Alexa device.
     * All parameters are from the request's SystemState
     * @param accessToken The API access token
     * @param deviceId The device ID
     * @param apiEndpoint The API endpoint value
     * @return A concatenated String of the device's address to use for the Google Maps API.
     * Or null if there was a permissions issue or an unexpected error.
     */
    private String getDeviceLocation(String deviceId, String accessToken, String apiEndpoint){
        AlexaDeviceAddressClient addressClient
                = new AlexaDeviceAddressClient(deviceId, accessToken, apiEndpoint);

        DeviceAddress deviceAddress = null;
        try {
            deviceAddress = addressClient.getAddress();
        } catch (IOException e) {
            log.info("Exception when trying to get device address.", e);
        }

        String addressString = null;
        if(deviceAddress != null){
            addressString = deviceAddress.countryCode + "," + deviceAddress.postalCode;;
        }

        return addressString;
    }

    private SystemState getSystemState(Context context) {
        SystemState systemState = context.getState(SystemInterface.class, SystemState.class);
        return systemState;
    }

    /**
     * Creates a SpeechletResponse with a card asking the user to set permissions, and an output speech.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private SpeechletResponse getAskPermissionsResponse(){
        String speechText = "Please configure permissions in the Alexa app so A.C. Tunes can play music based on your local timezone.";
        String cardText = "Please configure your permissions so A.C. Tunes can play you the correct tune based on your local timezone. " +
                "If you decide not to configure these settings, A.C. Tunes will use the Coordinated Universal Time (UTC) " +
                "to determine what tune to play.";

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
    private SpeechletResponse getHelpResponse() {
        String speechText = "Unable to get song title";
        if(this.currentSong != null){
            speechText = this.currentSong.split("|",2)[0];
        }

        // Create the plain text output
        PlainTextOutputSpeech speech = getPlainTextOutputSpeech(speechText);

        return SpeechletResponse.newTellResponse(speech);
    }

    private SpeechletResponse getErrorResponse(String errorMsg){
        // Create the plain text output
        PlainTextOutputSpeech speech = getPlainTextOutputSpeech(errorMsg);

        return SpeechletResponse.newTellResponse(speech);
    }


    /**
     * Helper method for retrieving an Ask response with a simple card and reprompt included.
     * @param cardTitle Title of the card that you want displayed.
     * @param speechText speech text that will be spoken to the user.
     * @return the resulting card and speech text.
     */
    private SpeechletResponse getAskResponse(String cardTitle, String speechText) {
        SimpleCard card = TunesUtil.getSimpleCard(cardTitle, speechText);
        PlainTextOutputSpeech speech = getPlainTextOutputSpeech(speechText);
        Reprompt reprompt = getReprompt(speech);

        return SpeechletResponse.newTellResponse(speech, card);
    }

    /**
     * Helper method that returns a reprompt object. This is used in Ask responses where you want
     * the user to be able to respond to your speech.
     * @param outputSpeech The OutputSpeech object that will be said once and repeated if necessary.
     * @return Reprompt instance.
     */
    private Reprompt getReprompt(OutputSpeech outputSpeech) {
        Reprompt reprompt = new Reprompt();
        reprompt.setOutputSpeech(outputSpeech);

        return reprompt;
    }
}
