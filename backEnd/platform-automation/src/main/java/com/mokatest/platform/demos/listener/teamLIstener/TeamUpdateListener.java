package com.mokatest.platform.demos.listener.teamLIstener;


import com.mokatest.platform.demos.domain.ui.Team;
import com.mokatest.platform.demos.listener.teamLIstener.Enum.TeamChangeType;
import com.mokatest.platform.demos.mapper.TeamMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TeamUpdateListener {

    @Resource
    private TeamMapper teamMapper;

    @Async
    @EventListener
    public void handleCourseChange(TeamUpdateEvent event) {
        TeamChangeType changeType = event.getChangeType();
        Team team = teamMapper.selectById(event.getTeamId());
        if (team == null) return;
        switch (changeType) {
            case INSERT:
                team.setTeamNumber(team.getTeamNumber() + event.getUpdateNumber());
                teamMapper.updateById(team);
                break;
            case DELETE:
                if (event.getUpdateNumber() < 0) {
                    team.setTeamNumber(team.getTeamNumber() + event.getUpdateNumber());
                } else {
                    team.setTeamNumber(team.getTeamNumber() - event.getUpdateNumber());
                }
                teamMapper.updateById(team);
                break;
        }
    }

}
