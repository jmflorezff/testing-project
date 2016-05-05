package buglocator.evaluation;

import buglocator.indexing.BaseIndexBuilder;
import buglocator.indexing.bug.reports.BugReportIndexBuilder;
import buglocator.indexing.source.code.SourceCodeIndexBuilder;
import buglocator.retrieval.RetrieverBase.UseField;
import org.apache.commons.io.FileUtils;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Fully evaluates this retrieval approach, creating indexes if they are not already found.
 */
public class EvaluationMain {
    private static final String[] systems = {"eclipse-3.1", "aspectj-1.5.3", "swt-3.1",
            "bookkeeper-4.1.0", "derby-10.9.1.0", "lucene-4.0", "mahout-0.8", "openjpa-2.2.0",
            "pig-0.11.1", "solr-4.4.0", "tika-1.3", "zookeeper-3.4.5"};

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

        buildIndexes(indexPath, dataPath);

        System.out.println("Method;System;Alpha;Top 1;Top 5;Top 10;MRR;MAP;" +
                "Average Precision;Average Recall;Amount of Queries");

        for (String system : systems) {
            String bugReportsFileName = system + ".json";
            if (!Files.exists(bugReportsPath.resolve(bugReportsFileName))) {
                System.err.println("Bug reports for \"" + system + "\" not found in data folder");
                continue;
            }

            float alpha = 0.3F;

            BugLocatorEvaluator bugLocatorEvaluator = new BugLocatorEvaluator(system,
                    UseField.TITLE_AND_DESCRIPTION,
                    indexPath,
                    dataPath,
                    alpha);

            BaselineEvaluator baselineEvaluator = new BaselineEvaluator(system,
                    UseField.TITLE_AND_DESCRIPTION, indexPath, dataPath);

            EvaluationResult bugLocatorResult = bugLocatorEvaluator.evaluate();
            EvaluationResult baselineResult = baselineEvaluator.evaluate();

            System.out.println(String.join(";", Arrays.<CharSequence>asList(
                    "BugLocator",
                    system,
                    String.valueOf(alpha),
                    bugLocatorResult.getCSVLine()
            )));

            System.out.println(String.join(";", Arrays.<CharSequence>asList(
                    "Baseline",
                    system,
                    "-",
                    baselineResult.getCSVLine()
            )));
        }
    }

    private static void buildIndexes(Path indexPath, Path dataPath) throws IOException {
        for (String system : systems) {
            Path sourceCodePath = indexPath.resolve(Paths.get("source-code", system));
            Path bugReportsPath = indexPath.resolve(Paths.get("bug-reports", system));

            if (!Files.exists(sourceCodePath)) {
                Path srcFile =
                        dataPath.resolve(Paths.get("processed-source-code", system + ".json"));
                buildIndex(srcFile, sourceCodePath, new SourceCodeIndexBuilder());
            }

            if (!Files.exists(bugReportsPath)) {
                Path srcFile =
                        dataPath.resolve(Paths.get("processed-bug-reports", system + ".json"));
                buildIndex(srcFile, bugReportsPath, new BugReportIndexBuilder());
            }
        }
    }

    private static void buildIndex(Path originPath, Path destPath, BaseIndexBuilder indexBuilder)
            throws IOException {
        FileUtils.forceMkdir(destPath.toFile());
        indexBuilder.buildIndex(originPath, destPath);
    }
}
