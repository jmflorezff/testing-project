package buglocator.evaluation;

import buglocator.indexing.data.BugReport;
import buglocator.indexing.utils.DateTimeJsonAdapter;
import buglocator.retrieval.BugLocatorRetriever;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.io.FileUtils;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.FSDirectory;
import org.joda.time.DateTime;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by juan on 4/25/16.
 */
public class BugLocatorEvaluator {
    //    private static final List<String> systems = Arrays.asList("bookkeeper", "derby", "lucene",
//            "mahout", "openjpa", "pig", "solr", "tika", "zookeeper");
    private static final String[] systems = {"swt", "aspectj", "eclipse"};

    public static void main(String[] args) throws IOException {
        evaluate(BugLocatorRetriever.UseField.TITLE_AND_DESCRIPTION,
                Paths.get("..", "index"),
                Paths.get("..", "data"),
                0.2F);
    }

    public static EvaluationResult[] evaluate(BugLocatorRetriever.UseField useField,
                                              Path indexPath,
                                              Path dataPath,
                                              float alpha)
            throws IOException {

        // Create a JSON deserializer
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
        gsonBuilder.registerTypeAdapter(DateTime.class, new DateTimeJsonAdapter());
        Gson gson = gsonBuilder.create();

        EvaluationResult[] results = new EvaluationResult[systems.length];

        for (String system : systems) {
            Path sourceIndexPath = Paths.get(indexPath.toString(), "source-code", system);
            FSDirectory sourceIndexDirectory =
                    FSDirectory.open(sourceIndexPath);
            FSDirectory bugReportsIndexDirectory =
                    FSDirectory.open(Paths.get(indexPath.toString(), "bug-reports", system));

            IndexSearcher sourceSearcher =
                    new IndexSearcher(DirectoryReader.open(sourceIndexDirectory));

            IndexSearcher bugReportSearcher =
                    new IndexSearcher(DirectoryReader.open(bugReportsIndexDirectory));

            String stats =
                    FileUtils.readFileToString(
                            Paths.get(sourceIndexPath.toString(), "stats.txt").toFile());
            Object[] collectionExtrema =
                    Arrays.stream(stats.split("\n")).map(s -> Integer.parseInt(s)).toArray();

            BugLocatorRetriever retriever = new BugLocatorRetriever(useField,
                    sourceSearcher,
                    bugReportSearcher,
                    alpha,
                    (Integer) collectionExtrema[0],
                    (Integer) collectionExtrema[1]);

            int top1s = 0;
            int queries = 0;
            int totalQueries = bugReportSearcher.getIndexReader().maxDoc();

            for (String jsonLine : FileUtils.readLines(Paths.get(dataPath.toString(),
                    "processed-bug-reports", system + ".json").toFile())) {
                queries++;
                System.out.println(
                        String.format("Processing query %d of %d", queries, totalQueries));
                BugReport bugReport = gson.fromJson(jsonLine, BugReport.class);
                ScoreDoc[] scoredFiles = retriever.locate(bugReport);
                Set<Integer> goldSet = new HashSet<>(bugReport.getFixedFiles()
                        .stream()
                        .map(f -> {
                            try {
                                return retriever.getSourceFileID(f);
                            } catch (IOException e) {
                                System.out.println("Index read error at gold set collection");
                            }
                            return null;
                        }).collect(Collectors.toList()));

                top1s += goldSet.contains(scoredFiles[0].doc) ? 1 : 0;
            }

            float top1Precision = (float) top1s / queries;
        }

        return null;
    }
}
