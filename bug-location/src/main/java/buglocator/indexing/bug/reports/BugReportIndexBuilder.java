package buglocator.indexing.bug.reports;

import buglocator.indexing.BaseIndexBuilder;
import buglocator.indexing.data.BugReport;
import buglocator.indexing.utils.DateTimeJsonAdapter;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import org.joda.time.DateTime;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Builds a Lucene index for a corpus of bug reports. Keeps no transient state, can be safely
 * reused.
 */
public class BugReportIndexBuilder extends BaseIndexBuilder<BugReport> {
    public BugReportIndexBuilder() {
        super(BugReport.class);
    }

    @Override
    protected Document createDocument(BugReport bugReport) {
        Document document = new Document();

        document.add(new StringField("key", bugReport.getKey(), Field.Store.YES));
        document.add(new Field("fullText",
                bugReport.getTitle() + " " + bugReport.getDescription(), termVectorsFieldType));
        document.add(new LongField("creationDate",
                bugReport.getCreationDate().getMillis(), Field.Store.NO));
        document.add(new LongField("resolutionDate",
                bugReport.getResolutionDate().getMillis(), Field.Store.NO));
        document.add(new StringField("fixedFiles",
                String.join(";", bugReport.getFixedFiles()), Field.Store.YES));

        return document;
    }
}
