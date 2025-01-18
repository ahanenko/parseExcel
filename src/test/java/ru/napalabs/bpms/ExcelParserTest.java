package ru.napalabs.bpms;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.io.FileUtils;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ru.napalabs.bpms.models.AuditOrderDemo;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
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
    void do_loadSchemas_failed_PathGet(){
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
        assertThrows(RuntimeException.class, parser::loadSchemas);
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
    void do_getWorkbook_success(String fileName, Integer rowsCount) throws NoSuchFieldException, IllegalAccessException {
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
        Field fieldWb = AbstractExcelParser.class.getDeclaredField("wb");
        fieldWb.setAccessible(true);
        Workbook wb = (Workbook) fieldWb.get(parser);
        assertEquals(rowsCount, wb.getSheetAt(0).getLastRowNum());
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
    void do_detectObjectsType_success() throws NoSuchFieldException, IllegalAccessException {
        // Given
        String fileName = "/объекты аудита/колодцы ТС_МС_new.xlsx";
        AbstractExcelParser parser = Mockito.mock(AbstractExcelParser.class, Mockito.withSettings()
                .useConstructor("jsons/audit_objects_schema/")
                .defaultAnswer(Mockito.CALLS_REAL_METHODS));
        String resourcesDirectory = "src/test/resources";
        File file = new File(resourcesDirectory + fileName);

        Map<String, String[]> attributesMapExpected = new HashMap<>();
        attributesMapExpected.put("city_id", new String[]{"city_id", "bigint"});
        attributesMapExpected.put("city_name", new String[]{"city_name", "string"});
        attributesMapExpected.put("pnt_id", new String[]{"eqm_id", "bigint"});
        attributesMapExpected.put("latitude", new String[]{"latitude", "bigdecimal"});
        attributesMapExpected.put("longitude", new String[]{"longitude", "bigdecimal"});
        attributesMapExpected.put("project_name", new String[]{"objects_group", "string"});
        attributesMapExpected.put("eo_sap_code", new String[]{"eo_sap_code", "bigint"});

        Map<String, String> displayValuesMapExpected = new HashMap<>();
        displayValuesMapExpected.put("city_name", "Город");
        displayValuesMapExpected.put("pnt_type_name", "Тип");
        displayValuesMapExpected.put("project_name", "Проект");
        displayValuesMapExpected.put("create_date", "Создан");

        // When
        parser.getWorkbook(file);
        parser.loadSchemas();

        // Then
        String result = assertDoesNotThrow(parser::detectObjectType);
        assertNotNull(result);
        assertEquals("well", result);

        Field filed = AbstractExcelParser.class.getDeclaredField("attributesMap");
        filed.setAccessible(true);
        Map<String, String[]> attributesMap = (Map<String, String[]>) filed.get(parser);
        assertTrue(areEqualWithArrayValue(attributesMapExpected, attributesMap));

        filed = AbstractExcelParser.class.getDeclaredField("displayValuesMap");
        filed.setAccessible(true);
        Map<String, String> displayValuesMap = (Map<String, String>) filed.get(parser);
        assertEquals(displayValuesMapExpected, displayValuesMap);
    }

    private boolean areEqualWithArrayValue(Map<String, String[]> first, Map<String, String[]> second) {
        if (first.size() != second.size()) {
            return false;
        }

        return first.entrySet().stream()
                .allMatch(e -> Arrays.equals(e.getValue(), second.get(e.getKey())));
    }

    @Test
    void do_detectObjectsType_failed_workbookIsNull() {
        // Given
        AbstractExcelParser parser = Mockito.mock(AbstractExcelParser.class, Mockito.withSettings()
                .useConstructor("jsons/audit_objects_schema/")
                .defaultAnswer(Mockito.CALLS_REAL_METHODS));
        // When
        parser.loadSchemas();
        // Then
        Exception e = assertThrows(NullPointerException.class, parser::detectObjectType);
        assertEquals("wb is null", e.getMessage());
    }

    @Test
    void do_detectObjectsType_failed_sheetIsNull() throws NoSuchFieldException, IllegalAccessException {
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
        Field filed = AbstractExcelParser.class.getDeclaredField("sheet");
        filed.setAccessible(true);
        filed.set(parser, null);
        // Then
        Exception e = assertThrows(NullPointerException.class, parser::detectObjectType);
        assertEquals("sheet is null", e.getMessage());
    }

    @Test
    void do_detectObjectsType_failed_firstRowNullOrEmpty() throws NoSuchFieldException, IllegalAccessException {
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
        Field wb = AbstractExcelParser.class.getDeclaredField("wb");
        wb.setAccessible(true);
        Field sheetField = AbstractExcelParser.class.getDeclaredField("sheet");
        sheetField.setAccessible(true);
        sheetField.set(parser, ((Workbook) wb.get(parser)).createSheet());
        // Then
        Exception e = assertThrows(NullPointerException.class, parser::detectObjectType);
        assertEquals("firstRow is null or empty", e.getMessage());

        // When 2
        Sheet sheet = (Sheet) sheetField.get(parser);
        sheet.createRow(0);
        // Then 2
        e = assertThrows(NullPointerException.class, parser::detectObjectType);
        assertEquals("firstRow is null or empty", e.getMessage());
    }
    
    @Test
    void do_detectObjectsType_failed_schemaHasNoFieldsRows() throws JsonProcessingException {
        // Given
        String fileName = "/объекты аудита/колодцы ТС_МС_new.xlsx";
        AbstractExcelParser parser = Mockito.mock(AbstractExcelParser.class, Mockito.withSettings()
                .useConstructor("jsons/wrong_schema/")
                .defaultAnswer(Mockito.CALLS_REAL_METHODS));
        String resourcesDirectory = "src/test/resources";
        File file = new File(resourcesDirectory + fileName);
          // When
        parser.loadSchemas();
        parser.getWorkbook(file);
        Exception e = assertThrows(NullPointerException.class, parser::detectObjectType);
        assertEquals("Wrong or empty schema", e.getMessage());
    }
    
    @Test
    void do_parse_success() throws JsonProcessingException {
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
        assertTrue(parser.parse(AuditOrderDemo.class, parser.detectObjectType()));
    }
}