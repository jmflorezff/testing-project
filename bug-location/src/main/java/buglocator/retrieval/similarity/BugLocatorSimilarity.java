package buglocator.retrieval.similarity;

import buglocator.retrieval.data.TermFrequencyDictionary;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static java.lang.Math.sqrt;
import static java.lang.StrictMath.pow;

/**
 * Calculates the similarity used by BugLocator.
 */
public class BugLocatorSimilarity extends BaseSimilarity {
    private final Map<String, Integer> documentFrequencies = new HashMap<>();
    final Map<Integer, Float> documentNorms = new HashMap<>();
    private final Map<Integer, Integer> documentLengths = new HashMap<>();
    private final int minDocumentLength;
    final float numDocs;
    private final float doclenRange;
    private final int minus3Sigma;
    private final int plus3Sigma;
    private Map<Integer, Float> normalizationFactors = new HashMap<>();

    public BugLocatorSimilarity(TermFrequencyDictionary termFrequencies, IndexReader reader,
                                int minus3Sigma, int plus3Sigma) {
        super(termFrequencies, reader);
        this.minDocumentLength = Math.max(0, minus3Sigma);
        this.minus3Sigma = minus3Sigma;
        this.plus3Sigma = plus3Sigma;
        numDocs = reader.numDocs();
        doclenRange = plus3Sigma - minDocumentLength;
    }

    @Override
    public float calculate(Map<String, Integer> queryFrequencies, float queryNorm, int docId) throws IOException {
        // First part: Multiplicative inverse of the square root of the sum of squared tf-idf
        // values for every term in the query
        float firstPart = 1 / queryNorm;

        // Second part: same as first part but for document
        float documentTfIdfNorm;
        if (!documentNorms.containsKey(docId)) {
            Terms termVector = reader.getTermVector(docId, "text");
            float tfIdfNormAccum = 0;
            int docLenAccum = 0;
            TermsEnum termsEnum = termVector.iterator();
            BytesRef term;
            while ((term = termsEnum.next()) != null) {
                String termString = term.utf8ToString();
                int totalTermFreq = (int) termsEnum.totalTermFreq();
                docLenAccum += totalTermFreq;
                float tfIdfWeight = (float) ((Math.log(totalTermFreq) + 1) *
                        Math.log(numDocs / getDocFreq(termString)));
                tfIdfNormAccum += pow(tfIdfWeight, 2);
            }
            documentTfIdfNorm = (float) sqrt(tfIdfNormAccum);

            documentNorms.put(docId, documentTfIdfNorm);
            documentLengths.put(docId, docLenAccum);
        } else {
            documentTfIdfNorm = documentNorms.get(docId);
        }

        float secondPart = 1 / documentTfIdfNorm;

        // Normalization factor according to a logistic function, it gives more weight to longer
        // documents
        float docLenNorm;
        if (!normalizationFactors.containsKey(docId)) {
            int docLen = documentLengths.get(docId);
            if (docLen < minus3Sigma) {
                docLenNorm = 0.5F;
            } else if (docLen > plus3Sigma) {
                docLenNorm = 1;
            } else {
                float n = (6 * (docLen - minDocumentLength)) / (doclenRange);
                float power = (float) Math.exp(n);
                docLenNorm = power / (1 + power);
            }
            normalizationFactors.put(docId, docLenNorm);
        } else {
            docLenNorm = normalizationFactors.get(docId);
        }

        // Combination of tf-idf for common terms
        float thirdPart = queryFrequencies.entrySet().stream().map(e -> {
            // e: queryTerm -> queryFreq
            Integer docFreq = termFrequencies.getFrequencyOrZero(docId, e.getKey());
            if (docFreq == 0) {
                return 0F;
            } else {
                return (float) ((Math.log(docFreq) + 1) * (Math.log(e.getValue()) + 1) *
                        pow(Math.log(numDocs / getDocFreq(e.getKey())), 2));
            }
        }).reduce(floatAdder).get();

        float tfIdfScore = firstPart * secondPart * thirdPart;

        return docLenNorm * tfIdfScore;
    }

    public int getDocFreq(String termString) {
        // If document frequency for the current term is not in the dictionary, read it from
        // the index
        if (!documentFrequencies.containsKey(termString)) {
            try {
                documentFrequencies.put(termString, reader.docFreq(new Term("text", termString)));
            } catch (IOException e) {
                return 1;
            }
        }
        return documentFrequencies.get(termString);
    }
}
