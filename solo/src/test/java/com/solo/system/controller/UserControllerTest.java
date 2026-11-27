package com.solo.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.solo.common.PageResult;
import com.solo.system.entity.SysUser;
import com.solo.system.request.UserPageRequest;
import com.solo.system.response.UserListVO;
import com.solo.system.service.SysUserService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserControllerTest {

    @Test
    void pageReturnsListVoAndEntitySerializationNeverExposesPassword() {
        SysUserService userService = mock(SysUserService.class);
        UserController controller = new UserController(userService);
        SysUser user = new SysUser();
        user.setId(9007199254740993L);
        user.setUsername("admin");
        user.setPassword("secret-hash");
        user.setStatus(1);
        Page<SysUser> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(user));
        UserPageRequest request = new UserPageRequest();
        when(userService.pageQuery(request)).thenReturn(page);

        PageResult<UserListVO> result = controller.getPage(request).getData();

        assertThat(result.getRows()).singleElement().satisfies(row -> {
            assertThat(row.getId()).isEqualTo(user.getId());
            assertThat(row.getUsername()).isEqualTo("admin");
        });
        String json = JsonMapper.builder().build().writeValueAsString(user);
        assertThat(json).doesNotContain("password", "secret-hash");
    }
}
