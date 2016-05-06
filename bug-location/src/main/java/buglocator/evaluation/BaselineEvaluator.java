package buglocator.evaluation;

import buglocator.retrieval.BaselineRetriever;
import buglocator.retrieval.RetrieverBase;
import buglocator.retrieval.RetrieverBase.UseField;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Evaluator using plain Lucene relevance to locate bugs.
 */
public class BaselineEvaluator extends BaseRetrievalEvaluator {
    private final UseField useField;

    public BaselineEvaluator(String systemName, UseField useField, Path indexPath, Path dataPath) {
        super(systemName, indexPath, dataPath);
        this.useField = useField;
    }

    @Override
    protected RetrieverBase setupRetriever() throws IOException {
        return new BaselineRetriever(useField, sourceSearcher, sourceSearcher);
    }

    @Override
    protected String getLogTag() {
        return "Baseline (VSM)";
    }
}
