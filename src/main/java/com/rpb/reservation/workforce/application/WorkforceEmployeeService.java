package com.rpb.reservation.workforce.application;

import com.rpb.reservation.auth.application.WorkforceAccountProvisioner;
import com.rpb.reservation.walkin.api.CurrentActor;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.workforce.persistence.*;
import com.rpb.reservation.workforce.positions.EmployeePositionSelection;
import static com.rpb.reservation.workforce.application.WorkforceDtos.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class WorkforceEmployeeService {
    private final WorkforceAccess access;
    private final WorkforceDirectory directory;
    private final WorkforceEmployeeRepository repository;
    private final WorkforceAccountProvisioner accounts;
    private final WorkforceAudit audit;
    private final Clock clock;
    private final EmployeePositionSelection positions;
    private final com.rpb.reservation.workforce.workmode.WorkModeService workModes;
    public WorkforceEmployeeService(WorkforceAccess access,WorkforceDirectory directory,WorkforceEmployeeRepository repository,WorkforceAccountProvisioner accounts,WorkforceAudit audit,Clock clock,EmployeePositionSelection positions){this(access,directory,repository,accounts,audit,clock,positions,null);}
    @org.springframework.beans.factory.annotation.Autowired
    public WorkforceEmployeeService(WorkforceAccess access,WorkforceDirectory directory,WorkforceEmployeeRepository repository,WorkforceAccountProvisioner accounts,WorkforceAudit audit,Clock clock,EmployeePositionSelection positions,com.rpb.reservation.workforce.workmode.WorkModeService workModes){
        this.access=access;this.directory=directory;this.repository=repository;this.accounts=accounts;this.audit=audit;this.clock=clock;this.positions=positions;this.workModes=workModes;
    }
    public EmployeePage list(CurrentActor actor,UUID store,int limit,int offset){
        access.enter(actor,store);access.manage(actor,store);
        if(limit<1||limit>100||offset<0)throw WorkforceException.invalid();
        var managed=access.managedStores(actor);
        return new EmployeePage(repository.list(actor.tenantId(),store,limit,offset).stream().map(e->visible(e,managed)).toList(),limit,offset);
    }
    public Employee get(CurrentActor actor,UUID store,UUID id){
        access.enter(actor,store);access.manage(actor,store);
        var e=repository.find(actor.tenantId(),id).orElseThrow(WorkforceException::missing);
        if(!e.storeIds().contains(store))throw WorkforceException.missing();
        return visible(e,access.managedStores(actor));
    }
    public Employee myself(CurrentActor actor){
        return repository.byAccount(actor.tenantId(),actor.actorId()).map(e->new Employee(e.id(),e.employeeNo(),e.name(),e.phone(),e.email(),e.jobTitle(),e.hireDate(),e.leaveDate(),e.status(),e.companyId(),e.companyEffectiveDate(),e.mainStoreId(),e.storeIds(),e.accountId(),e.version(),false,e.positionId(),e.birthDate(),e.englishName(),e.enabled())).orElse(null);
    }
    private Employee visible(Employee e,Set<UUID> managed){
        if(managed.containsAll(e.storeIds()))return e;
        return new Employee(e.id(),e.employeeNo(),e.name(),"","",e.jobTitle(),null,null,e.status(),null,null,null,
            e.storeIds().stream().filter(managed::contains).toList(),null,e.version(),false,e.positionId(),null,"",e.enabled());
    }
    @Transactional
    public Employee save(CurrentActor actor,UUID store,UUID id,EmployeeWrite input){
        access.sourceWrite(actor);
        access.enter(actor,store);access.manage(actor,store);
        positions.lock(actor.tenantId());repository.lock(id);
        Employee old=repository.find(actor.tenantId(),id).orElse(null);
        var managed=access.managedStores(actor);
        if(old!=null&&(!old.storeIds().contains(store)||!managed.containsAll(old.storeIds())))throw WorkforceException.forbidden();
        var extended=EmployeeProfileRules.extend(input,old);
        if(old!=null&&!old.hireDate().equals(extended.hireDate()))extended=repository.correctHireDate(actor.tenantId(),old,extended);
        var c=positions.resolve(actor.tenantId(),validate(extended),old);
        if(!managed.containsAll(c.storeIds())||!c.storeIds().contains(store)||!c.storeIds().contains(c.mainStoreId()))throw WorkforceException.forbidden();
        var activeStores=directory.stores(actor.tenantId()).stream().map(StoreOption::id).toList();
        if(!activeStores.containsAll(c.storeIds())||directory.companies(actor.tenantId()).stream().noneMatch(o->o.id().equals(c.companyId())))throw WorkforceException.invalid();
        if(old!=null&&(!old.storeIds().contains(store)||!managed.containsAll(old.storeIds())))throw WorkforceException.forbidden();
        if(!access.isHeadOffice(actor)){
            if(old!=null&&(!old.companyId().equals(c.companyId())||!old.companyEffectiveDate().equals(c.companyEffectiveDate())||!old.hireDate().equals(c.hireDate())))throw WorkforceException.forbidden();
            if(old==null&&directory.stores(actor.tenantId()).stream().noneMatch(s->s.id().equals(store)&&c.companyId().equals(s.companyId())))throw WorkforceException.forbidden();
        } else access.company(actor,c.companyId());
        if(old!=null&&c.expectedVersion()==0&&same(actor.tenantId(),old,c))return old;
        if((old==null?0:old.version())!=c.expectedVersion())throw WorkforceException.conflict();
        if(old!=null&&repository.hasActiveAttendance(actor.tenantId(),id)&&(!old.companyId().equals(c.companyId())||!new HashSet<>(old.storeIds()).equals(new HashSet<>(c.storeIds()))))throw new WorkforceException("WORKFORCE_ACTIVE_ATTENDANCE",409);
        if((old==null||!old.companyId().equals(c.companyId()))&&!directory.companyEnabled(actor.tenantId(),c.companyId()))throw new WorkforceException("WORKFORCE_COMPANY_DISABLED",400);
        LocalDate today=LocalDate.now(clock.withZone(ZoneId.of("Asia/Singapore")));
        if(old!=null){
            
            if(!old.companyId().equals(c.companyId())){if(!today.equals(c.companyEffectiveDate()))throw WorkforceException.invalid();}
            else if(old.hireDate().equals(c.hireDate())&&!old.companyEffectiveDate().equals(c.companyEffectiveDate()))throw WorkforceException.invalid();
        }
        UUID account=c.accountId();
        if(old!=null&&old.accountId()!=null){
            if(!Objects.equals(old.accountId(),account)||!c.username().isBlank()||!c.password().isBlank())throw WorkforceException.invalid();
        }
        if(account!=null){
            if(!c.username().isBlank()||!c.password().isBlank())throw WorkforceException.invalid();
            var accountStores=directory.accountStores(actor.tenantId(),account);
            if(!accountStores.containsAll(c.storeIds()))throw WorkforceException.forbidden();
            if(old==null||old.accountId()==null){
                if(!directory.ordinaryAccount(actor.tenantId(),account))throw WorkforceException.invalid();
                if(!managed.containsAll(accountStores))throw WorkforceException.forbidden();
                accounts.allowEntry(actor.tenantId(),account);
            }
        }else if(!c.username().isBlank()){
            if(!c.username().matches("[A-Za-z0-9._-]{1,40}")||!c.password().matches("[A-Za-z0-9]{6}"))throw WorkforceException.invalid();
            account=accounts.create(actor.tenantId(),c.username(),c.name(),c.password(),c.mainStoreId(),c.storeIds());
        }else if(!c.password().isBlank())throw WorkforceException.invalid();
        repository.save(actor.tenantId(),id,c,account,old,today);
        var saved=repository.find(actor.tenantId(),id).orElseThrow();
        if(workModes!=null)workModes.adoptPosition(actor,store,old,saved,today);
        // DTO contains no password/username; audit stores immutable before/after evidence.
        audit.changed(actor,store,"employee.save",id,old,saved);return saved;
    }
    private boolean same(UUID tenant,Employee e,EmployeeWrite c){
        boolean account=c.username().isBlank()?Objects.equals(e.accountId(),c.accountId()):c.accountId()==null&&accounts.matchesUsername(tenant,e.accountId(),c.username());
        return account&&e.employeeNo().equals(c.employeeNo())&&e.name().equals(c.name())&&e.phone().equals(c.phone())&&e.email().equals(c.email())&&e.jobTitle().equals(c.jobTitle())&&Objects.equals(e.positionId(),c.positionId())
            &&Objects.equals(e.birthDate(),c.birthDate())&&e.englishName().equals(c.englishName())&&e.enabled()==c.enabled()
            &&e.hireDate().equals(c.hireDate())&&Objects.equals(e.leaveDate(),c.leaveDate())&&e.status().equals(c.status())&&e.companyId().equals(c.companyId())
            &&e.companyEffectiveDate().equals(c.companyEffectiveDate())&&e.mainStoreId().equals(c.mainStoreId())&&new HashSet<>(e.storeIds()).equals(new HashSet<>(c.storeIds()));
    }
    private EmployeeWrite validate(EmployeeWrite c){
        if(c==null||c.expectedVersion()==null||c.expectedVersion()<0||c.hireDate()==null||c.companyId()==null||c.companyEffectiveDate()==null||c.mainStoreId()==null
            ||c.storeIds()==null||c.storeIds().isEmpty()||c.storeIds().size()>100||c.storeIds().stream().anyMatch(Objects::isNull)
            ||!Set.of("active","left").contains(c.status()==null?"":c.status())||c.companyEffectiveDate().isBefore(c.hireDate())
            ||c.leaveDate()!=null&&c.leaveDate().isBefore(c.hireDate())||"left".equals(c.status())&&c.leaveDate()==null)throw WorkforceException.invalid();
        var today=LocalDate.now(clock.withZone(ZoneId.of("Asia/Singapore")));
        if(c.hireDate().isAfter(today)||c.companyEffectiveDate().isAfter(today)||c.leaveDate()!=null&&c.leaveDate().isAfter(today))throw WorkforceException.invalid();
        return new EmployeeWrite(c.expectedVersion(),text(c.employeeNo(),40,true),text(c.name(),100,true),text(c.phone(),40,false),text(c.email(),160,false),text(c.jobTitle(),80,false),
            c.hireDate(),c.leaveDate(),c.status(),c.companyId(),c.companyEffectiveDate(),c.mainStoreId(),c.storeIds().stream().distinct().sorted().toList(),c.accountId(),text(c.username(),40,false),text(c.password(),6,false),c.positionId(),c.birthDate(),c.englishName(),c.enabled());
    }
    private String text(String value,int max,boolean required){var s=value==null?"":value.trim();if(s.length()>max||required&&s.isBlank())throw WorkforceException.invalid();return s;}
}
