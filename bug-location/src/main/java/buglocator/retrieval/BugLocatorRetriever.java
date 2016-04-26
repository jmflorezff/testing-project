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
public class BugLocatorRetriever {

    private UseField useField;
    private IndexReader sourceTextIndexReader;
    private TermFrequencyDictionary sourceFileTFCounts;
    private IndexSearcher sourceTextSearcher;
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

    public enum UseField {
        TITLE,
        DESCRIPTION,
        TITLE_AND_DESCRIPTION
    }

    public BugLocatorRetriever(UseField useField,
                               IndexSearcher sourceTextSearcher,
                               IndexSearcher bugReportSearcher,
                               float alpha,
                               int minSourceFileLength,
                               int maxSourceFileLength) {
        this.useField = useField;
        this.sourceTextSearcher = sourceTextSearcher;
        this.bugReportSearcher = bugReportSearcher;
        this.alpha = alpha;

        sourceTextIndexReader = sourceTextSearcher.getIndexReader();
        bugReportIndexReader = bugReportSearcher.getIndexReader();

        sourceFileTFCounts = new TermFrequencyDictionary();
        bugReportTFCounts = new TermFrequencyDictionary();
        sourceFileIDS = new HashMap<>();

        bugLocatorSimilarity = new BugLocatorSimilarity(bugReportTFCounts, bugReportIndexReader,
                minSourceFileLength, maxSourceFileLength);
        cosineSimilarity = new CosineSimilarity(bugReportTFCounts, bugReportIndexReader);
    }

    public Map.Entry<Integer, Float>[] locate(BugReport bugReport) throws IOException {
        String queryString;

        switch (useField) {
            case TITLE:
                queryString = bugReport.getTitle();
                break;
            case DESCRIPTION:
                queryString = bugReport.getDescription();
                break;
            case TITLE_AND_DESCRIPTION:
                queryString = bugReport.getTitle() + " " + bugReport.getDescription();
                break;
            default:
                throw new IllegalArgumentException("useField must be one of the constants defined" +
                        "in the enum");
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

        Map<Integer, Float> totalScores =
                new HashMap<>((topSourceFiles.totalHits + simiScores.size()) / 2);

        float rVSMNormalizeVal = maxRVSMScore - minRVSMScore;
        Arrays.stream(topSourceFiles.scoreDocs).forEach(sd -> {
            totalScores.put(sd.doc, (1 - alpha) * ((sd.score - minRVSMScore) / rVSMNormalizeVal));
        });

        float simiScoreNormalizeVal = maxSimiScore - minSimiScore;
        simiScores.entrySet().stream().forEach(e -> {
            Integer docId = e.getKey();
            float simiScore = e.getValue();
            float currScore = totalScores.getOrDefault(docId, 0F);
            totalScores.put(docId,
                    currScore + alpha * ((simiScore - minSimiScore) / simiScoreNormalizeVal));
        });

        return (Map.Entry<Integer, Float>[]) totalScores.entrySet().stream().sorted(
                (o1, o2) -> (int) Math.signum(o2.getValue() - o1.getValue())).toArray();
    }

    private Map<Integer, Float> scoreBugReports(
            Map<String, Integer> queryFreqs, ScoreDoc[] scoreDocs) {
        Map<Integer, List<ScoreDoc>> fixedBy = new HashMap<>();
        Map<ScoreDoc, Integer> amountOfFixedFiles = new HashMap<>();

        Arrays.stream(scoreDocs).forEach(sd -> {
            try {
                sd.score = cosineSimilarity.calculate(queryFreqs, sd.doc);
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
                    new TermQuery(new Term("file", filePath)), 1).scoreDocs[0];
            sourceFileIDS.put(filePath, file.doc);
        }

        return sourceFileIDS.get(filePath);
    }

    private BooleanQuery createRelatedBugsQuery(
            Map<String, Integer> queryFreqs, BugReport bugReport,
            TermFrequencyDictionary tfCounts) {
        BooleanQuery relatedBugsQuery = new BooleanQuery();

        // Add clause for fixed date, we are only interested in the bug reports that were fixed
        // before this bug was reported
        relatedBugsQuery.add(new BooleanClause(
                NumericRangeQuery.newLongRange("resolutionDate", 0L,
                        bugReport.getCreationDate().getMillis(), true, true),
                BooleanClause.Occur.MUST));

        queryFreqs.forEach((term, __) -> {
            FrequencyCollectingQuery newClause =
                    new FrequencyCollectingQuery("fullText", term, tfCounts);
            relatedBugsQuery.add(new BooleanClause(newClause, BooleanClause.Occur.SHOULD));
        });

        return relatedBugsQuery;
    }

    private void scoreSourceFiles(Map<String, Integer> queryFreqs, ScoreDoc[] scoreDocs) {
        Arrays.stream(scoreDocs).forEach(sd -> {
            try {
                sd.score = bugLocatorSimilarity.calculate(queryFreqs, sd.doc);
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

    private Map<String, Integer> extractQueryFreqs(String queryString) {
        Map<String, Integer> queryFreqs = new HashMap<>();

        Arrays.stream(queryString.split(" +")).forEach(w -> {
            try {
                // Only use the query words that appear in the corpus
                if (queryFreqs.containsKey(w)) {
                    queryFreqs.put(w, queryFreqs.get(w) + 1);
                } else if (sourceTextIndexReader.docFreq(new Term("text", w)) > 0) {
                    queryFreqs.put(w, 1);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        return queryFreqs;
    }
}