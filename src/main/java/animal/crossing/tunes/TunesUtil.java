package animal.crossing.tunes;

import com.amazon.speech.speechlet.Directive;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazon.speech.speechlet.interfaces.audioplayer.AudioItem;
import com.amazon.speech.speechlet.interfaces.audioplayer.PlayBehavior;
import com.amazon.speech.speechlet.interfaces.audioplayer.Stream;
import com.amazon.speech.speechlet.interfaces.audioplayer.directive.PlayDirective;
import com.amazon.speech.ui.PlainTextOutputSpeech;
import com.amazon.speech.ui.SimpleCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.sql.Time;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class TunesUtil {
    private static final Logger log = LoggerFactory.getLogger(TunesUtil.class);

    public static String token;

    /**
     * Constructs the {@code SpeechletResponse} using the provided timestamp to get the appropriate tune for the user's time.
     * @param timestamp the timestamp from the request
     * @param playFromBeginning force the song to play from the beginning
     * @return the constructed {@code SpeechletResponse}
     */
    public static SpeechletResponse getTune(Date timestamp, boolean playFromBeginning) {

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp.getTime());

        int dayHour = calendar.get(Calendar.HOUR_OF_DAY);
        int dayMinutes = calendar.get(Calendar.MINUTE);
        int daySeconds = calendar.get(Calendar.SECOND);

        log.info("Request time (hh:mm:ss): {}:{}:{} ({} ms)", dayHour, dayMinutes, daySeconds, timestamp.getTime());

        // Create the audio item
        AudioItem audioItem = new AudioItem();

        PlayDirective playDirective = new PlayDirective();

        String commonFileName = "+(Extended)+-+Animal+Crossing+-+New+Leaf+Music";
        String fileName = dayHour + commonFileName;

        Stream audioStream = new Stream();
        audioStream.setUrl("https://s3.amazonaws.com/actunes/" + fileName + ".m4a");

        long audioOffset = 0;
        if(!playFromBeginning){
            audioOffset = getOffset(dayMinutes, daySeconds);
        }
        audioStream.setOffsetInMilliseconds(audioOffset);

        log.info("Will play next: {}.m4a", fileName);

        if(token != null){
            playDirective.setPlayBehavior(PlayBehavior.REPLACE_ALL);
        }else{
            playDirective.setPlayBehavior(PlayBehavior.REPLACE_ALL);
        }

        fileName = getReadableTime(dayHour) + commonFileName;
        try {
            token = URLDecoder.decode(fileName, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        audioStream.setToken(token + "|" + UUID.randomUUID().toString());

        audioItem.setStream(audioStream);

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

    private static String getReadableTime(int dayHour) {
        String readableTime = String.valueOf(dayHour) + ":00";
        try {
            SimpleDateFormat _24HourSDF = new SimpleDateFormat("HH:mm");
            SimpleDateFormat _12HourSDF = new SimpleDateFormat("hh:mm a");
            Date _24HourDt = _24HourSDF.parse(readableTime);
            readableTime = _12HourSDF.format(_24HourDt);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return readableTime;
    }

    private static long getOffset(int dayMinutes, int daySeconds){
        long calculatedOffset = 0;

        long msDayMinutes = TimeUnit.MINUTES.toMillis(dayMinutes);
        long msDaySeconds = TimeUnit.SECONDS.toMillis(daySeconds);

        long minutesMilliseconds = msDayMinutes + msDaySeconds;

        if(msDayMinutes > TimeUnit.MINUTES.toMillis(30)){
            calculatedOffset = TimeUnit.MINUTES.toMillis(30)
                    - (TimeUnit.MINUTES.toMillis(60) - minutesMilliseconds);

            log.info("Offset: {} minutes ({}ms)",  TimeUnit.MILLISECONDS.toMinutes(calculatedOffset), calculatedOffset);
        }

        return calculatedOffset;
    }

    /**
     * Helper method that creates a card object.
     * @param title title of the card
     * @param content body of the card
     * @return SimpleCard the display card to be sent along with the voice response.
     */
    public static SimpleCard getSimpleCard(String title, String content) {
        SimpleCard card = new SimpleCard();
        card.setTitle(title);
        card.setContent(content);

        return card;
    }
}
