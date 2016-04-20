package buglocator.indexing;

import buglocator.indexing.data.BugReport;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;

/**
 * Builds a Lucene index for a corpus of bug reports. Keeps no transient state, can be safely
 * reused.
 */
public class BugReportIndexBuilder {
    public void buildIndex(String sourceFilePath, String indexDestinationPath) throws IOException {
        // Create an index writer
        IndexWriterConfig writerConfig = new IndexWriterConfig(new WhitespaceAnalyzer());
        writerConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        IndexWriter indexWriter =
                new IndexWriter(FSDirectory.open(Paths.get(indexDestinationPath)), writerConfig);

        // Create a JSON deserializer
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
//        gsonBuilder.registerTypeAdapter()
        Gson gson = gsonBuilder.create();

        // Iterate through the JSON lines file and extract all the documents
        try (BufferedReader br = new BufferedReader(new FileReader(sourceFilePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                BugReport bugReport = gson.fromJson(line, BugReport.class);
                indexWriter.addDocument(createDocument(bugReport));
            }
        }

        indexWriter.close();
    }

    private Document createDocument(BugReport bugReport) {
        Document document = new Document();
        document.add(new StringField("key", bugReport.getKey(), Field.Store.YES));
        document.add(new TextField("title", bugReport.getTitle(), Field.Store.YES));
        document.add(new TextField("description", bugReport.getDescription(), Field.Store.YES));
        document.add(new LongField("creationDate",
                bugReport.getCreationDate().getMillis(), Field.Store.NO));
        document.add(new LongField("resolutionDate",
                bugReport.getResolutionDate().getMillis(), Field.Store.NO));
        document.add(new StringField("fixedFiles",
                String.join(";", bugReport.getFixedFiles()), Field.Store.YES));

        return document;
    }
}
