package buglocator.indexing.code;

import org.apache.lucene.index.FieldInvertState;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;

/**
 * Similarity class that keeps track of maximum and minimum document length.
 * To be used while indexing source files.
 */
public class StatCollectingSimilarity extends Similarity {
    private int minDocumentLength = Integer.MAX_VALUE;
    private int maxDocumentLength = Integer.MIN_VALUE;

    public int getMinDocumentLength() {
        return minDocumentLength;
    }

    public int getMaxDocumentLength() {
        return maxDocumentLength;
    }

    @Override
    public long computeNorm(FieldInvertState state) {
        int uniqueTermCount = state.getUniqueTermCount();
        minDocumentLength = uniqueTermCount < minDocumentLength ? uniqueTermCount : minDocumentLength;
        maxDocumentLength = uniqueTermCount > maxDocumentLength ? uniqueTermCount : maxDocumentLength;
        return 0;
    }

    @Override
    public SimWeight computeWeight(float queryBoost, CollectionStatistics collectionStats, TermStatistics... termStats) {
        return new SimWeight() {
            @Override
            public float getValueForNormalization() {
                return 0;
            }

            @Override
            public void normalize(float queryNorm, float topLevelBoost) {

            }
        };
    }

    @Override
    public SimScorer simScorer(SimWeight weight, LeafReaderContext context) throws IOException {
        return new SimScorer() {
            @Override
            public float score(int doc, float freq) {
                return 0;
            }

            @Override
            public float computeSlopFactor(int distance) {
                return 0;
            }

            @Override
            public float computePayloadFactor(int doc, int start, int end, BytesRef payload) {
                return 0;
            }
        };
    }
}
