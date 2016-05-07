package buglocator.retrieval;

import buglocator.indexing.data.BugReport;
import buglocator.retrieval.similarity.TfIdfSimilarity;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

/**
 * Retriever that uses VSM to locate bugs.
 */
public class BaselineRetriever extends BugLocatorRetriever {
    private TfIdfSimilarity tfIdfSimilarity;

    public BaselineRetriever(UseField usefield, IndexSearcher sourceTextSearcher,
                             IndexSearcher bugReportSearcher) {
        super(usefield, sourceTextSearcher, bugReportSearcher, 0, 0, 0);
        tfIdfSimilarity = new TfIdfSimilarity(sourceFileTFCounts, sourceTextIndexReader);
    }

    @Override
    public ScoreDoc[] locate(BugReport bugReport, int maxResults) throws IOException {
        String queryString = getQueryString(bugReport);

        if (queryString == null) {
            return null;
        }

        Map<String, Integer> queryFreqs = extractQueryFreqs(queryString);

        // Create the BooleanQuery that wraps the term queries to search source files
        BooleanQuery sourceFilesQuery = createSourceFilesQuery(queryFreqs, sourceFileTFCounts);

        TopDocs topSourceFiles =
                sourceTextSearcher.search(sourceFilesQuery, sourceTextIndexReader.numDocs());
        tfIdfScore(queryFreqs, topSourceFiles.scoreDocs);

        ScoreDoc[] sortedEntries = Arrays.stream(topSourceFiles.scoreDocs)
                .sorted((o1, o2) -> Float.compare(o2.score, o1.score))
                .toArray(ScoreDoc[]::new);

        return Arrays.copyOfRange(sortedEntries, 0, Math.min(maxResults, sortedEntries.length));
    }

    private void tfIdfScore(Map<String, Integer> queryFreqs, ScoreDoc[] scoreDocs) {
        int numDocs = sourceTextIndexReader.numDocs();
        float queryNorm = (float) Math.sqrt(queryFreqs.entrySet().stream()
                .map(e -> {
                    String termString = e.getKey();
                    int termQueryFreq = e.getValue();
                    // Dampened term frequency value in query
                    float dampTf = (float) (Math.log(termQueryFreq) + 1);
                    // idf value for term
                    float idf = (float) Math.log(numDocs / tfIdfSimilarity.getDocFreq(termString));
                    return (float) Math.pow(dampTf * idf, 2);
                })
                .reduce((x, y) -> x + y).get());

        Arrays.stream(scoreDocs).forEach(sd -> {
            try {
                sd.score = tfIdfSimilarity.calculate(queryFreqs, queryNorm, sd.doc);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
}
