package buglocator.indexing.data;

import com.google.gson.annotations.SerializedName;
import org.joda.time.DateTime;

import java.util.List;

/**
 * Created by juan on 4/19/16.
 */
public class BugReport {
    private String key;
    private String title;
    private String description;
    private DateTime creationDate;
    private DateTime resolutionDate;
    private List<String> fixedFiles;

    public String getKey() {
        return key;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public DateTime getCreationDate() {
        return creationDate;
    }

    public DateTime getResolutionDate() {
        return resolutionDate;
    }

    public List<String> getFixedFiles() {
        return fixedFiles;
    }
}
