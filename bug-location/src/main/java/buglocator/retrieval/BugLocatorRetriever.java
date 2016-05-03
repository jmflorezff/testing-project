package buglocator.retrieval;

import buglocator.indexing.data.BugReport;
import buglocator.retrieval.data.TermFrequencyDictionary;
import buglocator.retrieval.internals.FrequencyCollectingQuery;
import buglocator.retrieval.similarity.BugLocatorSimilarity;
import buglocator.retrieval.similarity.CosineSimilarity;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;

import java.io.IOException;
import java.util.*;

/**
 * Retriever that implements the BugLocator algorithm.
 */
public class BugLocatorRetriever extends RetrieverBase {

    private TermFrequencyDictionary sourceFileTFCounts;
    private BugLocatorSimilarity bugLocatorSimilarity;
    private TermFrequencyDictionary bugReportTFCounts;
    private IndexSearcher bugReportSearcher;
    private IndexReader bugReportIndexReader;
    private CosineSimilarity cosineSimilarity;
    private Map<String, Integer> sourceFileIDS;

    private float maxSimiScore;
    private float minSimiScore;
    private float maxRVSMScore;
    private float minRVSMScore;
    private float alpha;

    public BugLocatorRetriever(UseField useField,
                               IndexSearcher sourceTextSearcher,
                               IndexSearcher bugReportSearcher,
                               float alpha,
                               int minSourceFileLength,
                               int maxSourceFileLength) {
        super(useField, sourceTextSearcher);
        this.bugReportSearcher = bugReportSearcher;
        this.alpha = alpha;

        bugReportIndexReader = bugReportSearcher.getIndexReader();

        sourceFileTFCounts = new TermFrequencyDictionary();
        bugReportTFCounts = new TermFrequencyDictionary();
        sourceFileIDS = new HashMap<>();

        bugLocatorSimilarity = new BugLocatorSimilarity(sourceFileTFCounts, sourceTextIndexReader,
                minSourceFileLength, maxSourceFileLength);
        cosineSimilarity = new CosineSimilarity(bugReportTFCounts, bugReportIndexReader);
    }

    /**
     * Uses the technique presented in the paper to retrieve a ranked list of source code files
     * where the bug reported is most likely to be located.
     *
     * @param bugReport The object representing the bug to locate.
     * @return An ordered list of ranked source files.
     * @throws IOException when an index read fails.
     */
    @Override
    public ScoreDoc[] locate(BugReport bugReport) throws IOException {
        if (bugReport.getCreationDate() == null) {
            return null;
        }

        String queryString = getQueryString(bugReport);

        if (queryString == null) {
            return null;
        }

        maxRVSMScore = maxSimiScore = Float.MIN_VALUE;
        minRVSMScore = minSimiScore = Float.MAX_VALUE;
        Map<String, Integer> queryFreqs = extractQueryFreqs(queryString);

        // Create the BooleanQuery that wraps the term queries to search source files
        BooleanQuery sourceFilesQuery = createSourceFilesQuery(queryFreqs, sourceFileTFCounts);

        TopDocs topSourceFiles =
                sourceTextSearcher.search(sourceFilesQuery, sourceTextIndexReader.numDocs());
        scoreSourceFiles(queryFreqs, topSourceFiles.scoreDocs);

        // Search related bug reports
        BooleanQuery relatedBugsQuery =
                createRelatedBugsQuery(queryFreqs, bugReport, bugReportTFCounts);

        TopDocs topBugReports =
                bugReportSearcher.search(relatedBugsQuery, bugReportIndexReader.numDocs());
        Map<Integer, Float> simiScores = scoreBugReports(queryFreqs, topBugReports.scoreDocs);

        Map<Integer, Float> totalScores = new HashMap<>();

        float rVSMNormalizeVal = maxRVSMScore - minRVSMScore;
        Arrays.stream(topSourceFiles.scoreDocs).forEach(sd -> {
            totalScores.put(sd.doc, (1 - alpha) * ((sd.score - minRVSMScore) / rVSMNormalizeVal));
        });

        float simiScoreNormalizeVal = maxSimiScore - minSimiScore;
        simiScores.entrySet().stream().forEach(e -> {
            Integer docId = e.getKey();
            float simiScore = e.getValue();
            float currScore = totalScores.getOrDefault(docId, 0F);

            float finalScore;
            if (simiScoreNormalizeVal != 0) {
                finalScore =
                        currScore + (alpha * ((simiScore - minSimiScore) / simiScoreNormalizeVal));
            } else {
                finalScore = currScore + (alpha * simiScore);
            }

            totalScores.put(docId, finalScore);
        });

        ScoreDoc[] results = new ScoreDoc[totalScores.size()];

        Object[] sortedEntries = totalScores.entrySet().stream().sorted(
                (o1, o2) -> Float.compare(o2.getValue(), o1.getValue())).toArray();

        for (int i = 0; i < sortedEntries.length; i++) {
            Map.Entry<Integer, Float> e = (Map.Entry<Integer, Float>) sortedEntries[i];
            results[i] = new ScoreDoc(e.getKey(), e.getValue());
        }

        return results;
    }

