package buglocator.indexing;

import buglocator.indexing.utils.DateTimeJsonAdapter;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import org.joda.time.DateTime;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Base class for index builders.
 */
public abstract class BaseIndexBuilder<T> {
    private final Class<T> jsonElementClass;

    public BaseIndexBuilder(Class<T> jsonElementClass) {
        this.jsonElementClass=jsonElementClass;
    }

    public void buildIndex(Path sourceFilePath, Path indexPath) throws IOException {
        // Create an index writer
        IndexWriterConfig writerConfig = new IndexWriterConfig(new WhitespaceAnalyzer());
        writerConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        IndexWriter indexWriter =
                new IndexWriter(FSDirectory.open(indexPath), writerConfig);

        // Create a JSON deserializer
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
        gsonBuilder.registerTypeAdapter(DateTime.class, new DateTimeJsonAdapter());
        Gson gson = gsonBuilder.create();

        // Iterate through the JSON lines file and extract all the documents
        try (BufferedReader br = new BufferedReader(new FileReader(sourceFilePath.toFile()))) {
            String line;
            while ((line = br.readLine()) != null) {
                T item = gson.fromJson(line, jsonElementClass);
                indexWriter.addDocument(createDocument(item));
            }
        }

        indexWriter.close();
    }

    protected abstract Document createDocument(T item);
}