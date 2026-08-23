package com.krdevops.springai.service.handoff;
import com.krdevops.springai.model.handoff.ScreenHandoffBundle;
import org.springframework.stereotype.Repository;
import java.util.Map; import java.util.Optional; import java.util.concurrent.ConcurrentHashMap;
@Repository public class ScreenHandoffBundleRepository { private final Map<String,ScreenHandoffBundle> store=new ConcurrentHashMap<>(); public ScreenHandoffBundle save(ScreenHandoffBundle b){if(b==null||!b.hasValidContentHash()||!b.hasValidAuditSnapshotHash())throw new IllegalArgumentException("Handoff Hash가 유효하지 않습니다."); if(store.putIfAbsent(b.bundleId(),b)!=null)throw new IllegalStateException("Handoff ID가 이미 존재합니다."); return b;} public Optional<ScreenHandoffBundle> find(String id){return Optional.ofNullable(store.get(id));} }
