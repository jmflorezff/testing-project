package buglocator.evaluation;

/**
 * Encapsulates the results of an evaluation for a single system
 */
public class EvaluationResult {
    private final String system;
    private final float top1Precision;
    private final float top5Precision;
    private final float top10Precision;
    private final float meanReciprocalRank;
    private final float meanAveragePrecision;
    private final float averagePrecision;
    private final float averageRecall;
    private final boolean valid;
    private final int actualQueries;

    public EvaluationResult(String system, int actualQueries, float top1Precision,
                            float top5Precision, float top10Precision,
                            float meanReciprocalRank,
                            float meanAveragePrecision, float averagePrecision,
                            float averageRecall) {
        this.valid = true;
        this.system = system;
        this.actualQueries = actualQueries;
        this.top1Precision = top1Precision;
        this.top5Precision = top5Precision;
        this.top10Precision = top10Precision;
        this.meanReciprocalRank = meanReciprocalRank;
        this.meanAveragePrecision = meanAveragePrecision;
        this.averagePrecision = averagePrecision;
        this.averageRecall = averageRecall;
    }

    public boolean isValid() {
        return valid;
    }

    public String getSystem() {
        return system;
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

    public String getCSVLine() {
        return String.join(";",
                String.valueOf(top1Precision),
                String.valueOf(top5Precision),
                String.valueOf(top10Precision),
                String.valueOf(meanReciprocalRank),
                String.valueOf(meanAveragePrecision),
                String.valueOf(averagePrecision),
                String.valueOf(averageRecall),
                String.valueOf(actualQueries));
    }
}
