package buglocator.retrieval.similarity;

import buglocator.retrieval.data.TermFrequencyDictionary;
import org.apache.lucene.index.IndexReader;

import java.io.IOException;
import java.util.Map;
import java.util.function.BinaryOperator;

/**
 * Base class for custom document similarity implementations.
 */
public abstract class BaseSimilarity {
    protected static final BinaryOperator<Float> floatAdder = (x, y) -> x + y;
    protected static final BinaryOperator<Integer> intAdder = (i, j) -> i + j;

    protected TermFrequencyDictionary termFrequencies;
    protected IndexReader reader;

    public BaseSimilarity(TermFrequencyDictionary termFrequencies, IndexReader reader) {
        this.termFrequencies = termFrequencies;
        this.reader = reader;
    }

    public abstract float calculate(
            Map<String, Integer> queryFrequencies, float queryNorm, int docId)
            throws IOException;
}
