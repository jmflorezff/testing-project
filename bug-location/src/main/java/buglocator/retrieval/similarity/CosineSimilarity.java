package buglocator.retrieval.similarity;

import buglocator.retrieval.data.TermFrequencyDictionary;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.TermsEnum;

import java.io.IOException;
import java.util.Map;

/**
 * Calculates cosine similarity between a query and a document.
 */
public class CosineSimilarity extends BaseSimilarity {
    public CosineSimilarity(TermFrequencyDictionary termFrequencies, IndexReader reader) {
        super(termFrequencies, reader);
    }

    @Override
    public float calculate(Map<String, Integer> queryFrequencies, float queryNorm, int docId) throws IOException {

        // Calculate norm of the document, same as for the query but using the term vector stored
        // in the index
        float docNorm = 0;
        TermsEnum termIterator = reader.getTermVector(docId, "fullText").iterator();
        while (termIterator.next() != null) {
            docNorm += Math.pow(termIterator.totalTermFreq(), 2);
        }
        docNorm = (float) Math.sqrt(docNorm);

        // Calculate the dot product of the query and the document and return the result divided
        // by the product of both norms
        return queryFrequencies.entrySet().stream().map(e -> {
            Integer docFreq = termFrequencies.getFrequencyOrZero(docId, e.getKey());
            return e.getValue() * docFreq;
        }).reduce(intAdder).get() / (docNorm * queryNorm);
    }
}
