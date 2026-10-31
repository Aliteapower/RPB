package com.rpb.reservation.workforce.api;

import com.rpb.reservation.workforce.application.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import com.rpb.reservation.walkin.api.*;
import static com.rpb.reservation.workforce.application.WorkforceDtos.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.*;
import org.springframework.dao.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/workforce")
public class WorkforceController extends WorkforceEndpointSupport {
    private final WorkforceHomeService home;
    private final WorkforceEmployeeService employees;
    private final WorkforceCompanyService companies;
    private final WorkforceAdminService admins;
    public WorkforceController(CurrentActorProvider actors,WorkforceHomeService home,WorkforceEmployeeService employees,WorkforceCompanyService companies,WorkforceAdminService admins){
        super(actors);this.home=home;this.employees=employees;this.companies=companies;this.admins=admins;
    }
    @GetMapping("/home") public Home home(@PathVariable UUID storeId){return home.home(actor(),storeId);}
    @GetMapping("/employees") public EmployeePage list(@PathVariable UUID storeId,@RequestParam(defaultValue="50")int limit,@RequestParam(defaultValue="0")int offset){return employees.list(actor(),storeId,limit,offset);}
    @GetMapping("/employees/{id}") public Employee employee(@PathVariable UUID storeId,@PathVariable UUID id){return employees.get(actor(),storeId,id);}
    @PutMapping("/employees/{id}") public Employee save(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody EmployeeWrite c){return employees.save(actor(),storeId,id,c);}
    @GetMapping("/companies") public List<Company> companies(@PathVariable UUID storeId){return companies.list(actor(),storeId);}
    @PutMapping("/companies/{id}") public Company company(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody CompanyWrite c){return companies.save(actor(),storeId,id,c);}
    @GetMapping("/administrators") public List<Administrator> administrators(@PathVariable UUID storeId){return admins.list(actor(),storeId);}
    @PutMapping("/administrators/{id}") public Administrator administrator(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody AdministratorWrite c){return admins.save(actor(),storeId,id,c);}
}
