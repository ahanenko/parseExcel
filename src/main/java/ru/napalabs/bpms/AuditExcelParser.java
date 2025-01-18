package ru.napalabs.bpms;

import ru.napalabs.bpms.models.AuditOrderDemo;

import java.util.Map;

public class AuditExcelParser extends AbstractExcelParser {


    public AuditExcelParser(String schemaPath) {
        super(schemaPath);
    }

    @Override
    public void setRequiredFields(Object entityClass) {
        AuditOrderDemo auditOrderDemo = (AuditOrderDemo) entityClass;
        auditOrderDemo.setOrderStatus("NEW");
        auditOrderDemo.setOrderType("VOLS");
        auditOrderDemo.setObjectType("WELL");
    }

    @Override
    public void setAdditionalFields(Object entityObject, Map<String, String> additionalFields) {

    }

    @Override
    public void saveEntityClass(Object entityClass) {
        System.out.println(entityClass);
    }

}
