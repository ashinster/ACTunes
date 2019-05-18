package animal.crossing.tunes;

import animal.crossing.tunes.data.ResponseClass;
import com.amazon.speech.speechlet.SpeechletException;
import com.amazon.speech.speechlet.SpeechletRequestHandler;
import com.amazon.speech.speechlet.SpeechletRequestHandlerException;
import com.amazon.speech.speechlet.lambda.LambdaSpeechletRequestHandler;
import com.amazon.speech.speechlet.lambda.SpeechletRequestStreamHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

public class ACTunesSpeechletRequestStreamHandler extends SpeechletRequestStreamHandler{
    private static final Logger log = LoggerFactory.getLogger(ACTunesSpeechletRequestStreamHandler.class);

    private static final Set<String> supportedApplicationIds;

    // A "static initialisation block". It runs when the class is first loaded; only once.
    static {
        supportedApplicationIds = new HashSet<>();
        supportedApplicationIds.add(System.getenv("ACTUNES_APPLICATION_ID"));
    }

    public ACTunesSpeechletRequestStreamHandler(){
        super(new ACTunesPlayer(), supportedApplicationIds);
    }
}
