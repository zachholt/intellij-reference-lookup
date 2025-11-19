package com.zachholt.referencelookup.parser;

import com.zachholt.referencelookup.model.ReferenceItem;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaConstantParserTest {

    @Test
    public void testParseSimpleConstants() throws IOException {
        // Setup
        String content = """
                package com.example;
                
                public class Constants {
                    /** Error occurred */
                    public static final String ERROR_UNKNOWN = "Unknown Error";
                    
                    // Success status
                    public static final int STATUS_OK = 200;
                    
                    public static final Integer MAX_RETRIES = 5;
                }
                """;
        
        Path tempFile = Files.createTempFile("test", ".java");
        Files.writeString(tempFile, content);
        
        // Execute
        JavaConstantParser parser = new JavaConstantParser();
        List<ReferenceItem> items = parser.parseJavaFile(tempFile);
        
        // Verify
        assertThat(items).hasSize(3);
        
        ReferenceItem item1 = items.get(0);
        assertThat(item1.getCode()).isEqualTo("ERROR_UNKNOWN");
        assertThat(item1.getDescription()).isEqualTo("Error occurred");
        assertThat(item1.getCategory()).isEqualTo("Error Codes");
        
        ReferenceItem item2 = items.get(1);
        assertThat(item2.getCode()).isEqualTo("STATUS_OK");
        assertThat(item2.getDescription()).isEqualTo("Success status");
        assertThat(item2.getCategory()).isEqualTo("Status Codes");

        ReferenceItem item3 = items.get(2);
        assertThat(item3.getCode()).isEqualTo("MAX_RETRIES");
        assertThat(item3.getDescription()).isEqualTo("5"); // Fallback to value
        
        // Cleanup
        Files.deleteIfExists(tempFile);
    }
}