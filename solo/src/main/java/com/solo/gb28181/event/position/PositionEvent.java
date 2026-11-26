package com.solo.gb28181.event.position;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PositionEvent extends ApplicationEvent {

    private final DevicePosition position;

    public PositionEvent(Object source, DevicePosition position) {
        super(source);
        this.position = position;
    }
}
