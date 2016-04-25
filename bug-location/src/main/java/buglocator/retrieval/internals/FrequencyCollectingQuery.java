package buglocator.retrieval.internals;

import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.ToStringUtils;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * A Lucene Term Query that collects term frequencies in indexed documents.
 */
public class FrequencyCollectingQuery extends Query {
    private final Term term;
    private final TermContext perReaderTermState;
    private final Map<String, Map<Integer, Integer>> tfCounts;
    private final String termString;

    /**
     * Constructs a query for the term <code>t</code>.
     */
    public FrequencyCollectingQuery(String field, String termString, Map<String, Map<Integer, Integer>> tfCounts) {
        this.tfCounts = tfCounts;
        this.term = new Term(field, termString);
        this.termString = termString;
        perReaderTermState = null;
    }

    final class MyWeight extends Weight {
        private final Similarity similarity;
        private final Similarity.SimWeight stats;
        private final TermContext termStates;
        private final boolean needsScores;
        private final Map<String, Map<Integer, Integer>> tfCounts;
        private final String termString;

        public MyWeight(String termString, IndexSearcher searcher, boolean needsScores, TermContext termStates,
                        Map<String, Map<Integer, Integer>> tfCounts)
                throws IOException {
            super(FrequencyCollectingQuery.this);
            this.termString = termString;
            this.tfCounts = tfCounts;
            this.needsScores = needsScores;
            assert termStates != null : "TermContext must not be null";
            // checked with a real exception in FrequencyCollectingQuery constructor
            assert termStates.hasOnlyRealTerms();
            this.termStates = termStates;
            this.similarity = searcher.getSimilarity();

            final CollectionStatistics collectionStats;
            final TermStatistics termStats;
            if (needsScores) {
                collectionStats = searcher.collectionStatistics(term.field());
                termStats = searcher.termStatistics(term, termStates);
            } else {
                // do not bother computing actual stats, scores are not needed
                final int maxDoc = searcher.getIndexReader().maxDoc();
                final int docFreq = termStates.docFreq();
                final long totalTermFreq = termStates.totalTermFreq();
                collectionStats = new CollectionStatistics(term.field(), maxDoc, -1, -1, -1);
                termStats = new TermStatistics(term.bytes(), docFreq, totalTermFreq);
            }
            this.stats = similarity.computeWeight(getBoost(), collectionStats, termStats);
        }

        @Override
        public void extractTerms(Set<Term> terms) {
            terms.add(getTerm());
        }

        @Override
        public String toString() {
            return "weight(" + FrequencyCollectingQuery.this + ")";
        }

        @Override
        public float getValueForNormalization() {
            return stats.getValueForNormalization();
        }

        @Override
        public void normalize(float queryNorm, float topLevelBoost) {
            stats.normalize(queryNorm, topLevelBoost);
        }

        @Override
        public Scorer scorer(LeafReaderContext context, Bits acceptDocs) throws IOException {
            assert termStates.topReaderContext == ReaderUtil.getTopLevelContext(context) : "The top-reader used to create Weight (" + termStates.topReaderContext + ") is not the same as the current reader's top-reader (" + ReaderUtil.getTopLevelContext(context);
            final TermsEnum termsEnum = getTermsEnum(context);
            if (termsEnum == null) {
                return null;
            }
            PostingsEnum docs = termsEnum.postings(acceptDocs, null, needsScores ? PostingsEnum.FREQS : PostingsEnum.NONE);
            assert docs != null;
            return new FrequencyCollectingScorer(termString, this, docs, similarity.simScorer(stats, context), tfCounts);
        }

        /**
         * Returns a {@link TermsEnum} positioned at this weights Term or null if
         * the term does not exist in the given context
         */
        private TermsEnum getTermsEnum(LeafReaderContext context) throws IOException {
            final TermState state = termStates.get(context.ord);
            if (state == null) { // term is not present in that reader
                assert termNotInReader(context.reader(), term) : "no termstate found but term exists in reader term=" + term;
                return null;
            }
            // System.out.println("LD=" + reader.getLiveDocs() + " set?=" +
            // (reader.getLiveDocs() != null ? reader.getLiveDocs().get(0) : "null"));
            final TermsEnum termsEnum = context.reader().terms(term.field())
                    .iterator();
            termsEnum.seekExact(term.bytes(), state);
            return termsEnum;
        }

        private boolean termNotInReader(LeafReader reader, Term term) throws IOException {
            // only called from assert
            // System.out.println("TQ.termNotInReader reader=" + reader + " term=" +
            // field + ":" + bytes.utf8ToString());
            return reader.docFreq(term) == 0;
        }

        @Override
        public Explanation explain(LeafReaderContext context, int doc) throws IOException {
            Scorer scorer = scorer(context, context.reader().getLiveDocs());
            if (scorer != null) {
                int newDoc = scorer.advance(doc);
                if (newDoc == doc) {
                    float freq = scorer.freq();
                    Similarity.SimScorer docScorer = similarity.simScorer(stats, context);
                    Explanation freqExplanation = Explanation.match(freq, "termFreq=" + freq);
                    Explanation scoreExplanation = docScorer.explain(doc, freqExplanation);
                    return Explanation.match(
                            scoreExplanation.getValue(),
                            "weight(" + getQuery() + " in " + doc + ") ["
                                    + similarity.getClass().getSimpleName() + "], result of:",
                            scoreExplanation);
                }
            }
            return Explanation.noMatch("no matching term");
        }
    }

    /**
     * Returns the term of this query.
     */
    public Term getTerm() {
        return term;
    }

    public Map<String, Map<Integer, Integer>> getTfCounts() {
        return tfCounts;
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
        final IndexReaderContext context = searcher.getTopReaderContext();
        final TermContext termState;
        if (perReaderTermState == null
                || perReaderTermState.topReaderContext != context) {
            // make FrequencyCollectingQuery single-pass if we don't have a PRTS or if the context
            // differs!
            termState = TermContext.build(context, term);
        } else {
            // PRTS was pre-build for this IS
            termState = this.perReaderTermState;
        }

        return new MyWeight(termString, searcher, needsScores, termState, tfCounts);
    }

    /**
     * Prints a user-readable version of this query.
     */
    @Override
    public String toString(String field) {
        StringBuilder buffer = new StringBuilder();
        if (!term.field().equals(field)) {
            buffer.append(term.field());
            buffer.append(":");
        }
        buffer.append(term.text());
        buffer.append(ToStringUtils.boost(getBoost()));
        return buffer.toString();
    }

    /**
     * Returns true iff <code>o</code> is equal to this.
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FrequencyCollectingQuery)) return false;
        FrequencyCollectingQuery other = (FrequencyCollectingQuery) o;
        return super.equals(o) && this.term.equals(other.term);
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ term.hashCode();
    }
}