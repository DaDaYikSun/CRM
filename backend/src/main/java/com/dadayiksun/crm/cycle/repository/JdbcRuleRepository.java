package com.dadayiksun.crm.cycle.repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.dadayiksun.crm.cycle.domain.RuleSettings.CoatInterval;
import com.dadayiksun.crm.cycle.domain.RuleSettings.VaccineRuleGroup;
import com.dadayiksun.crm.cycle.domain.RuleSettings.VaccineRuleRow;
import com.dadayiksun.crm.cycle.domain.RuleSettings.VaccineTypeRow;

@Repository
public class JdbcRuleRepository implements RuleRepository {

    private final JdbcTemplate jdbc;

    public JdbcRuleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<VaccineRuleGroup> vaccineRules() {
        Map<Long, VaccineTypeRow> types = new LinkedHashMap<>();
        Map<Long, List<VaccineRuleRow>> rules = new LinkedHashMap<>();
        jdbc.query("SELECT vaccine_type_id, code, name, is_major, is_active FROM vaccine_type"
                + " ORDER BY sort_order, vaccine_type_id", rs -> {
            long id = rs.getLong("vaccine_type_id");
            types.put(id, new VaccineTypeRow(id, rs.getString("code"), rs.getString("name"),
                    rs.getBoolean("is_major"), rs.getBoolean("is_active")));
            rules.put(id, new ArrayList<>());
        });
        jdbc.query("SELECT vaccine_rule_id, vaccine_type_id, phase, age_from_days, age_to_days, interval_days,"
                + " dose_count, note FROM vaccine_rule WHERE is_active = 1 ORDER BY vaccine_type_id, age_from_days,"
                + " vaccine_rule_id", rs -> {
            List<VaccineRuleRow> list = rules.get(rs.getLong("vaccine_type_id"));
            if (list != null) {
                list.add(new VaccineRuleRow(rs.getLong("vaccine_rule_id"), rs.getString("phase"),
                        rs.getInt("age_from_days"), rs.getObject("age_to_days", Integer.class),
                        rs.getInt("interval_days"), rs.getObject("dose_count", Integer.class), rs.getString("note")));
            }
        });
        return types.values().stream().map(t -> new VaccineRuleGroup(t, rules.get(t.vaccineTypeId()))).toList();
    }

    @Override
    public void updateRule(VaccineRuleRow rule, long staffId) {
        jdbc.update("UPDATE vaccine_rule SET age_from_days = ?, age_to_days = ?, interval_days = ?, dose_count = ?,"
                + " updated_by = ? WHERE vaccine_rule_id = ?", rule.ageFromDays(), rule.ageToDays(),
                rule.intervalDays(), rule.doseCount(), staffId, rule.vaccineRuleId());
    }

    @Override
    public void updateMajor(long vaccineTypeId, boolean isMajor, long staffId) {
        jdbc.update("UPDATE vaccine_type SET is_major = ?, updated_by = ? WHERE vaccine_type_id = ?",
                isMajor, staffId, vaccineTypeId);
    }

    @Override
    public List<CoatInterval> coatIntervals() {
        return jdbc.query("SELECT coat_type_id, name, default_groom_weeks FROM coat_type WHERE is_active = 1"
                + " ORDER BY sort_order, coat_type_id", (rs, i) -> new CoatInterval(rs.getLong("coat_type_id"),
                rs.getString("name"), rs.getInt("default_groom_weeks")));
    }

    @Override
    public void updateGroomWeeks(long coatTypeId, int weeks, long staffId) {
        jdbc.update("UPDATE coat_type SET default_groom_weeks = ?, updated_by = ? WHERE coat_type_id = ?",
                weeks, staffId, coatTypeId);
    }
}
