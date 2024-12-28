package ru.napalabs.bpms;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.logging.log4j.util.Strings;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public abstract class AbstractExcelParser {
    private final String schemaPath;
    private final Map<String, String> schemas = new HashMap<>();
    private Workbook wb;
    private Sheet sheet;
    private final Map<String, Class<?>> CLASSES = new HashMap<>(){{
        put("string", String.class);
        put("bigint", Long.class);
        put("bigdecimal", BigDecimal.class);
        put("coords", JsonNode.class);
    }};

    public AbstractExcelParser(String schemaPath) {
        this.schemaPath = schemaPath;
    }

    public final Map<String, String> loadSchemas() {
        if (this.schemaPath == null) { throw new NullPointerException("schemaPath is null"); }
        final String POSTFIX = "_xls_schema.json";

        URL resource = getClass().getClassLoader().getResource(this.schemaPath);
        if (resource == null) { throw new NullPointerException("resource is null"); }

        try (Stream<Path> entries =  Files.walk(Paths.get(resource.toURI()))) {
            entries.filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .filter(file -> file.getName().endsWith(POSTFIX))
                    .forEach(file -> {
                        try {
                            schemas.put(file.getName().substring(0, file.getName().indexOf(POSTFIX)), FileUtils.readFileToString(file, StandardCharsets.UTF_8));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return SerializationUtils.clone((HashMap<String, String>) schemas);
    }

    public final boolean getWorkbook(File file) {
        try {
            wb = WorkbookFactory.create(new FileInputStream(file));
            sheet = wb.getSheetAt(0);
        } catch (IOException | EncryptedDocumentException e) {
            return false;
        }

        return true;
    }

    public final String detectObjectsType() throws JsonProcessingException {
        if (wb == null) { throw new NullPointerException("wb is null"); }
        if (sheet == null) { throw new NullPointerException("sheet is null"); }
        Row firstRow = sheet.getRow(0);
        int firstCellNum;
        if (firstRow == null || (firstCellNum = firstRow.getLastCellNum()) == 0) { throw new NullPointerException("firstRow is null or empty"); }
        // Формируем набор из объектов и списка с названием полей из шаблона JSON для этого объекта.
        Map<String, List<String>> namesMap = new HashMap<>();
        {
            JsonNode node;
            ObjectMapper mapper = new ObjectMapper();

            for (String schemaObject : schemas.keySet()) {
                List<String> names = new ArrayList<>();
                node = mapper.readTree(this.schemas.get(schemaObject));
                if (node.hasNonNull("fields")) {
                    node.get("fields").elements().forEachRemaining(name -> names.add(name.get("name").asText()));
                }
                // Отсекаем шаблоны, в которых количество параметров не совпадает с числом колонок в таблице.
                if (names.size() == firstCellNum) namesMap.put(schemaObject, names);
            }
        }

        // Просматриваем заголовки в первой строке файла и ищем совпадение с шаблонами.
        for (int cellNum = 0; cellNum < firstRow.getLastCellNum(); cellNum++) {
            Cell cell = firstRow.getCell(cellNum);
            if (cell == null) {
                // Увы, пустых ячеек в заголовке быть не должно -> не совпал ни один шаблон.
                namesMap.clear();
//                log.error("AUDIT.Парсинг нарядов. Определение типа объекта по первой строке. Обнаружены пустые ячейки.");
                return Strings.EMPTY;
            }

            // В примерах тип ячейки был либо формула, формирующая строку, либо строка.
            CellType cellType = cell.getCellType();
            if (cellType != CellType.FORMULA && cellType != CellType.STRING) {
//                log.error("AUDIT.Парсинг нарядов. Определение типа объекта по первой строке. Формат ячеек должен быть Формула или Текст.");
                return Strings.EMPTY;
            }

            // Если такого имени колонки в шаблоне нет - убираем этот шаблон из просматриваемых.
            String cellValue = cell.getStringCellValue();
            for (String schemaObject : new HashSet<>(namesMap.keySet())) {
                if (!namesMap.get(schemaObject).contains(cellValue)) namesMap.remove(schemaObject);
            }
        }

        // В живых должен остаться один.
        if (namesMap.size() != 1) {
//            log.error("AUDIT.Парсинг нарядов. Определение типа объекта по первой строке. Не удалось однозначно определить тип. Обнаружены типы {}", namesMap);
            return Strings.EMPTY;
        }

        return namesMap.keySet().iterator().next();
    }
}
