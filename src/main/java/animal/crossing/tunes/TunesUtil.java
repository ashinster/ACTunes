package animal.crossing.tunes;

import com.amazon.speech.speechlet.Directive;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazon.speech.speechlet.interfaces.audioplayer.AudioItem;
import com.amazon.speech.speechlet.interfaces.audioplayer.PlayBehavior;
import com.amazon.speech.speechlet.interfaces.audioplayer.Stream;
import com.amazon.speech.speechlet.interfaces.audioplayer.directive.PlayDirective;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class TunesUtil {
    private static final Logger log = LoggerFactory.getLogger(TunesUtil.class);

    private Date date;

    public static class TunesBuilder{
        private Date date;

        public TunesBuilder(long requestTimestamp){
            this.date = new Date(requestTimestamp);
        }

        public TunesBuilder date(long requestTimestamp, long localOffset){
            this.date = new Date(requestTimestamp + localOffset);
            return this;
        }

        public TunesUtil build(){
            return new TunesUtil(this);
        }
    }

    private TunesUtil(TunesBuilder tunesBuilder){
        this.date = tunesBuilder.date;
    }


    public SpeechletResponse getTune(){

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(this.date.getTime());

        int dayHour = calendar.get(Calendar.HOUR_OF_DAY);
        int dayMinutes = calendar.get(Calendar.MINUTE);
        int daySeconds = calendar.get(Calendar.SECOND);

        log.info("Request time (hh:mm:ss): {}:{}:{} ({} ms)", dayHour, dayMinutes, daySeconds, this.date.getTime());

        String tuneNameAppend = "+(Extended)+-+Animal+Crossing+-+New+Leaf+Music";
        String fileName = dayHour + tuneNameAppend;
        String audioUrl = "https://s3.amazonaws.com/actunes/" + fileName + ".m4a";
        log.info("Using URL: {}", audioUrl);

        Stream audioStream = new Stream();
        audioStream.setUrl(audioUrl);
        audioStream.setOffsetInMilliseconds(getOffset(dayMinutes, daySeconds));

        String token = get12HourTime(dayHour) + tuneNameAppend;
        try {
            // Decode the text so Alexa can read the name when asked for the tune name.
            token = URLDecoder.decode(token, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.error("error trying to decode string to make token. Using a token that isn't URL decoded.", e);
        }
        audioStream.setToken(token + "|" + UUID.randomUUID().toString());

        // Create the audio item
        AudioItem audioItem = new AudioItem();
        audioItem.setStream(audioStream);

        PlayDirective playDirective = new PlayDirective();
        playDirective.setPlayBehavior(PlayBehavior.REPLACE_ALL);
        playDirective.setAudioItem(audioItem);

        // Make the PlayDirective using the audio item
        List<Directive> directives = new ArrayList<>();
        directives.add(playDirective);

        SpeechletResponse speechletResponse = new SpeechletResponse();
        // Set the response directive with the PlayDirective we just constructed
        speechletResponse.setDirectives(directives);

        speechletResponse.setNullableShouldEndSession(true);

        return speechletResponse;
    }

    private String get12HourTime(int dayHour) {
        String readableTime = String.valueOf(dayHour).concat(":00");

        try {
            SimpleDateFormat _24HourSDF = new SimpleDateFormat("HH:mm");
            SimpleDateFormat _12HourSDF = new SimpleDateFormat("hh:mm a");
            Date _24HourDt = _24HourSDF.parse(readableTime);
            readableTime = _12HourSDF.format(_24HourDt);
        } catch (Exception e) {
            log.error("Error trying to create readable token name.", e);
        }
        return readableTime;
    }

    private long getOffset(int dayMinutes, int daySeconds){
        long calculatedOffset = 0;

        long msDayMinutes = TimeUnit.MINUTES.toMillis(dayMinutes);
        long msDaySeconds = TimeUnit.SECONDS.toMillis(daySeconds);

        long minutesMilliseconds = msDayMinutes + msDaySeconds;

        if(minutesMilliseconds > TimeUnit.MINUTES.toMillis(30)){
            calculatedOffset = TimeUnit.MINUTES.toMillis(30)
                    - (TimeUnit.MINUTES.toMillis(60) - minutesMilliseconds);


            String hms = String.format("%02d:%02d",
                    TimeUnit.MILLISECONDS.toMinutes(calculatedOffset) % TimeUnit.HOURS.toMinutes(1),
                    TimeUnit.MILLISECONDS.toSeconds(calculatedOffset) % TimeUnit.MINUTES.toSeconds(1));

            log.info("Offset (mm:ss): {} ({}ms)", hms, calculatedOffset);
        }

        return calculatedOffset;
    }
}
