package com.rpb.reservation.workforce.approval.explanations;

import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.CurrentActor;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true)
public class ReviewExplanationService {
    public record Page(List<ReviewExplanationRepository.Item> items,boolean headOffice){}
    public record Write(Long expectedVersion,String code,String text,Boolean enabled){}
    private final WorkforceAccess access;private final ReviewExplanationRepository repository;private final WorkforceAudit audit;
    public ReviewExplanationService(WorkforceAccess access,ReviewExplanationRepository repository,WorkforceAudit audit){this.access=access;this.repository=repository;this.audit=audit;}
    public Page list(CurrentActor actor,UUID store){access.enter(actor,store);access.manage(actor,store);return new Page(repository.list(actor.tenantId()),access.isHeadOffice(actor));}
    @Transactional public ReviewExplanationRepository.Item save(CurrentActor actor,UUID store,UUID id,Write c){
        access.sourceWrite(actor);access.enter(actor,store);access.headOffice(actor);
        if(id==null||c==null||c.expectedVersion()==null||c.expectedVersion()<0||c.enabled()==null||c.code()==null||!Set.of("GENERAL","LATE","EARLY","BREAK_SHORT","BREAK_OVER","MISSING").contains(c.code())||c.text()==null||c.text().isBlank()||c.text().trim().length()>500)throw WorkforceException.invalid();
        repository.lock(actor.tenantId());var all=repository.list(actor.tenantId());var old=all.stream().filter(i->i.id().equals(id)).findFirst().orElse(null);String text=c.text().trim();
        if(old!=null&&old.code().equals(c.code())&&old.text().equals(text)&&old.enabled()==c.enabled()&&(old.version()==c.expectedVersion()||old.version()==c.expectedVersion()+1))return old;
        if((old==null?0:old.version())!=c.expectedVersion())throw WorkforceException.conflict();
        if(old==null&&all.size()>=100)throw WorkforceException.invalid();
        if(all.stream().anyMatch(i->!i.id().equals(id)&&i.code().equals(c.code())&&i.text().equals(text)))throw WorkforceException.conflict();
        var next=new ReviewExplanationRepository.Item(id,c.code(),text,c.enabled(),c.expectedVersion()+1);repository.save(actor.tenantId(),next);audit.changed(actor,store,"review-explanation.save",id,old,next);return next;
    }
}
