package buglocator.evaluation;

import buglocator.retrieval.BugLocatorRetriever;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Fully evaluates this retrieval approach, creating indexes if they are not already found.
 */
public class EvaluationMain {
    public static void main(String[] args) throws IOException {
        BugLocatorEvaluator evaluator = new BugLocatorEvaluator();
        EvaluationResult[] results = evaluator.evaluate(
                BugLocatorRetriever.UseField.TITLE_AND_DESCRIPTION,
                Paths.get("..", "index"),
                Paths.get("..", "data"),
                0.3F);

        System.out.println("System;Alpha;Top 1;Top 5;Top 10;MRR;MAP;" +
                "Average Precision;Average Recall;Amount of Queries");

        for (EvaluationResult result : results) {
            if (!result.isValid()) {
                System.out.println(result.getSystem() + ";;;;;;;;;0");
            } else {
                System.out.println(String.join(";", Arrays.<CharSequence>asList(
                        result.getSystem(),
                        String.valueOf(result.getAlpha()),
                        String.valueOf(result.getTop1Precision()),
                        String.valueOf(result.getTop5Precision()),
                        String.valueOf(result.getTop10Precision()),
                        String.valueOf(result.getMeanReciprocalRank()),
                        String.valueOf(result.getMeanAveragePrecision()),
                        String.valueOf(result.getAveragePrecision()),
                        String.valueOf(result.getAverageRecall()),
                        String.valueOf(result.getActualQueries())
                )));
            }
        }
    }
}
