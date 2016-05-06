package buglocator.evaluation;

import buglocator.indexing.data.BugReport;
import buglocator.indexing.utils.DateTimeJsonAdapter;
import buglocator.retrieval.RetrieverBase;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.io.FileUtils;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.FSDirectory;
import org.joda.time.DateTime;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Base class for retrieval evaluators.
 */
public abstract class BaseRetrievalEvaluator {
    protected final Gson gson;
    protected final String systemName;
    protected final Path indexPath;
    protected IndexSearcher sourceSearcher;
    protected RetrieverBase retriever;
    private Map<String, Integer> fileIDCache = new HashMap<>();
    private Path dataPath;

    public BaseRetrievalEvaluator(String systemName, Path indexPath, Path dataPath) {
        // Create a JSON deserializer
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
        gsonBuilder.registerTypeAdapter(DateTime.class, new DateTimeJsonAdapter());
        gson = gsonBuilder.create();

        this.systemName = systemName;
        this.indexPath = indexPath;
        this.dataPath = dataPath;
    }

    /**
     * @return A system evaluation or {@code null} if it is not possible.
     * @throws IOException
     */
    public EvaluationResult evaluate() throws IOException {

        FSDirectory sourceIndexDirectory =
                FSDirectory.open(indexPath.resolve(Paths.get("source-code", systemName)));
        sourceSearcher =
                new IndexSearcher(DirectoryReader.open(sourceIndexDirectory));

        retriever = setupRetriever();

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

        System.out.println(
                String.format("Beginning %s evaluation of system %s\n", getLogTag(), systemName));

        List<String> lines = FileUtils.readLines(Paths.get(dataPath.toString(),
                "processed-bug-reports", systemName + ".json").toFile());
        int lineCount = lines.size();
        int notificationInterval = lineCount / 10;

        for (int i = 0; i < lines.size(); i++) {
            String jsonLine = lines.get(i);
            BugReport bugReport = gson.fromJson(jsonLine, BugReport.class);

            if (i % notificationInterval == 0) {
                System.out.println(
                        String.format("[%s - %s] Processing bug report %d of %d",
                                systemName, getLogTag(), i + 1, lineCount));
            }

            ScoreDoc[] scoredFiles = retriever.locate(bugReport, 10);
            // If the bug report doesn't have the required information it will return null
            if (scoredFiles == null) {
                continue;
            }

            actualQueries++;
            Set<Integer> goldSet = bugReport.getFixedFiles()
                    .stream()
                    .map(f -> {
                        try {
                            return getSourceFileID(f);
                        } catch (IOException e) {
                            System.err.println("Index read error at gold set collection");
                        }
                        return null;
                    }).collect(Collectors.toSet());

            int topRank = getTopRank(goldSet, scoredFiles);

            if (topRank > 0 && topRank <= 10) {
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

        EvaluationResult result = null;

        if (actualQueries > 0) {
            float top1Precision = (float) top1s / actualQueries;
            float top5Precision = (float) top5s / actualQueries;
            float top10Precision = (float) top10s / actualQueries;
            float meanReciprocalRank = reciprocalRankAccum / actualQueries;
            float meanAveragePrecision = averagePrecisionAccum / actualQueries;
            float averagePrecision = precisionAccum / actualQueries;
            float averageRecall = recallAccum / actualQueries;

            result = new EvaluationResult(
                    systemName,
                    actualQueries,
                    top1Precision,
                    top5Precision,
                    top10Precision,
                    meanReciprocalRank,
                    meanAveragePrecision,
                    averagePrecision,
                    averageRecall);
        } else {
            System.err.println("No valid queries for system " + systemName);
        }

        System.out.println(
                String.format("\nFinished %s evaluation for system %s\n", getLogTag(), systemName));
        System.out.println("--------\n");

        return result;
    }

    private int getSourceFileID(String fileName) throws IOException {
        if (!fileIDCache.containsKey(fileName)) {
            fileIDCache.put(fileName, sourceSearcher.search(
                    new TermQuery(new Term("path", fileName)), 1).scoreDocs[0].doc);
        }

        return fileIDCache.get(fileName);
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

    /**
     * Initializes the retriever instance used to perform the evaluation.
     *
     * @throws IOException
     */
    protected abstract RetrieverBase setupRetriever() throws IOException;

    protected abstract String getLogTag();
}
