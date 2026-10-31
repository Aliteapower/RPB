package com.rpb.reservation.workforce.approval.explanations;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewExplanationRepository {
    public record Item(UUID id,String code,String text,boolean enabled,long version){}
    private final JdbcTemplate jdbc;
    public ReviewExplanationRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public void lock(UUID tenant){jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"workforce.review-explanations:"+tenant);}
    public List<Item> list(UUID tenant){return jdbc.query("select id,code,content,enabled,version from workforce_review_explanations where tenant_id=? order by code,created_at,id",(r,n)->new Item(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getBoolean(4),r.getLong(5)),tenant);}
    public void save(UUID tenant,Item item){jdbc.update("""
        insert into workforce_review_explanations(tenant_id,id,code,content,enabled,version) values(?,?,?,?,?,?)
        on conflict(tenant_id,id) do update set code=excluded.code,content=excluded.content,
        enabled=excluded.enabled,version=excluded.version,updated_at=now()
        """,tenant,item.id(),item.code(),item.text(),item.enabled(),item.version());}
}
