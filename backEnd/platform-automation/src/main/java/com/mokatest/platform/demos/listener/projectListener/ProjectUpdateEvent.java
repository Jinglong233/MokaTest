package com.mokatest.platform.demos.listener.projectListener;

import com.mokatest.platform.demos.listener.projectListener.Enum.UpdateDataType;
import org.springframework.context.ApplicationEvent;


public class ProjectUpdateEvent extends ApplicationEvent {

    private final Integer updateNumber;

    private final String projectId;
    private final UpdateDataType updateDataType;



    public ProjectUpdateEvent(Object source, Integer updateNumber, String projectId, UpdateDataType updateDataType) {
        super(source);
        this.updateNumber = updateNumber;
        this.projectId = projectId;
        this.updateDataType = updateDataType;
    }

    public Integer getUpdateNumber() {
        return updateNumber;
    }

    public String getProjectId() {
        return projectId;
    }

    public UpdateDataType getUpdateDataType() {
        return updateDataType;
    }
}
