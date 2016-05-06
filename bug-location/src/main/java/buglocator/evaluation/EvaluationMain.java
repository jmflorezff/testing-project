package buglocator.evaluation;

import buglocator.indexing.BaseIndexBuilder;
import buglocator.indexing.bug.reports.BugReportIndexBuilder;
import buglocator.indexing.source.code.SourceCodeIndexBuilder;
import buglocator.retrieval.RetrieverBase.UseField;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.io.PrintWriter;
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
        ArgumentParser argumentParser = createArgumentParser();
        Namespace arguments;
        try {
            arguments = argumentParser.parseArgs(args);
        } catch (ArgumentParserException e) {
            argumentParser.handleError(e);
            return;
        }

        // Get the command line arguments or their defaults if not provided
        Path dataPath = Paths.get(arguments.getString("data_folder"));
        Path indexPath = Paths.get(arguments.getString("index_folder"));
        Path outputFilePath = Paths.get(arguments.getString("output_file"));
        Path bugReportsPath = dataPath.resolve("processed-bug-reports");
        float alpha = arguments.getFloat("alpha");
        boolean fullAnalysis = arguments.getBoolean("full_analysis");

        if (!Files.exists(dataPath)) {
            System.err.println(String.format("Data folder '%s' does not exist",
                    dataPath.toAbsolutePath().toString()));
            return;
        }

        if (!Files.isDirectory(dataPath)) {
            System.err.println(String.format("Data folder '%s' is not a directory",
                    dataPath.toAbsolutePath().toString()));
            return;
        }

        if (!Files.exists(bugReportsPath) || !Files.isDirectory(bugReportsPath)) {
            System.err.println(String.format("Data folder '%s' does not exist. This directory " +
                            "should exist and contain the .json files corresponding to the processed " +
                            "bug reports used for evaluation.",
                    bugReportsPath.toAbsolutePath().toString()));
            return;
        }

        if (Files.exists(outputFilePath)) {
            System.err.println("Output file already exists, please choose a different output" +
                    "file name with the option -o");
            return;
        }

        PrintWriter outputWriter = new PrintWriter(outputFilePath.toFile());
        buildIndexes(indexPath, dataPath, fullAnalysis);

        String evaluationType;
        if (fullAnalysis) {
            evaluationType = "full";
        } else {
            evaluationType = "short";
        }

        System.out.println(String.format("Beginning %s analysis", evaluationType));
        long startTime = System.currentTimeMillis();

        outputWriter.println("Method;System;Alpha;% Top 1;% Top 5;% Top 10;MRR;MAP;" +
                "Average Precision;Average Recall;Amount of Queries");

        for (String system : systems) {
            if (!fullAnalysis && system.startsWith("eclipse")) {
                continue;
            }

            String bugReportsFileName = system + ".json";
            if (!Files.exists(bugReportsPath.resolve(bugReportsFileName))) {
                System.err.println("Bug reports for \"" + system + "\" not found in data folder");
                continue;
            }

            BugLocatorEvaluator bugLocatorEvaluator = new BugLocatorEvaluator(system,
                    UseField.TITLE_AND_DESCRIPTION,
                    indexPath,
                    dataPath,
                    alpha);

            BaselineEvaluator baselineEvaluator = new BaselineEvaluator(system,
                    UseField.TITLE_AND_DESCRIPTION, indexPath, dataPath);

            EvaluationResult bugLocatorResult = bugLocatorEvaluator.evaluate();
            EvaluationResult baselineResult = baselineEvaluator.evaluate();

            outputWriter.println(String.join(";", Arrays.<CharSequence>asList(
                    "BugLocator",
                    system,
                    String.valueOf(alpha),
                    bugLocatorResult.getCSVLine()
            )));

            outputWriter.println(String.join(";", Arrays.<CharSequence>asList(
                    "Baseline (VSM)",
                    system,
                    "-",
                    baselineResult.getCSVLine()
            )));
        }

        outputWriter.close();

        long finishTime = System.currentTimeMillis();
        long runTime = finishTime - startTime;

        float minutes = runTime / 60000F;
        int minutesWhole = (int) Math.floor(minutes);
        float seconds = (minutes - minutesWhole) * 1000F;
        int secondsWhole = (int) Math.floor(seconds);

        System.out.println(String.format("Finished analysis in %d minutes and %d seconds",
                minutesWhole, secondsWhole));
    }

    private static void buildIndexes(Path indexPath, Path dataPath, boolean fullAnalysis) throws IOException {
        boolean indexesBuilt = false;
        for (String system : systems) {
            if (!fullAnalysis && system.startsWith("eclipse")) {
                continue;
            }

            Path sourceCodePath = indexPath.resolve(Paths.get("source-code", system));
            Path bugReportsPath = indexPath.resolve(Paths.get("bug-reports", system));

            if (!Files.exists(sourceCodePath)) {
                System.out.println(
                        String.format("Building source code index for system %s", system));
                Path srcFile =
                        dataPath.resolve(Paths.get("processed-source-code", system + ".json"));
                buildIndex(srcFile, sourceCodePath, new SourceCodeIndexBuilder());
                indexesBuilt = true;
            }

            if (!Files.exists(bugReportsPath)) {
                System.out.println(
                        String.format("Building bug report index for system %s", system));
                Path srcFile =
                        dataPath.resolve(Paths.get("processed-bug-reports", system + ".json"));
                buildIndex(srcFile, bugReportsPath, new BugReportIndexBuilder());
                indexesBuilt = true;
            }
        }

        if (!indexesBuilt) {
            System.out.println("Indexes for target systems are already built");
        }

        System.out.println();
    }

    private static void buildIndex(Path originPath, Path destPath, BaseIndexBuilder indexBuilder)
            throws IOException {
        FileUtils.forceMkdir(destPath.toFile());
        indexBuilder.buildIndex(originPath, destPath);
    }

    private static ArgumentParser createArgumentParser() {
        ArgumentParser parser = ArgumentParsers.newArgumentParser("BugLocatorII")
                .defaultHelp(true)
                .description("Reimplementation of the BugLocator tool proposed by Zhou et al.");

        parser.addArgument("-d", "--data-folder")
                .help("The folder where the data for the analysis is located")
                .setDefault("data");

        parser.addArgument("-i", "--index-folder")
                .help("Folder where the index will be located. It will be created if it " +
                        "doesn't exist")
                .setDefault("index");

        parser.addArgument("-a", "--alpha")
                .help("Combination factor for the two kinds of scores used by the tool, " +
                        "the default is 0.3")
                .type(Float.class)
                .setDefault(0.3F);

        parser.addArgument("-o", "--output-file")
                .help("File to which the results of the analysis will be output")
                .setDefault("buglocator-evaluation.csv");

        parser.addArgument("-f", "--full-analysis")
                .help("Includes the analysis of the Eclipse system, which takes a long time to " +
                        "process")
                .action(Arguments.storeTrue());

        return parser;
    }
}
