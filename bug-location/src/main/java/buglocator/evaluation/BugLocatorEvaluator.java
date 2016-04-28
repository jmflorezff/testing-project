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
 * Retrieval evaluator for the BugLocator approach.
 */
public class BugLocatorEvaluator {
    private static final String[] systems = {"aspectj-1.5.3", "bookkeeper-4.1.0", "derby-10.9.1.0",
            "eclipse-3.1", "lucene-4.0", "mahout-0.8", "openjpa-2.2.0", "pig-0.11.1", "solr-4.4.0",
            "swt-3.1", "tika-1.3", "zookeeper-3.4.5.json"};

    public EvaluationResult[] evaluate(BugLocatorRetriever.UseField useField,
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
        int systemIndex = 0;

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
            int top5s = 0;
            int top10s = 0;
            // Amount of actually performed queries. A query will not be performed if the
            // corresponding bug report doesn't have the required field specified in the UseField
            // parameter of this method.
            int actualQueries = 0;
            float reciprocalRankAccum = 0;
            float averagePrecisionAccum = 0;
            float precisionAccum = 0;
            float recallAccum = 0;
            int totalQueries = bugReportSearcher.getIndexReader().maxDoc();

            for (String jsonLine : FileUtils.readLines(Paths.get(dataPath.toString(),
                    "processed-bug-reports", system + ".json").toFile())) {
                System.out.println(
                        String.format("Processing query %d of %d", actualQueries + 1, totalQueries));
                BugReport bugReport = gson.fromJson(jsonLine, BugReport.class);

                ScoreDoc[] scoredFiles = retriever.locate(bugReport);
                // If the bug report doesn't have the required information it will return null
                if (scoredFiles == null) {
                    continue;
                }

                actualQueries++;
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

                int topRank = getTopRank(goldSet, scoredFiles);

                if (topRank <= 10) {
                    top10s++;
                    if (topRank <= 5) {
                        top5s++;
                        if (topRank == 1) {
                            top1s++;
                        }
                    }
                }

                reciprocalRankAccum += topRank > 0 ? 1F / topRank : 0;
                averagePrecisionAccum += calculateAveragePrecision(goldSet, scoredFiles);
                int relevantRetrieved = getRelevantRetrieved(goldSet, scoredFiles);
                precisionAccum += relevantRetrieved / ((float) scoredFiles.length);
                recallAccum += relevantRetrieved / ((float) goldSet.size());
            }

            if (actualQueries > 0) {
                float top1Precision = (float) top1s / actualQueries;
                float top5Precision = (float) top5s / actualQueries;
                float top10Precision = (float) top10s / actualQueries;
                float meanReciprocalRank = reciprocalRankAccum / actualQueries;
                float meanAveragePrecision = averagePrecisionAccum / actualQueries;
                float averagePrecision = precisionAccum / actualQueries;
                float averageRecall = recallAccum / actualQueries;

                results[systemIndex++] = new EvaluationResult(
                        system,
                        alpha,
                        actualQueries,
                        top1Precision,
                        top5Precision,
                        top10Precision,
                        meanReciprocalRank,
                        meanAveragePrecision,
                        averagePrecision,
                        averageRecall);
            } else {
                results[systemIndex++] = new EvaluationResult(system);
                System.out.println("No valid queries for system " + system);
            }
        }

        return results;
    }

    private int getRelevantRetrieved(Set<Integer> goldSet, ScoreDoc[] scoredFiles) {
        return (int) Arrays.stream(scoredFiles).filter(sd -> goldSet.contains(sd.doc)).count();
    }

    private float calculateAveragePrecision(Set<Integer> goldSet, ScoreDoc[] scoredFiles) {
        float totalRelevant = Math.min(goldSet.size(), scoredFiles.length);
        if (totalRelevant == 0) {
            return 0;
        }

        float currentRelevant = 0;

        float accumulator = 0;
        for (int i = 0; i < scoredFiles.length; i++) {
            // If the current document is relevant
            if (goldSet.contains(scoredFiles[i].doc)) {
                currentRelevant++;
                accumulator += currentRelevant / (i + 1);
            }

            if (currentRelevant == totalRelevant) {
                break;
            }
        }

        return accumulator / totalRelevant;
    }

    /**
     * Finds the first returned file belonging to the gold set.
     *
     * @param goldSet     A set of file IDs known to be the cause of this bug.
     * @param scoredFiles The ranked files returned by the approach.
     * @return The rank of the first file returned that is found in the gold set or {@code 0} if it
     * is not found.
     */
    private int getTopRank(Set<Integer> goldSet, ScoreDoc[] scoredFiles) {
        int topRank = 0;

        for (int i = 0; i < scoredFiles.length; i++) {
            if (goldSet.contains(scoredFiles[i].doc)) {
                topRank = i + 1;
                break;
            }
        }

        return topRank;
    }
}
