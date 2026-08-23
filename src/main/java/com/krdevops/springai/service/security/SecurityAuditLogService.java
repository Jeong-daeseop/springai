package com.krdevops.springai.service.security;
import org.springframework.stereotype.Service;
import java.time.Instant; import java.util.List; import java.util.concurrent.CopyOnWriteArrayList;
@Service public class SecurityAuditLogService { private final List<Entry> entries=new CopyOnWriteArrayList<>(); public Entry record(String actor,String action,String target){if(actor==null||actor.isBlank()||action==null||action.isBlank()||target==null||target.isBlank())throw new IllegalArgumentException("Audit 필수값이 누락되었습니다."); Entry e=new Entry(actor,action,target,Instant.now());entries.add(e);return e;} public List<Entry> all(){return List.copyOf(entries);} public record Entry(String actor,String action,String target,Instant occurredAt){} }
