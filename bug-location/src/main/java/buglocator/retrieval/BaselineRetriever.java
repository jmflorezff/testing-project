package buglocator.retrieval;

import buglocator.indexing.data.BugReport;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;

import java.io.IOException;
import java.util.Map;

/**
 * Retriever that uses plain Lucene to locate bugs.
 */
public class BaselineRetriever extends RetrieverBase {
    public BaselineRetriever(UseField usefield, IndexSearcher sourceTextSearcher) {
        super(usefield, sourceTextSearcher);
    }

    @Override
    public ScoreDoc[] locate(BugReport bugReport, int maxResults) throws IOException {
        String queryString = getQueryString(bugReport);
        if (queryString == null) {
            return null;
        }

        Map<String, Integer> queryFreqs = extractQueryFreqs(queryString);

        BooleanQuery query = new BooleanQuery();

        queryFreqs.forEach((term, frequency) -> {
            TermQuery termQuery = new TermQuery(new Term("text", term));
            termQuery.setBoost(frequency);
            query.add(new BooleanClause(termQuery, BooleanClause.Occur.SHOULD));
        });

        return sourceTextSearcher.search(query, maxResults).scoreDocs;
    }
}
