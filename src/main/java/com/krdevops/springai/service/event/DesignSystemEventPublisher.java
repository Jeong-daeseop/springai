package com.krdevops.springai.service.event;
import com.krdevops.springai.model.event.DesignSystemEvent;
import org.springframework.stereotype.Service;
import java.util.List; import java.util.concurrent.CopyOnWriteArrayList;
@Service public class DesignSystemEventPublisher { private final List<DesignSystemEvent> events=new CopyOnWriteArrayList<>(); public DesignSystemEvent publish(DesignSystemEvent e){events.add(e);return e;} public List<DesignSystemEvent> findAll(){return List.copyOf(events);} }
