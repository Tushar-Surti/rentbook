package com.rentbook.condition;

import com.rentbook.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/** One line of a condition report: a fitting in a room, how it was found, and what each side noted. */
@Entity
@Table(name = "condition_items")
public class ConditionItem extends BaseEntity {

    /** In order from best to worst, so a move-out line can say whether it got worse. */
    public enum Condition {
        GOOD, WORN, DAMAGED, MISSING;

        public boolean worseThan(Condition before) {
            return ordinal() > before.ordinal();
        }
    }

    @Column(name = "report_id", nullable = false, updatable = false)
    private UUID reportId;

    @Column(nullable = false, updatable = false)
    private int position;

    @Column(nullable = false, length = 60, updatable = false)
    private String area;

    @Column(nullable = false, length = 80, updatable = false)
    private String item;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Condition condition;

    @Column(length = 500)
    private String note;

    @Column(name = "tenant_note", length = 500)
    private String tenantNote;

    protected ConditionItem() {
    }

    ConditionItem(UUID reportId, int position, String area, String item, Condition condition) {
        this.reportId = reportId;
        this.position = position;
        this.area = area.strip();
        this.item = item.strip();
        this.condition = condition;
    }

    void record(Condition condition, String note) {
        this.condition = condition;
        this.note = blankToNull(note);
    }

    void noteFromTenant(String note) {
        this.tenantNote = blankToNull(note);
    }

    /** Lines are matched between move-in and move-out by where they are and what they are. */
    String key() {
        return area.strip().toLowerCase() + "\u0000" + item.strip().toLowerCase();
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    public UUID getReportId() {
        return reportId;
    }

    public int getPosition() {
        return position;
    }

    public String getArea() {
        return area;
    }

    public String getItem() {
        return item;
    }

    public Condition getCondition() {
        return condition;
    }

    public String getNote() {
        return note;
    }

    public String getTenantNote() {
        return tenantNote;
    }
}
