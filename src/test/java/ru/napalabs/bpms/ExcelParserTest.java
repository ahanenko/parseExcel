package ru.napalabs.bpms;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.*;

class ExcelParserTest {
    @Test
    void test_loadSchemas_success() throws IOException, URISyntaxException {
        AbstractExcelParser parser = Mockito.mock(AbstractExcelParser.class, Mockito.withSettings()
                .useConstructor("jsons/audit_objects_schema/")
                .defaultAnswer(Mockito.CALLS_REAL_METHODS));

        assertDoesNotThrow(parser::loadSchemas);
        assertEquals(5, parser.loadSchemas().size());
    }

    @Test
    void test_loadSchemas_failed_schemaIsNull() {
        AbstractExcelParser parser = Mockito.mock(AbstractExcelParser.class, Mockito.withSettings()
                .useConstructor((String)null)
                .defaultAnswer(Mockito.CALLS_REAL_METHODS));
        assertThrows(RuntimeException.class, parser::loadSchemas);
    }

    @Test
    void test_loadSchemas_failed_schemaWrongValue() {
        AbstractExcelParser parser = Mockito.mock(AbstractExcelParser.class, Mockito.withSettings()
                .useConstructor("wrongPath")
                .defaultAnswer(Mockito.CALLS_REAL_METHODS));
        assertThrows(NullPointerException.class, parser::loadSchemas);
    }

    @Test
    void test_loadSchemas_failed_PathGet() throws IOException {
        // Given
        AbstractExcelParser parser = Mockito.mock(AbstractExcelParser.class, Mockito.withSettings()
                .useConstructor("")
                .defaultAnswer(Mockito.CALLS_REAL_METHODS));
        MockedStatic<Paths> mockStatic = mockStatic(Paths.class);
        // When
        when(Paths.get(anyString())).thenThrow(IllegalArgumentException.class);
        // Then
        assertThrows(RuntimeException.class, parser::loadSchemas);
        mockStatic.close();
    }

    @Test
    void test_loadSchemas_failed_FilesWalk() throws IOException {
        // Given
        AbstractExcelParser parser = Mockito.mock(AbstractExcelParser.class, Mockito.withSettings()
                .useConstructor("")
                .defaultAnswer(Mockito.CALLS_REAL_METHODS));
        MockedStatic<Files> mockStatic = mockStatic(Files.class);

        // When
        when(Files.walk(any())).thenThrow(IOException.class);
        // Then
        assertThrows(RuntimeException.class, parser::loadSchemas);

        mockStatic.close();
    }
}