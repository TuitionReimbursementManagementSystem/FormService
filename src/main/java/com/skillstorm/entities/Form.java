package com.skillstorm.entities;

import com.skillstorm.constants.EventType;
import com.skillstorm.constants.GradeFormat;
import com.skillstorm.constants.Status;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@Table("form")
public class Form {

    @PrimaryKeyColumn(name = "id", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private UUID id;

    @PrimaryKeyColumn(name = "username", ordinal = 1, type = PrimaryKeyType.CLUSTERED)
    private String username;

    @Column("first_name")
    private String firstName;

    @Column("last_name")
    private String lastName;

    private String email;

    private LocalTime time;

    private LocalDate date;

    private String location;

    private String description;

    private BigDecimal cost;

    @Column("grade_format")
    private GradeFormat gradeFormat;

    @Column("passing_grade")
    private String passingGrade;

    @Column("event_type")
    private EventType eventType;

    private String justification;

    @Column("hours_missed")
    private int hoursMissed;

    private String attachment;

    @Column("supervisor_preapproval")
    private String supervisorAttachment;

    @Column("department_head_preapproval")
    private String departmentHeadAttachment;

    private Status status;

    @Column("reason_denied")
    private String reasonDenied;

    @Column("excess_funds_approved")
    private boolean excessFundsApproved;

    private BigDecimal reimbursement;

    @Column("completion_attachment")
    private String completionAttachment;
}
