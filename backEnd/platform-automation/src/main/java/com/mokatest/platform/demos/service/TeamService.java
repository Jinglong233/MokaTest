package com.mokatest.platform.demos.service;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mokatest.platform.demos.domain.ui.Team;
import com.mokatest.platform.demos.domain.ui.dto.addDTO.AddTeamMemberDTO;
import com.mokatest.platform.demos.domain.ui.dto.addDTO.TeamMemberOperateDTO;
import com.mokatest.platform.demos.domain.ui.dto.addDTO.UpdateTeamMemberRoleDTO;

/**
* @author: JingLong
* @description 针对表【team】的数据库操作Service
* @createDate 2026-03-21 14:10:23
*/
public interface TeamService extends IService<Team> {

    SaResult getTeamList();

    SaResult createTeam(Team team);

    SaResult updateTeam(Team team);

    SaResult addTeamMember(AddTeamMemberDTO addTeamMemberDTO);

    SaResult getTeamMembers(Integer teamId);

    SaResult updateMemberRole(UpdateTeamMemberRoleDTO dto);

    SaResult removeMember(TeamMemberOperateDTO dto);

    SaResult updateMemberStatus(TeamMemberOperateDTO dto);

    SaResult deleteTeam(Integer teamId);

    boolean canDeleteTeam(Integer teamId, String loginId);
}
