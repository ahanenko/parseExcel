package ru.napalabs.bpms;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.Column;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public abstract class AbstractExcelParser {
    private final String schemaPath;
    private final Map<String, String> schemas = new HashMap<>();
    private Workbook wb;
    private Sheet sheet;

    // Содержит набор аттрибутов данных. Будут записаны в БД в конкретное поле таблицы
    // {"name":"pnt_id", "type":["attribute"], "attribute" : {"name" : "eqm_id", "type": "bigint"}}
    // Для поля с именем "pnt_id" будет сопоставлен аттрибут с именем "eqm_id" и типом "bigint"
    private Map<String, String[]> attributesMap;

    // Содержит набор аттрибутов для отображения в виде описания. Из них формируется json, записываемый в специальное поле объекта данных
    // {"name":"pnt_type_name", "type":["displayText"], "displayName" :  "Тип"}
    // Для поля с именем "pnt_type_name" будет сопоставлен имя "Тип" и сформируется строка json вида "Тип" : 'данные из таблицы для этого поля'
    private Map<String, String> displayValuesMap;

    // Соответствие значимой строки из шаблона номеру колонки в исходном файле Excel. Для оптимизации выборки.
    private Map<String, Integer> nameToColNumMap;

    // Содержит сопоставление типов данных из БД, указываемых в шаблоне, типам данных в Java
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
                            this.schemas.put(file.getName().substring(0, file.getName().indexOf(POSTFIX)), FileUtils.readFileToString(file, StandardCharsets.UTF_8));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return SerializationUtils.clone((HashMap<String, String>) this.schemas);
    }

    public final boolean getWorkbook(File file) {
        try {
            this.wb = WorkbookFactory.create(new FileInputStream(file));
            this.sheet = this.wb.getSheetAt(0);
        } catch (IOException | EncryptedDocumentException e) {
            return false;
        }

        return true;
    }

    public final String detectObjectType() throws JsonProcessingException {
        if (this.wb == null) { throw new NullPointerException("wb is null"); }
        if (this.sheet == null) { throw new NullPointerException("sheet is null"); }
        Row firstRow = this.sheet.getRow(0);
        int firstCellNum;
        if (firstRow == null || (firstCellNum = firstRow.getLastCellNum()) <= 0) { throw new NullPointerException("firstRow is null or empty"); }
        // Набор из объектов и списков, с названием полей из шаблона JSON, для этих объектов.
        Map<String, List<String>> namesMap = new HashMap<>();
        // Набор из объектов, содержащих map из поля, имени поля в БД и его тип
        Map<String, Map<String, String[]>> attributesMapByObject = new HashMap<>();
        // Набор из объектов, содержащих map из имени поля и текста для его отображения
        Map<String, Map<String, String>> displayValuesMapByObject = new HashMap<>();

        // Разбираем схемы и сохраняем структурировано имена полей, имена и типы аттрибутов и текст для "отображаемых" полей
        {
            JsonNode node;
            ObjectMapper mapper = new ObjectMapper();

            for (String schemaObject : this.schemas.keySet()) {
                List<String> names = new ArrayList<>();
                Map<String, String[]> attrValues = new HashMap<>();
                Map<String, String> displayValues = new HashMap<>();

                node = mapper.readTree(this.schemas.get(schemaObject));
                if (node.hasNonNull("fields")) {
                    node.get("fields").elements().forEachRemaining(field -> {
                        // Добавляем имя поля
                        names.add(field.get("name").asText());

                        field.get("type").elements().forEachRemaining(typeItemValue -> {
                            // Добавляем имя и тип аттрибута для поля
                            if (typeItemValue.asText().contains("attribute")) {
                                attrValues.put(field.get("name").asText(),
                                        new String[]{
                                                field.get("attribute").get("name").asText(),
                                                field.get("attribute").get("type").asText(),
                                        });
                            }
                            // Добавляем отображаемое описание для поля
                            if (typeItemValue.asText().contains("displayText")) {
                                displayValues.put(field.get("name").asText(), field.get("displayName").asText());
                            }
                        });
                    });

                    // Отсекаем шаблоны, в которых количество параметров не совпадает с числом колонок в таблице.
                    if (names.size() == firstCellNum) {
                        namesMap.put(schemaObject, names);
                        attributesMapByObject.put(schemaObject, attrValues);
                        displayValuesMapByObject.put(schemaObject, displayValues);
                    }
                }
            }
        }
        if (namesMap.isEmpty()) { throw new NullPointerException("Wrong or empty schema"); }

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
            for (Map.Entry<String, List<String>> mapEntry : namesMap.entrySet()) {
                if (!mapEntry.getValue().contains(cell.getStringCellValue())) {
                    namesMap.remove(mapEntry.getKey());
                    attributesMapByObject.remove(mapEntry.getKey());
                    displayValuesMapByObject.remove(mapEntry.getKey());
                }
            }
        }

        // В живых должен остаться один.
        if (namesMap.size() != 1) {
//            log.error("AUDIT.Парсинг нарядов. Определение типа объекта по первой строке. Не удалось однозначно определить тип. Обнаружены типы {}", namesMap);
            return Strings.EMPTY;
        }

        String objectType = namesMap.keySet().iterator().next();
        this.attributesMap = attributesMapByObject.get(objectType);
        this.displayValuesMap = displayValuesMapByObject.get(objectType);
        this.nameToColNumMap = new HashMap<>();
        for (int cellNum = 0; cellNum < firstRow.getLastCellNum(); cellNum++) {
            Cell cell = firstRow.getCell(cellNum);
            if (this.attributesMap.containsKey(cell.getStringCellValue())) { this.nameToColNumMap.put(cell.getStringCellValue(), cellNum); }
            if (this.displayValuesMap.containsKey(cell.getStringCellValue())) { this.nameToColNumMap.put(cell.getStringCellValue(), cellNum); }
        }

        return objectType;
    }

    public final boolean parse(Class<?> entityClass, String schema) {
        final int TYPE_JSON = 1;
        final int FIELD_JAVA = 0;
        final int FIRST_ROW = 0;

        // Если нет схем, то и делать нечего
        if (this.schemas.isEmpty() || schema == null || !this.schemas.containsKey(schema)) {
//            log.error("AUDIT.Парсинг нарядов: не загружены схемы для определения типа загружаемого наряда.");
            return false;
        }

        // набор аттрибутов - методов-сеттеров, для объекта
        Map<String, Method> attrMethod = new HashMap<>();

        Field[] fields = entityClass.getDeclaredFields();
        Method[] methods = entityClass.getDeclaredMethods();
        try {
            // Формируем набор методов "setters" для целевого класса, на основе полей с аннотацией @Column JPA
            // В дальнейшем, при разборе файла Excel будем вызывать соответствующий setter, для установки значения в целевом классе
            for (Map.Entry<String, String[]> attribute : this.attributesMap.entrySet()) {
                // Получаем поле нашего класса объекта, у которого аннотация JPA (имя поля таблицы БД)
                // совпадает с именем поля указанного в шаблоне.
                Optional<Field> fieldToSet = Arrays.stream(fields)
                        .filter(field -> field.getAnnotation(Column.class).name().equals(attribute.getValue()[FIELD_JAVA]))
                        .findFirst();
                // Если такое поле есть, получаем для него метод setter нашего базового класса и сохраняем его в hashmap
                if (fieldToSet.isPresent()) {
                    Optional<Method> methodToCall = Arrays.stream(methods)
                            .filter(method -> method.getName().contains("set" + StringUtils.capitalize(fieldToSet.get().getName())))
                            .findFirst();
                    if (methodToCall.isPresent()) {
                        attrMethod.put(attribute.getKey(), entityClass.getMethod(methodToCall.get().getName(), this.CLASSES.get(attribute.getValue()[TYPE_JSON])));
                    }
                }
            }
            int entitySavesCounter = 0;
            // Разбираем тело файла по шаблону и сохраняем в данные в целевой класс и потом в БД.
            for (int rowNum = 1; rowNum <= this.sheet.getLastRowNum(); rowNum++) {
                Row row = this.sheet.getRow(rowNum);
                if (row == null) break;

                // Создаём экземпляр объекта для последующего наполнения и записи в БД
                Object entityObject = entityClass.getConstructor().newInstance();

                // Заполняем обязательные атрибуты экземпляра объекта
                for (String attrValue : this.attributesMap.keySet()) {
                    int colNum = this.nameToColNumMap.get(attrValue);
                    Cell cell = row.getCell(colNum);

                    try {
                        if (attrMethod.containsKey(attrValue)) {
                            Object value = switch (this.attributesMap.get(attrValue)[TYPE_JSON]) {
                                case "string" -> cell.getStringCellValue();
                                case "bigint" -> (long) cell.getNumericCellValue();
                                case "bigdecimal" -> BigDecimal.valueOf(cell.getNumericCellValue());
                                case "coords" -> parseCoordsString2JSONArray(cell.getStringCellValue());
                                default -> throw new IllegalStateException("Unexpected value: " + attrValue);
                            };
                            // вызываем метод-сеттер объекта и передаём ему параметры.
                            attrMethod.get(attrValue).invoke(entityObject, value);
                        }
                    } catch (Exception ignore) {
                    }
                }

                // Собираем дополнительные атрибуты в список для последующей передачи в абстрактный метод
                Map<String, String> additionalAttributes = new HashMap<>();
                FormulaEvaluator evaluator = this.wb.getCreationHelper().createFormulaEvaluator();
                DataFormatter formatter = new DataFormatter();
                for (String displayValue : this.displayValuesMap.keySet()) {
                    int colNum = this.nameToColNumMap.get(displayValue);
                    Cell cell = row.getCell(colNum);
                    additionalAttributes.put(displayValue, formatter.formatCellValue(cell, evaluator));
                }

                // Устанавливаем атрибуты, необходимые для этого объекта по бизнес-логике, но отсутствующие в таблице
                setRequiredFields(entityObject);
                // Отправляем на реализацию необязательные аттрибуты
                setAdditionalFields(entityObject, additionalAttributes);

                saveEntityClass(entityObject);
                entitySavesCounter++;
            }
        } catch (NullPointerException e) {
//            log.error("AUDIT.Парсинг нарядов. Во время разбора файла произошла ошибка {}", e.getLocalizedMessage());
            return false;
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return true;
    }
    
    // Формируем JSON массив с координатами
    protected JsonNode parseCoordsString2JSONArray(String coords) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode result = mapper.createObjectNode();
        if (coords == null || coords.isEmpty()) return result;

        ArrayNode arrayNode = result.putArray("points");

        //"Очищаем" начало строки, в текущих файлах есть "кривые" вставки с формулами
        if (coords.charAt(0) == '=') coords = coords.substring(2);

        for (String coord : coords.split(",")) {
            ObjectNode item = mapper.createObjectNode();
            try {
                coord = coord.trim();
                String[] coordArr = coord.split(" ");
                if (coordArr.length != 2) continue;
                BigDecimal latitude = new BigDecimal(coordArr[0]);
                BigDecimal longitude = new BigDecimal(coordArr[1]);
                item.put("latitude", latitude);
                item.put("longitude", longitude);
                arrayNode.add(item);
            } catch (Exception ignore) {}
        }
        return result;
    }
    
    public abstract void setRequiredFields(final Object entityObject);
    public abstract void setAdditionalFields(final Object entityObject, final Map<String, String> additionalFields);
    public abstract void saveEntityClass(final Object entityObject);
}
