package ru.napalabs.bpms.models;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
public class AuditOrderDemo {
    @Column(name = "id")
    private Long id;

    @Column(name = "order_number")
    private Long orderNumber;

    @Column(name = "object_type")
    private String objectType;

    @Column(name = "executor_id")
    private Long executorId;

    @Column(name = "executor_login")
    private String executorLogin;

    @Column(name = "creator_id")
    private Long creatorId;

    @Column(name = "creator_login")
    private String creatorLogin;

    @Column(name = "organization")
    private Long organization;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "creation_date")
    private Instant creationDate;

    @Column(name = "order_type")
    private String orderType;

    @Column(name = "order_status")
    private String orderStatus;

    @Column(name = "assigned_date")
    private Instant assignedDate;

    @Column(name = "execution_date")
    private Instant executionDate;

    @Column(name = "city_id")
    private Long cityId;

    @Column(name = "city_name")
    private String cityName;

    @Column(name = "eqm_id")
    private Long eqmId;

    @Column(name = "objects_group")
    private String objectsGroup;

    @Column(name = "latitude")
    private BigDecimal latitude;

    @Column(name = "longitude")
    private BigDecimal longitude;

    @Column(name = "eo_sap_code")
    private Long eoSapCode;

    @Column(name = "order_additional_params")
    private JsonNode orderAdditionalParams;

    @Column(name = "coords")
    private JsonNode coords;
}