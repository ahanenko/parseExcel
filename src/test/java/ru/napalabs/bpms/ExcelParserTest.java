package ru.napalabs.bpms;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class ExcelParserTest {
    @Test
    void do_loadSchemas_success() throws IOException {
        // Given
        AbstractExcelParser parser = Mockito.mock(AbstractExcelParser.class, Mockito.withSettings()
                .useConstructor("jsons/audit_objects_schema/")
                .defaultAnswer(Mockito.CALLS_REAL_METHODS));
        URL resource = parser.getClass().getClassLoader().getResource("jsons/audit_objects_schema/");

        File file = new File( resource.getPath() + "well_xls_schema.json");
        String wellSchemaStr = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
        // When -> Then
        Map<String, String> schemas = assertDoesNotThrow(parser::loadSchemas);
        assertEquals(5, schemas.size());
        assertEquals(wellSchemaStr, schemas.get("well"));
    }

    @Test
    void do_loadSchemas_failed_schemaIsNull() {
        AbstractExcelParser parser = Mockito.mock(AbstractExcelParser.class, Mockito.withSettings()
                .useConstructor((String)null)
                .defaultAnswer(Mockito.CALLS_REAL_METHODS));
        assertThrows(RuntimeException.class, parser::loadSchemas);
    }

    @Test
    void do_loadSchemas_failed_schemaWrongValue() {
        AbstractExcelParser parser = Mockito.mock(AbstractExcelParser.class, Mockito.withSettings()
                .useConstructor("wrongPath")
                .defaultAnswer(Mockito.CALLS_REAL_METHODS));
        assertThrows(NullPointerException.class, parser::loadSchemas);
    }

    @Test
    void do_loadSchemas_failed_PathGet() throws IOException {
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
    void do_loadSchemas_failed_FilesWalk() throws IOException {
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

    @Test
    void do_loadSchemas_failed_readSchemaFile() throws IOException {
        // Given
        AbstractExcelParser parser = Mockito.mock(AbstractExcelParser.class, Mockito.withSettings()
                .useConstructor("jsons/audit_objects_schema/")
                .defaultAnswer(Mockito.CALLS_REAL_METHODS));
        MockedStatic<FileUtils> mockStatic = mockStatic(FileUtils.class);
        // When
        when(FileUtils.readFileToString(any(File.class), eq(StandardCharsets.UTF_8))).thenThrow(IOException.class);
        // Then
        RuntimeException e = assertThrows(RuntimeException.class, parser::loadSchemas);
        mockStatic.close();
    }

    @ParameterizedTest
    @CsvSource({
            "/объекты аудита/колодцы ТС_МС_new.xlsx, 2",
            "/объекты аудита/кабели ТС_МС_new.xlsx, 449",
            "/объекты аудита/кроссы ТС_МС_new.xlsx, 376",
            "/объекты аудита/муфты ТС_МС_new.xlsx, 364",
            "/объекты аудита/опоры ТС_МС_new.xlsx, 667"
    })
    void do_getWorkbook_success(String fileName, Integer rowsCount)  {
        // Given
        AbstractExcelParser parser = Mockito.mock(AbstractExcelParser.class, Mockito.withSettings()
                .useConstructor("jsons/audit_objects_schema/")
                .defaultAnswer(Mockito.CALLS_REAL_METHODS));
        String resourcesDirectory = "src/test/resources";
        File file = new File(resourcesDirectory + fileName);
        // When
        parser.loadSchemas();
        // Then
        Boolean result = assertDoesNotThrow(() -> parser.getWorkbook(file));
        assertTrue(result);
    }

    @Test
    void do_getWorkbook_failed()  {
        // Given
        AbstractExcelParser parser = Mockito.mock(AbstractExcelParser.class, Mockito.withSettings()
                .useConstructor("jsons/audit_objects_schema/")
                .defaultAnswer(Mockito.CALLS_REAL_METHODS));
        String resourcesDirectory = "src/test/resources";
        File file = new File(resourcesDirectory + "fileName");
        // When
        parser.loadSchemas();
        // Then
        Boolean result = assertDoesNotThrow(() -> parser.getWorkbook(file));
        assertFalse(result);
    }

    @Test
    void do_detectObjectsType_success()  {
        // Given
        String fileName = "/объекты аудита/колодцы ТС_МС_new.xlsx";
        AbstractExcelParser parser = Mockito.mock(AbstractExcelParser.class, Mockito.withSettings()
                .useConstructor("jsons/audit_objects_schema/")
                .defaultAnswer(Mockito.CALLS_REAL_METHODS));
        String resourcesDirectory = "src/test/resources";
        File file = new File(resourcesDirectory + fileName);
        // When
        parser.getWorkbook(file);
        parser.loadSchemas();
        // Then
        String result = assertDoesNotThrow(parser::detectObjectsType);
        assertNotNull(result);
    }

    @Test
    void do_detectObjectsType_failed_workbookIsNull()  {
        // Given
        String fileName = "/объекты аудита/колодцы ТС_МС_new.xlsx";
        AbstractExcelParser parser = Mockito.mock(AbstractExcelParser.class, Mockito.withSettings()
                .useConstructor("jsons/audit_objects_schema/")
                .defaultAnswer(Mockito.CALLS_REAL_METHODS));
        // When
        parser.loadSchemas();
        // Then
        assertThrows(NullPointerException.class, parser::detectObjectsType);
    }
}