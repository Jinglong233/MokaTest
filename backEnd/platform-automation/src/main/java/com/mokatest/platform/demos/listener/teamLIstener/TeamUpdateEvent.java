package com.mokatest.platform.demos.listener.teamLIstener;

import com.mokatest.platform.demos.listener.teamLIstener.Enum.TeamChangeType;
import org.springframework.context.ApplicationEvent;


public class TeamUpdateEvent extends ApplicationEvent {

    private Integer teamId;

    private Integer updateNumber;
    private TeamChangeType changeType;


    public TeamUpdateEvent(Object source, Integer updateNumber, Integer teamId, TeamChangeType changeType) {
        super(source);
        this.updateNumber = updateNumber;
        this.teamId = teamId;
        this.changeType = changeType;

    }

    public Integer getTeamId() {
        return teamId;
    }

    public Integer getUpdateNumber() {
        return updateNumber;
    }

    public TeamChangeType getChangeType() {
        return changeType;
    }
}
