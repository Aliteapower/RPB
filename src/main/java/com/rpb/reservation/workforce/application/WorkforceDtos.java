package com.rpb.reservation.workforce.application;

import java.time.LocalDate;
import java.util.*;

public final class WorkforceDtos {
    private WorkforceDtos(){}
    public record Option(UUID id,String name){}
    public record StoreOption(UUID id,String name,UUID companyId){}
    public record Company(UUID id,String name,String registrationNo,int payDay,boolean enabled,long version){}
    public record CompanyWrite(Long expectedVersion,String registrationNo,Integer payDay,Boolean enabled){}
    public record Employee(UUID id,String employeeNo,String name,String phone,String email,String jobTitle,
        LocalDate hireDate,LocalDate leaveDate,String status,UUID companyId,LocalDate companyEffectiveDate,
        UUID mainStoreId,List<UUID> storeIds,UUID accountId,long version,boolean editable,UUID positionId,LocalDate birthDate,String englishName,boolean enabled){
        public Employee(UUID id,String employeeNo,String name,String phone,String email,String jobTitle,LocalDate hireDate,LocalDate leaveDate,String status,UUID companyId,LocalDate companyEffectiveDate,UUID mainStoreId,List<UUID> storeIds,UUID accountId,long version,boolean editable,UUID positionId){this(id,employeeNo,name,phone,email,jobTitle,hireDate,leaveDate,status,companyId,companyEffectiveDate,mainStoreId,storeIds,accountId,version,editable,positionId,null,"",true);}
        public Employee(UUID id,String employeeNo,String name,String phone,String email,String jobTitle,LocalDate hireDate,LocalDate leaveDate,String status,UUID companyId,LocalDate companyEffectiveDate,UUID mainStoreId,List<UUID> storeIds,UUID accountId,long version,boolean editable){this(id,employeeNo,name,phone,email,jobTitle,hireDate,leaveDate,status,companyId,companyEffectiveDate,mainStoreId,storeIds,accountId,version,editable,null);}
    }
    public record EmployeeWrite(Long expectedVersion,String employeeNo,String name,String phone,String email,String jobTitle,
        LocalDate hireDate,LocalDate leaveDate,String status,UUID companyId,LocalDate companyEffectiveDate,
        UUID mainStoreId,List<UUID> storeIds,UUID accountId,String username,String password,UUID positionId,LocalDate birthDate,String englishName,Boolean enabled){
        public EmployeeWrite(Long expectedVersion,String employeeNo,String name,String phone,String email,String jobTitle,LocalDate hireDate,LocalDate leaveDate,String status,UUID companyId,LocalDate companyEffectiveDate,UUID mainStoreId,List<UUID> storeIds,UUID accountId,String username,String password,UUID positionId){this(expectedVersion,employeeNo,name,phone,email,jobTitle,hireDate,leaveDate,status,companyId,companyEffectiveDate,mainStoreId,storeIds,accountId,username,password,positionId,null,null,null);}
        public EmployeeWrite(Long expectedVersion,String employeeNo,String name,String phone,String email,String jobTitle,LocalDate hireDate,LocalDate leaveDate,String status,UUID companyId,LocalDate companyEffectiveDate,UUID mainStoreId,List<UUID> storeIds,UUID accountId,String username,String password){this(expectedVersion,employeeNo,name,phone,email,jobTitle,hireDate,leaveDate,status,companyId,companyEffectiveDate,mainStoreId,storeIds,accountId,username,password,null);}
    }
    public record EmployeePage(List<Employee> items,int limit,int offset){}
    public record Administrator(UUID accountId,String name,boolean employeeManage,UUID payrollCompanyId,long version){}
    public record AdministratorWrite(Long expectedVersion,Boolean employeeManage,UUID payrollCompanyId){}
    public record Home(boolean headOffice,boolean employeeManage,List<StoreOption> stores,List<Option> companies,
        List<Option> accounts,List<Option> administratorAccounts,Employee myself,List<StoreOption> clockStores){}
}
