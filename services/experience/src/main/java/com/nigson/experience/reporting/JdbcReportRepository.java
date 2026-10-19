package com.nigson.experience.reporting;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import com.nigson.shared.problem.Problem;
import com.nigson.shared.problem.ProblemCode;

/** Bounded, sanitized queries over Experience-owned tables only. */
public final class JdbcReportRepository implements ReportRepository {
    private static final int MAX_CAMPAIGNS = 100;
    private final JdbcTemplate jdbc;
    public JdbcReportRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public ReportValues.Summary engagement(ReportValues.Query query) {
        Timestamp from = Timestamp.from(query.from()), to = Timestamp.from(query.to());
        Map<String,Long> leads = grouped("SELECT status state,count(*) total FROM experience.lead WHERE created_at>=? AND created_at<? GROUP BY status ORDER BY status", from,to);
        Map<String,Long> applications = grouped("SELECT state,count(*) total FROM experience.career_application WHERE created_at>=? AND created_at<? GROUP BY state ORDER BY state", from,to);
        Map<String,Long> reviews = grouped("SELECT state,count(*) total FROM experience.review WHERE created_at>=? AND created_at<? GROUP BY state ORDER BY state", from,to);
        Long subscriptions = jdbc.queryForObject("SELECT count(*) FROM experience.newsletter_subscription WHERE created_at>=? AND created_at<? AND state='ACTIVE'",Long.class,from,to);
        List<ReportValues.Campaign> campaigns = jdbc.query("SELECT id FROM experience.campaign WHERE starts_at>=? AND starts_at<? ORDER BY starts_at,id LIMIT ?",(r,n)->new ReportValues.Campaign(r.getObject("id",UUID.class),0L),from,to,MAX_CAMPAIGNS+1);
        if(campaigns.size()>MAX_CAMPAIGNS)throw new Problem.Failure(ProblemCode.REPORTING_RULE_VIOLATION);
        return new ReportValues.Summary(leads,applications,reviews,subscriptions==null?0:subscriptions,campaigns);
    }

    private Map<String,Long> grouped(String sql,Timestamp from,Timestamp to) {
        var values = new LinkedHashMap<String,Long>();
        jdbc.query(sql,(r,n)->{values.put(r.getString("state"),r.getLong("total"));return null;},from,to);
        return values;
    }
}
