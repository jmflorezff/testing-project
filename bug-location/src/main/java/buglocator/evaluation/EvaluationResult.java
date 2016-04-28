package buglocator.evaluation;

/**
 * Encapsulates the results of an evaluation for a single system
 */
public class EvaluationResult {
    private final String system;
    private final float alpha;
    private final float top1Precision;
    private final float top5Precision;
    private final float top10Precision;
    private final float meanReciprocalRank;
    private final float meanAveragePrecision;
    private final float averagePrecision;
    private final float averageRecall;
    private final boolean valid;
    private final int actualQueries;

    public EvaluationResult(String system, float alpha, int actualQueries, float top1Precision,
                            float top5Precision, float top10Precision,
                            float meanReciprocalRank,
                            float meanAveragePrecision, float averagePrecision,
                            float averageRecall) {
        this.valid = true;
        this.system = system;
        this.alpha = alpha;
        this.actualQueries = actualQueries;
        this.top1Precision = top1Precision;
        this.top5Precision = top5Precision;
        this.top10Precision = top10Precision;
        this.meanReciprocalRank = meanReciprocalRank;
        this.meanAveragePrecision = meanAveragePrecision;
        this.averagePrecision = averagePrecision;
        this.averageRecall = averageRecall;
    }

    public EvaluationResult(String system) {
        this.valid = false;
        this.system = system;
        alpha = Float.NaN;
        actualQueries = 0;
        top1Precision = Float.NaN;
        top5Precision = Float.NaN;
        top10Precision = Float.NaN;
        meanReciprocalRank = Float.NaN;
        meanAveragePrecision = Float.NaN;
        averagePrecision = Float.NaN;
        averageRecall = Float.NaN;
    }

    public boolean isValid() {
        return valid;
    }

    public String getSystem() {
        return system;
    }

    public float getAlpha() {
        return alpha;
    }

    public int getActualQueries() {
        return actualQueries;
    }

    public float getTop1Precision() {
        return top1Precision;
    }

    public float getTop5Precision() {
        return top5Precision;
    }

    public float getTop10Precision() {
        return top10Precision;
    }

    public float getMeanReciprocalRank() {
        return meanReciprocalRank;
    }

    public float getMeanAveragePrecision() {
        return meanAveragePrecision;
    }

    public float getAveragePrecision() {
        return averagePrecision;
    }

    public float getAverageRecall() {
        return averageRecall;
    }
}
