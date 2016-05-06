package buglocator.retrieval.similarity;

import buglocator.retrieval.data.TermFrequencyDictionary;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.Map;

/**
 * Created by juan on 5/6/16.
 */
public class TfIdfSimilarity extends BugLocatorSimilarity {
    public TfIdfSimilarity(TermFrequencyDictionary termFrequencies, IndexReader reader) {
        super(termFrequencies, reader, 0, 0);
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
            TermsEnum termsEnum = termVector.iterator();
            BytesRef term;
            while ((term = termsEnum.next()) != null) {
                String termString = term.utf8ToString();
                int totalTermFreq = (int) termsEnum.totalTermFreq();
                float tfIdfWeight = (float) ((Math.log(totalTermFreq) + 1) *
                        Math.log(numDocs / getDocFreq(termString)));
                tfIdfNormAccum += Math.pow(tfIdfWeight, 2);
            }
            documentTfIdfNorm = (float) Math.sqrt(tfIdfNormAccum);

            documentNorms.put(docId, documentTfIdfNorm);
        } else {
            documentTfIdfNorm = documentNorms.get(docId);
        }

        float secondPart = 1 / documentTfIdfNorm;

        // Combination of tf-idf for common terms
        float thirdPart = queryFrequencies.entrySet().stream().map(e -> {
            // e: queryTerm -> queryFreq
            Integer docFreq = termFrequencies.getFrequencyOrZero(docId, e.getKey());
            if (docFreq == 0) {
                return 0F;
            } else {
                return (float) ((Math.log(docFreq) + 1) * (Math.log(e.getValue()) + 1) *
                        Math.pow(Math.log(numDocs / getDocFreq(e.getKey())), 2));
            }
        }).reduce(floatAdder).get();

        return firstPart * secondPart * thirdPart;
    }
}
