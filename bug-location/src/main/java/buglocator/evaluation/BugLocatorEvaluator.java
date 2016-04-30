package buglocator.evaluation;

import buglocator.indexing.data.BugReport;
import buglocator.indexing.utils.DateTimeJsonAdapter;
import buglocator.retrieval.BugLocatorRetriever;
import buglocator.retrieval.BugLocatorRetriever.UseField;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.io.FileUtils;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.FSDirectory;
import org.joda.time.DateTime;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Retrieval evaluator for the BugLocator approach.
 */
public class BugLocatorEvaluator extends BaseRetrievalEvaluator {
    private final UseField useField;
    private final float alpha;
    private BugLocatorRetriever retriever;

    public BugLocatorEvaluator(
            String systemName, UseField useField, Path indexPath, Path dataPath, float alpha) {
        super(systemName, indexPath, dataPath);
        this.useField = useField;
        this.alpha = alpha;
    }

    @Override
    protected void setup() throws IOException {
        FSDirectory bugReportsIndexDirectory =
                FSDirectory.open(indexPath.resolve(Paths.get("bug-reports", systemName)));

        IndexSearcher bugReportSearcher =
                new IndexSearcher(DirectoryReader.open(bugReportsIndexDirectory));

        String stats =
                FileUtils.readFileToString(indexPath.resolve(
                        Paths.get("source-code", systemName, "stats.txt")).toFile());
        Integer[] collectionExtrema =
                Arrays.stream(stats.split("\n")).map(Integer::parseInt).toArray(Integer[]::new);

        retriever = new BugLocatorRetriever(useField,
                sourceSearcher,
                bugReportSearcher,
                alpha,
                collectionExtrema[0],
                collectionExtrema[1]);
    }

    @Override
    protected ScoreDoc[] search(BugReport bugReport) throws IOException {
        return retriever.locate(bugReport);
    }
}
