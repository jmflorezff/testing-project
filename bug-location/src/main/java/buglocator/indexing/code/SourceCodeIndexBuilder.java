package buglocator.indexing.code;

import buglocator.indexing.BaseIndexBuilder;
import buglocator.indexing.data.SourceFileText;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriterConfig;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Builds a Lucene index for a corpus of source code file texts. Should not be reused.
 */
public class SourceCodeIndexBuilder extends BaseIndexBuilder<SourceFileText> {
    private StatCollectingSimilarity similarity = new StatCollectingSimilarity();
    private boolean used = false;

    public SourceCodeIndexBuilder() {
        super(SourceFileText.class);
    }

    @Override
    public void buildIndex(Path sourceFilePath, Path indexPath) throws IOException {
        if (used) {
            throw new IllegalStateException("An index was already built using this instance");
        }

        super.buildIndex(sourceFilePath, indexPath);
        used = true;
    }

    @Override
    protected Document createDocument(SourceFileText item) {
        Document document = new Document();

        document.add(new StringField("path", item.getFilePath(), Field.Store.YES));
        document.add(new Field("text", item.getText(), termVectorsFieldType));

        return document;
    }

    @Override
    protected IndexWriterConfig createIndexBuilderConfig() {
        IndexWriterConfig indexBuilderConfig = super.createIndexBuilderConfig();
        indexBuilderConfig.setSimilarity(similarity);

        return indexBuilderConfig;
    }

    public int getMaxDocumentLength() {
        if (!used) {
            throw new IllegalStateException("Can't get collection stats without indexing a corpus" +
                    "first");
        }

        return similarity.getMaxDocumentLength();
    }

    public int getMinDocumentLength() {
        if (!used) {
            throw new IllegalStateException("Can't get collection stats without indexing a corpus" +
                    "first");
        }

        return similarity.getMinDocumentLength();
    }
}
