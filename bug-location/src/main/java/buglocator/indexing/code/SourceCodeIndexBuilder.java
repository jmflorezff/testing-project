package buglocator.indexing.code;

import buglocator.indexing.BaseIndexBuilder;
import buglocator.indexing.data.SourceFileText;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;

/**
 * Created by juan on 4/21/16.
 */
public class SourceCodeIndexBuilder extends BaseIndexBuilder<SourceFileText> {
    public SourceCodeIndexBuilder() {
        super(SourceFileText.class);
    }

    @Override
    protected Document createDocument(SourceFileText item) {
        Document document = new Document();

        document.add(new StringField("path", item.getFilePath(), Field.Store.YES));
        document.add(new TextField("text", item.getText(), Field.Store.YES));

        return document;
    }
}
