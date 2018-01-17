package animal.crossing.tunes;

import com.amazon.speech.speechlet.lambda.SpeechletRequestStreamHandler;

import java.util.HashSet;
import java.util.Set;

public class ACTunesSpeechletRequestStreamHandler extends SpeechletRequestStreamHandler {
    private static final Set<String> supportedApplicationIds;

    static {
        supportedApplicationIds = new HashSet<String>();
        supportedApplicationIds.add("amzn1.ask.skill.8a578f3f-52cc-45a6-b36e-56d2dc3496a3");
    }

    public ACTunesSpeechletRequestStreamHandler() {
        super(new ACTunesPlayer(), supportedApplicationIds);
    }
}
