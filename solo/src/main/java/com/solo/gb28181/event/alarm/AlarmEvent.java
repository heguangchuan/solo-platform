package com.solo.gb28181.event.alarm;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class AlarmEvent extends ApplicationEvent {

    private final DeviceAlarm alarm;

    public AlarmEvent(Object source, DeviceAlarm alarm) {
        super(source);
        this.alarm = alarm;
    }
}
