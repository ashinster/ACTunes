package animal.crossing.tunes.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public abstract class GoogleData {
    /**
     * Overidden toString() method to return the pretty printed JSON String of this POJO
     * @return
     */
    @Override
    public String toString(){
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String prettyJson = gson.toJson(this);
        return prettyJson;
    }
}
