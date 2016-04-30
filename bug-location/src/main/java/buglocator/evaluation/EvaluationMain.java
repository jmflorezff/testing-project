package buglocator.evaluation;

import buglocator.retrieval.BugLocatorRetriever;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Fully evaluates this retrieval approach, creating indexes if they are not already found.
 */
public class EvaluationMain {
    private static final String[] systems = {"bookkeeper-4.1.0", "derby-10.9.1.0",
            "lucene-4.0", "mahout-0.8", "openjpa-2.2.0", "pig-0.11.1", "solr-4.4.0",
            "tika-1.3", "zookeeper-3.4.5"};

    public static void main(String[] args) throws IOException {
        Path dataPath = Paths.get("..", "data");
        Path indexPath = Paths.get("..", "index");
        if (!Files.exists(dataPath)) {
            System.err.println("Data folder does not exist");
            return;
        }
        if (!Files.isDirectory(dataPath)) {
            System.err.println("Data folder is not a directory");
            return;
        }
        Path bugReportsPath = dataPath.resolve("processed-bug-reports");
        if (!Files.exists(bugReportsPath) || !Files.isDirectory(bugReportsPath)) {
            System.err.println("Directory " + bugReportsPath.toString() + " does not exist");
            return;
        }

        System.out.println("System;Alpha;Top 1;Top 5;Top 10;MRR;MAP;" +
                "Average Precision;Average Recall;Amount of Queries");

        for (String system : systems) {
            String bugReportsFileName = system + ".json";
            if (!Files.exists(bugReportsPath.resolve(bugReportsFileName))) {
                System.err.println("Bug reports for \"" + system + "\" not found in data folder");
                continue;
            }

            BugLocatorEvaluator bugLocatorEvaluator = new BugLocatorEvaluator(system,
                    BugLocatorRetriever.UseField.TITLE_AND_DESCRIPTION,
                    indexPath,
                    dataPath,
                    0.3F);

            EvaluationResult bugLocatorResult = bugLocatorEvaluator.evaluate();

            System.out.println(String.join(";", Arrays.<CharSequence>asList(
                    bugLocatorResult.getSystem(),
                    String.valueOf("alpha"),
                    String.valueOf(bugLocatorResult.getTop1Precision()),
                    String.valueOf(bugLocatorResult.getTop5Precision()),
                    String.valueOf(bugLocatorResult.getTop10Precision()),
                    String.valueOf(bugLocatorResult.getMeanReciprocalRank()),
                    String.valueOf(bugLocatorResult.getMeanAveragePrecision()),
                    String.valueOf(bugLocatorResult.getAveragePrecision()),
                    String.valueOf(bugLocatorResult.getAverageRecall()),
                    String.valueOf(bugLocatorResult.getActualQueries())
            )));
        }
    }
}
