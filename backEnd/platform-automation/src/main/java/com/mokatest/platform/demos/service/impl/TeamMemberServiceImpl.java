package com.mokatest.platform.demos.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.domain.ui.TeamMember;
import com.mokatest.platform.demos.service.TeamMemberService;
import com.mokatest.platform.demos.mapper.TeamMemberMapper;
import org.springframework.stereotype.Service;

/**
* @author: JingLong
* @description 针对表【team_member(团队成员表)】的数据库操作Service实现
* @createDate 2026-03-23 15:40:47
*/
@Service
public class TeamMemberServiceImpl extends ServiceImpl<TeamMemberMapper, TeamMember>
    implements TeamMemberService{

}




