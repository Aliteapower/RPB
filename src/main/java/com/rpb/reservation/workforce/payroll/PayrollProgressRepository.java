package com.rpb.reservation.workforce.payroll;

import static com.rpb.reservation.workforce.payroll.PayrollProgressDtos.*;
import com.rpb.reservation.workforce.application.WorkforceJson;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.util.*;
import java.sql.*;
import java.util.function.Supplier;
import java.util.concurrent.Semaphore;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Repository;

/** Only bulk command persistence; salary facts and calculation caches belong to PayrollRepository. */
@Repository
public class PayrollProgressRepository {
    private final JdbcTemplate jdbc;
    private final WorkforceJson json;
    private final DataSource lockConnections;
    private final Semaphore operations=new Semaphore(2);
    /** Non-pooled fixture constructor. Production wiring always uses the annotated constructor. */
    public PayrollProgressRepository(JdbcTemplate jdbc,WorkforceJson json){this.jdbc=jdbc;this.json=json;this.lockConnections=Objects.requireNonNull(jdbc.getDataSource());}
    @Autowired
    public PayrollProgressRepository(JdbcTemplate jdbc,WorkforceJson json,DataSourceProperties properties){
        this.jdbc=jdbc;this.json=json;
        this.lockConnections=new DriverManagerDataSource(properties.determineUrl(),properties.determineUsername(),properties.determinePassword());
    }
    /** Dedicated session lock serializes the complete operation across instances without a long transaction. */
    public <T>T withOperationLock(UUID tenant,UUID command,Supplier<T> work){
        if(!operations.tryAcquire())throw new WorkforceException("WORKFORCE_PAYROLL_BUSY",503);
        try{return locked(tenant,command,work);}finally{operations.release();}
    }
    private <T>T locked(UUID tenant,UUID command,Supplier<T> work){
        String key="workforce.pay.bulk.execution:"+tenant+":"+command;
        try(var connection=lockConnections.getConnection()){
            if(!connection.getAutoCommit())connection.setAutoCommit(true);
            boolean locked;
            try(var statement=connection.prepareStatement("select pg_try_advisory_lock(hashtextextended(?,0))")){
                statement.setString(1,key);try(var result=statement.executeQuery()){result.next();locked=result.getBoolean(1);}
            }
            if(!locked)throw new WorkforceException("WORKFORCE_PAYROLL_BUSY",503);
            try{return work.get();}
            finally{
                try(var statement=connection.prepareStatement("select pg_advisory_unlock(hashtextextended(?,0))")){
                    statement.setString(1,key);statement.executeQuery().close();
                }catch(SQLException failure){
                    // A pooled logical close alone must never return a still-locked session to the pool.
                    try{connection.abort(Runnable::run);}catch(SQLException abortFailure){failure.addSuppressed(abortFailure);}
                    throw failure;
                }
            }
        }catch(SQLException e){throw new DataAccessResourceFailureException("Payroll bulk operation lock failed",e);}
    }
    public void lock(UUID tenant,UUID command){jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"workforce.pay.bulk:"+tenant+":"+command);}
    public BulkResult prepare(UUID tenant,UUID command,UUID store,UUID actor,BulkWrite payload){
        lock(tenant,command);
        var rows=jdbc.query("select store_id,actor_id,payload,response from workforce_pay_bulk_operations where tenant_id=? and id=?",(r,n)->{
            if(!store.equals(r.getObject(1,UUID.class))||!actor.equals(r.getObject(2,UUID.class))||!payload.equals(json.read(r.getString(3),BulkWrite.class)))throw WorkforceException.conflict();
            String response=r.getString(4);return new Prepared(response==null?null:json.read(response,BulkResult.class));
        },tenant,command);
        if(!rows.isEmpty())return rows.getFirst().response();
        jdbc.update("insert into workforce_pay_bulk_operations(id,tenant_id,store_id,actor_id,payload,created_at,updated_at) values(?,?,?,?,?::jsonb,now(),now())",command,tenant,store,actor,json.write(payload));
        return null;
    }
    private record Prepared(BulkResult response) {}
    public BulkResult complete(UUID tenant,UUID command,UUID store,UUID actor,BulkWrite payload,BulkResult response){
        var old=prepare(tenant,command,store,actor,payload);if(old!=null)return old;
        jdbc.update("update workforce_pay_bulk_operations set response=?::jsonb,updated_at=now() where tenant_id=? and id=? and response is null",json.write(response),tenant,command);
        return response;
    }
}