    private Map<Integer, Float> scoreBugReports(
            Map<String, Integer> queryFreqs, ScoreDoc[] scoreDocs) {
        Map<Integer, List<ScoreDoc>> fixedBy = new HashMap<>();
        Map<ScoreDoc, Integer> amountOfFixedFiles = new HashMap<>();

        // Calculate the norm of the query vector: square root of the sum of square term frequencies
        float queryNorm = (float) Math.sqrt(queryFreqs.entrySet().stream()
                .map(e -> (float) Math.pow(e.getValue(), 2))
                .reduce((x, y) -> x + y).get());

        Arrays.stream(scoreDocs).forEach(sd -> {
            try {
                sd.score = cosineSimilarity.calculate(queryFreqs, queryNorm, sd.doc);
                if (sd.score == 0) {
                    // It's a false positive
                    return;
                }
                Document bugReport = bugReportIndexReader.document(sd.doc);
                Arrays.stream(bugReport.get("fixedFiles").split(";")).forEach(f -> {
                    try {
                        int sourceFileID = getSourceFileID(f);
                        if (!fixedBy.containsKey(sourceFileID)) {
                            fixedBy.put(sourceFileID, new ArrayList<>());
                        }
                        fixedBy.get(sourceFileID).add(sd);

                        int fixedFiles = amountOfFixedFiles.getOrDefault(sd, 0);
                        amountOfFixedFiles.put(sd, fixedFiles + 1);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        Map<Integer, Float> fileSimiScores = new HashMap<>();

        fixedBy.entrySet().forEach(e -> {
            int fileID = e.getKey();
            List<ScoreDoc> referencedBy = e.getValue();
            float simiScore = referencedBy.stream()
                    .map(sd -> sd.score / amountOfFixedFiles.get(sd))
                    .reduce((x, y) -> x + y)
                    .get();

            updateExtremeSimiScores(simiScore);

            fileSimiScores.put(fileID, simiScore);
        });

        return fileSimiScores;
    }

    private void updateExtremeSimiScores(float simiScore) {
        minSimiScore = simiScore < minSimiScore ? simiScore : minSimiScore;
        maxSimiScore = simiScore > maxSimiScore ? simiScore : maxSimiScore;
    }

    public int getSourceFileID(String filePath) throws IOException {
        if (!sourceFileIDS.containsKey(filePath)) {
            ScoreDoc file = sourceTextSearcher.search(
                    new TermQuery(new Term("path", filePath)), 1).scoreDocs[0];
            sourceFileIDS.put(filePath, file.doc);
        }

        return sourceFileIDS.get(filePath);
    }

    private BooleanQuery createRelatedBugsQuery(
            Map<String, Integer> queryFreqs, BugReport bugReport,
            TermFrequencyDictionary tfCounts) {
        BooleanQuery relatedBugsQuery = new BooleanQuery();

        // Add clause for fixed date, we are only interested in the bug reports that were fixed
        // before this bug was reported.
        relatedBugsQuery.add(new BooleanClause(
                NumericRangeQuery.newLongRange("resolutionDate", 1L,
                        bugReport.getCreationDate().getMillis(), true, false),
                BooleanClause.Occur.FILTER));

        // Make sure the bug report used as query is not retrieved by explicitly forbidding its key
        relatedBugsQuery.add(new BooleanClause(new TermQuery(new Term("key", bugReport.getKey())),
                BooleanClause.Occur.MUST_NOT));

        queryFreqs.forEach((term, __) -> {
            FrequencyCollectingQuery newClause =
                    new FrequencyCollectingQuery("fullText", term, tfCounts);
            relatedBugsQuery.add(new BooleanClause(newClause, BooleanClause.Occur.SHOULD));
        });

        return relatedBugsQuery;
    }

    private void scoreSourceFiles(Map<String, Integer> queryFreqs, ScoreDoc[] scoreDocs) {
        int numDocs = sourceTextIndexReader.numDocs();
        float queryNorm = (float) (1 / Math.sqrt(queryFreqs.entrySet().stream()
                .map(e -> {
                    String termString = e.getKey();
                    int termQueryFreq = e.getValue();
                    // Dampened term frequency value in query
                    float dampTf = (float) (Math.log(termQueryFreq) + 1);
                    // idf value for term
                    float idf = (float) Math.log(numDocs / bugLocatorSimilarity.getDocFreq(termString));
                    return (float) Math.pow(dampTf * idf, 2);
                })
                .reduce((x, y) -> x + y).get()));

        Arrays.stream(scoreDocs).forEach(sd -> {
            try {
                sd.score = bugLocatorSimilarity.calculate(queryFreqs, queryNorm, sd.doc);
                updateExtremeRVSMScores(sd.score);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void updateExtremeRVSMScores(float score) {
        minRVSMScore = score < minRVSMScore ? score : minRVSMScore;
        maxRVSMScore = score > maxRVSMScore ? score : maxRVSMScore;
    }

    private BooleanQuery createSourceFilesQuery(Map<String, Integer> queryFreqs,
                                                TermFrequencyDictionary tfCounts) {
        BooleanQuery wrapperQuery = new BooleanQuery();
        queryFreqs.forEach((term, freq) -> {
            FrequencyCollectingQuery newClause =
                    new FrequencyCollectingQuery("text", term, tfCounts);
            wrapperQuery.add(new BooleanClause(newClause, BooleanClause.Occur.SHOULD));
        });

        return wrapperQuery;
    }
}
