package buglocator.retrieval.similarity;

import buglocator.retrieval.data.TermFrequencyDictionary;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.Map;

import static java.lang.Math.exp;
import static java.lang.Math.sqrt;
import static java.lang.StrictMath.pow;

/**
 * Calculates the similarity used by BugLocator.
 */
public class BugLocatorSimilarity extends BaseSimilarity {
    private final Map<String, Integer> documentFrequencies;
    private final int minDocumentLength;
    private final int maxDocumentLength;

    public BugLocatorSimilarity(TermFrequencyDictionary termFrequencies, IndexReader reader,
                                Map<String, Integer> documentFrequencies, int minDocumentLength,
                                int maxDocumentLength) {
        super(termFrequencies, reader);
        this.documentFrequencies = documentFrequencies;
        this.minDocumentLength = minDocumentLength;
        this.maxDocumentLength = maxDocumentLength;
    }

    @Override
    public float calculate(Map<String, Integer> queryFrequencies, int docId) throws IOException {
        Terms termVector = reader.getTermVector(docId, "text");

        // Stats needed to calculate the score
        int docLen = (int) termVector.size();
        float numDocs = reader.numDocs();

        // Normalization factor according to a logistic function, it gives more weight to longer
        // documents
        float docLenNorm = (float) (1 / (1 +
                exp(-(docLen - minDocumentLength) / (minDocumentLength - maxDocumentLength))));

        // First part: Multiplicative inverse of the square root of the sum of squared tf-idf
        // values for every term in the query
        float firstPart = (float) (1 / sqrt(queryFrequencies.entrySet().stream()
                .map(e -> {
                    String termString = e.getKey();
                    int termQueryFreq = e.getValue();
                    // Dampened term frequency value in query
                    float dampTf = (float) (Math.log(termQueryFreq) + 1);
                    // idf value for term
                    float idf = (float) Math.log(numDocs / documentFrequencies.get(termString));
                    return (float) pow(dampTf * idf, 2);
                })
                .reduce(floatAdder).get()));

        // Second part: same as first part but for document
        float secondPart = 0;
        TermsEnum termsEnum = termVector.iterator();
        BytesRef term;
        while ((term = termsEnum.next()) != null) {
            String termString = term.utf8ToString();
            // If document frequency for the current term is not in the dictionary, read it from
            // the index
            if (!documentFrequencies.containsKey(termString)) {
                documentFrequencies.put(termString, reader.docFreq(new Term("text", termString)));
            }
            secondPart += pow((Math.log(termsEnum.totalTermFreq()) + 1) *
                    Math.log(numDocs / documentFrequencies.get(termString)), 2);
        }
        secondPart = (float) (1 / sqrt(secondPart));

        // Combination of tf-idf for common terms
        float thirdPart = queryFrequencies.entrySet().stream().map(e -> {
            // e: queryTerm -> queryFreq
            Integer docFreq = termFrequencies.getFrequencyOrZero(docId, e.getKey());
            if (docFreq == 0) {
                return 0F;
            } else {
                return (float) ((Math.log(docFreq) + 1) * (Math.log(e.getValue()) + 1) *
                        pow(Math.log(numDocs / documentFrequencies.get(e.getKey())), 2));
            }
        }).reduce(floatAdder).get();

        return docLenNorm * firstPart * secondPart * thirdPart;
    }
}
