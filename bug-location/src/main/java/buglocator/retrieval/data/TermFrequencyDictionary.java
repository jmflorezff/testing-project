package buglocator.retrieval.data;

import java.util.HashMap;

/**
 * Caches part of an inverted index in memory.
 */
public class TermFrequencyDictionary extends HashMap<String, HashMap<Integer, Integer>> {
    public void putTermFrequency(int docId, String termString, int termFreq) {
        if (!containsKey(termString)) {
            put(termString, new HashMap<>());
        }
        get(termString).put(docId, termFreq);
    }

    public int getFrequencyOrZero(int docId, String term) {
        return containsKey(term) ? get(term).getOrDefault(docId, 0) : 0;
    }
}
