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
 * Base class for retrievers.
 */
public abstract class RetrieverBase {

    protected final UseField useField;
    protected IndexReader sourceTextIndexReader;
    protected IndexSearcher sourceTextSearcher;

    public enum UseField {
        TITLE,
        DESCRIPTION,
        TITLE_AND_DESCRIPTION
    }

    static {
        // Set maximum clause count just in case
        BooleanQuery.setMaxClauseCount(10000);
    }

    public RetrieverBase(UseField usefield, IndexSearcher sourceTextSearcher) {
        this.useField = usefield;
        this.sourceTextSearcher = sourceTextSearcher;
        sourceTextIndexReader = sourceTextSearcher.getIndexReader();
    }

    /**
     * Retrieve a ranked list of source code files where the bug reported is most likely to be
     * located depending on the actual implementation.
     *
     * @param bugReport The object representing the bug to locate.
     * @return An ordered list of ranked source files.
     * @throws IOException when an index read fails.
     */
    public abstract ScoreDoc[] locate(BugReport bugReport) throws IOException;

    protected String getQueryString(BugReport bugReport) {
        String queryString;

        switch (useField) {
            case TITLE:
                if (bugReport.getTitle() == null) {
                    return null;
                }
                queryString = bugReport.getTitle();
                break;
            case DESCRIPTION:
                if (bugReport.getDescription() == null) {
                    return null;
                }
                queryString = bugReport.getDescription();
                break;
            case TITLE_AND_DESCRIPTION:
                if (bugReport.getTitle() == null && bugReport.getDescription() == null) {
                    return null;
                }
                queryString = bugReport.getTitle() + " " + bugReport.getDescription();
                break;
            default:
                throw new IllegalArgumentException("useField must be one of the constants defined" +
                        "in the enum");
        }

        return queryString;
    }

    protected Map<String, Integer> extractQueryFreqs(String queryString) {
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
