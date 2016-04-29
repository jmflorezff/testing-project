package buglocator.indexing.source.code;

import org.apache.lucene.index.FieldInvertState;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.search.similarities.DefaultSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;

/**
 * Similarity class that keeps track of maximum and minimum document length.
 * To be used while indexing source files. It uses {@link DefaultSimilarity} to compute the return
 * values for the methods.
 */
public class StatCollectingSimilarity extends Similarity {
    /**
     * Minimum number of individual terms seen in a document so far. Initialized to
     * {@code Integer.MAX_VALUE} so that it gets updated as soon as a single document is processed.
     */
    private int minDocumentLength = Integer.MAX_VALUE;

    /**
     * Maximum number of individual terms seen in a document so far. Initialized to
     * {@code Integer.MIN_VALUE} so that it gets updated as soon as a single document is processed.
     */
    private int maxDocumentLength = Integer.MIN_VALUE;

    /**
     * Similarity object used to actually calculate the return values of the methods. Used instead
     * of inheritance since the methods in {@link DefaultSimilarity} are final.
     */
    private DefaultSimilarity actualSimilarity = new DefaultSimilarity();

    public int getMinDocumentLength() {
        return minDocumentLength;
    }

    public int getMaxDocumentLength() {
        return maxDocumentLength;
    }

    @Override
    public long computeNorm(FieldInvertState state) {
        int uniqueTermCount = state.getUniqueTermCount();
        minDocumentLength =
                uniqueTermCount < minDocumentLength ? uniqueTermCount : minDocumentLength;
        maxDocumentLength =
                uniqueTermCount > maxDocumentLength ? uniqueTermCount : maxDocumentLength;
        return actualSimilarity.computeNorm(state);
    }

    @Override
    public SimWeight computeWeight(
            float queryBoost, CollectionStatistics collectionStats, TermStatistics... termStats) {
        return actualSimilarity.computeWeight(queryBoost, collectionStats, termStats);
    }

    @Override
    public SimScorer simScorer(SimWeight weight, LeafReaderContext context) throws IOException {
        return actualSimilarity.simScorer(weight, context);
    }
}
