package buglocator.indexing.utils;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.joda.time.DateTime;

import java.io.IOException;

/**
 * Deserializes JodaTime objects from json dates.
 */
public class DateTimeJsonAdapter extends TypeAdapter<DateTime> {
    @Override
    public void write(JsonWriter jsonWriter, DateTime dateTime) throws IOException {
        // Not needed
    }

    @Override
    public DateTime read(JsonReader jsonReader) throws IOException {
        // The dates from the author's dataset are poorly formatted, fix that adding this 'T'
        String dateString = jsonReader.nextString().replace(' ', 'T');
        return new DateTime(dateString);
    }
}
