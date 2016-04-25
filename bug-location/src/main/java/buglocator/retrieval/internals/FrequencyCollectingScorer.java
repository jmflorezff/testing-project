package buglocator.retrieval.internals;

import buglocator.retrieval.data.TermFrequencyDictionary;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.similarities.Similarity;

import java.io.IOException;

/**
 * A scorer to use with {@link FrequencyCollectingQuery}.
 */
public class FrequencyCollectingScorer extends Scorer {
    private final PostingsEnum postingsEnum;
    private final TermFrequencyDictionary tfCounts;
    private final String termString;

    public FrequencyCollectingScorer(String termString, Weight weight, PostingsEnum td,
                                     Similarity.SimScorer docScorer,
                                     TermFrequencyDictionary tfCounts) {
        super(weight);
        this.termString = termString;
        this.tfCounts = tfCounts;
        this.postingsEnum = td;
    }

    @Override
    public int docID() {
        return postingsEnum.docID();
    }

    @Override
    public int freq() throws IOException {
        return postingsEnum.freq();
    }

    @Override
    public int nextDoc() throws IOException {
        return postingsEnum.nextDoc();
    }

    @Override
    public float score() throws IOException {
        assert docID() != NO_MORE_DOCS;
        int docId = postingsEnum.docID();
        int freq = postingsEnum.freq();
        tfCounts.putTermFrequency(docId, termString, freq);
        return 1;
    }

    @Override
    public int advance(int target) throws IOException {
        return postingsEnum.advance(target);
    }

    @Override
    public long cost() {
        return postingsEnum.cost();
    }

    @Override
    public String toString() {
        return "scorer(" + weight + ")[" + super.toString() + "]";
    }
}
