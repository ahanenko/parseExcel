package ru.napalabs.bpms;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public abstract class AbstractExcelParser {
    private final String schemaPath;
    private final Map<String, String> schemas = new HashMap<>();
    private final Map<String, Class<?>> CLASSES = new HashMap<>(){{
        put("string", String.class);
        put("bigint", Long.class);
        put("bigdecimal", BigDecimal.class);
        put("coords", JsonNode.class);
    }};

    public AbstractExcelParser(String schemaPath) {
        this.schemaPath = schemaPath;
    }

    public Map<String, String> loadSchemas() {
        if (this.schemaPath == null) { throw new NullPointerException("schemaPath is null"); }
        final String POSTFIX = "_xls_schema.json";

        URL resource = getClass().getClassLoader().getResource(this.schemaPath);
        if (resource == null) { throw new NullPointerException("resource is null"); }

        try (Stream<Path> entries =  Files.walk(Paths.get(resource.toURI()))) {
            entries.filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .filter(file -> file.getName().endsWith(POSTFIX))
                    .forEach(file -> schemas.put(file.getName().substring(0, file.getName().indexOf(POSTFIX)), file.getName()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return SerializationUtils.clone((HashMap<String, String>) this.schemas);
//        collect.forEach(file -> schemas.put(file.getName().substring(0, file.getName().indexOf("_xls_schema.json")), file.getName()));
//        collect.forEach(System.out::println);
//        Paths.get(resource.toURI());
//        System.out.println(resource.getPath());
//        File file = new File(this.schemaPath+"opt_cable_xls_schema.json");
//        FileUtils.readFileToString(file, StandardCharsets.UTF_8);
//             File dir = FileUtils.getFile(resource.getPath());
//             System.out.println(dir.getAbsolutePath());
//             String[] files = dir.list(WildcardFileFilter.builder().setWildcards("*xls_schema.json").get());
    }

    public Workbook getWorkbook(File file) throws IOException {
        InputStream inputStream = new FileInputStream(file);
        Workbook wb = WorkbookFactory.create(inputStream);
        Sheet sheet = wb.getSheetAt(0);

        return wb;
    }
}
